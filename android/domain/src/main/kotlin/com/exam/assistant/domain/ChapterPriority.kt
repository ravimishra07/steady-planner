package com.exam.assistant.domain

/**
 * How often a chapter has been asked in NEET (PRODUCTION_V1_PLAN §4.2).
 *
 * Built offline by `tools/build_pyq_priority.py` from per-paper counts that
 * coaching sources state for 2021-2025. Nothing here is a prediction: it is a
 * historical average, with the sources it came from.
 */
enum class PriorityBand {
    HIGH,
    MEDIUM,
    LOW,

    /** Fewer than two years of counts. Shown as "No PYQ data", never as zero. */
    NO_DATA,
}

data class ChapterPriority(
    val chapterId: String,
    val band: PriorityBand,
    /** Average questions per year over [yearsUsed] years, one decimal. Null with [PriorityBand.NO_DATA]. */
    val meanQuestionsPerYear: Double?,
    val yearsUsed: Int,
    /** Publishers whose counts were used. */
    val sourceNames: List<String>,
)

data class PyqPriorityTable(
    val firstYear: Int,
    val lastYear: Int,
    val method: String,
    val generatedFrom: String,
    val chapters: Map<String, ChapterPriority>,
) {
    /** A chapter missing from the table has no data, exactly like one marked [PriorityBand.NO_DATA]. */
    fun forChapter(chapterId: String): ChapterPriority =
        chapters[chapterId] ?: ChapterPriority(chapterId, PriorityBand.NO_DATA, null, 0, emptyList())

    companion object {
        val EMPTY = PyqPriorityTable(0, 0, "", "", emptyMap())
    }
}

/** Lower is planned earlier under high-yield-first. Unknown chapters sort after known ones. */
fun PriorityBand.yieldRank(): Int = when (this) {
    PriorityBand.HIGH -> 0
    PriorityBand.MEDIUM -> 1
    PriorityBand.LOW -> 2
    PriorityBand.NO_DATA -> 3
}

/** The planner's priority view: chapter id to its record. */
typealias ChapterPriorities = Map<String, ChapterPriority>

/** Parses the asset written by `tools/build_pyq_priority.py`. Throws [IllegalArgumentException] on a malformed file. */
fun parsePyqPriority(json: String): PyqPriorityTable {
    val root = MiniJson(json).parse() as? Map<*, *> ?: throw IllegalArgumentException("priority file is not an object")
    val years = (root["years"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    val chapters = (root["chapters"] as? Map<*, *> ?: throw IllegalArgumentException("priority file has no chapters"))
        .entries.associate { (key, value) ->
            val id = key as String
            val node = value as? Map<*, *> ?: throw IllegalArgumentException("chapter $id is not an object")
            id to ChapterPriority(
                chapterId = id,
                band = runCatching { PriorityBand.valueOf(node["band"] as String) }.getOrDefault(PriorityBand.NO_DATA),
                meanQuestionsPerYear = (node["meanQuestionsPerYear"] as? Number)?.toDouble(),
                yearsUsed = (node["yearsUsed"] as? Number)?.toInt() ?: 0,
                sourceNames = (node["sourceNames"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            )
        }
    return PyqPriorityTable(
        firstYear = years.firstOrNull() ?: 0,
        lastYear = years.lastOrNull() ?: 0,
        method = root["method"] as? String ?: "",
        generatedFrom = root["generatedFrom"] as? String ?: "",
        chapters = chapters,
    )
}

/**
 * A small JSON reader so the priority file can be parsed and tested in the
 * Android-free domain module. Objects become maps, arrays lists, numbers
 * [Double], and null stays null. Only what the generated file uses.
 */
private class MiniJson(private val text: String) {
    private var pos = 0

    fun parse(): Any? {
        val value = value()
        skipSpace()
        require(pos == text.length) { "trailing data at $pos" }
        return value
    }

    private fun value(): Any? {
        skipSpace()
        require(pos < text.length) { "unexpected end" }
        return when (val c = text[pos]) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) number() else throw IllegalArgumentException("unexpected '$c' at $pos")
        }
    }

    private fun obj(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        pos++
        skipSpace()
        if (peek() == '}') { pos++; return result }
        while (true) {
            skipSpace()
            val key = string()
            skipSpace()
            expect(':')
            result[key] = value()
            skipSpace()
            when (peek()) {
                ',' -> pos++
                '}' -> { pos++; return result }
                else -> throw IllegalArgumentException("expected , or } at $pos")
            }
        }
    }

    private fun array(): List<Any?> {
        val result = ArrayList<Any?>()
        pos++
        skipSpace()
        if (peek() == ']') { pos++; return result }
        while (true) {
            result += value()
            skipSpace()
            when (peek()) {
                ',' -> pos++
                ']' -> { pos++; return result }
                else -> throw IllegalArgumentException("expected , or ] at $pos")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            require(pos < text.length) { "unterminated string" }
            val c = text[pos++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    require(pos < text.length) { "unterminated escape" }
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'u' -> {
                            require(pos + 4 <= text.length) { "bad unicode escape" }
                            out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw IllegalArgumentException("bad escape \\$e")
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = pos
        while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
        return text.substring(start, pos).toDouble()
    }

    private fun literal(word: String, value: Any?): Any? {
        require(text.startsWith(word, pos)) { "bad literal at $pos" }
        pos += word.length
        return value
    }

    private fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'

    private fun expect(c: Char) {
        require(peek() == c) { "expected '$c' at $pos" }
        pos++
    }

    private fun skipSpace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
