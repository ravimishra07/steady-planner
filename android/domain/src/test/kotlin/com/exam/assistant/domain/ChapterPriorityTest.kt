package com.exam.assistant.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPriorityTest {
    @Test
    fun `parses bands means years and sources`() {
        val table = parsePyqPriority(
            """
            {"generatedFrom": "draft.json", "years": [2021, 2025], "method": "m \"q\"",
             "chapters": {
               "c.a": {"band": "HIGH", "meanQuestionsPerYear": 4.2, "yearsUsed": 5, "sourceNames": ["PW", "Careers360"]},
               "c.b": {"band": "NO_DATA", "meanQuestionsPerYear": null, "yearsUsed": 1, "sourceNames": []}
             }}
            """.trimIndent(),
        )
        assertEquals(2021, table.firstYear)
        assertEquals(2025, table.lastYear)
        assertEquals("m \"q\"", table.method)
        assertEquals(PriorityBand.HIGH, table.forChapter("c.a").band)
        assertEquals(4.2, table.forChapter("c.a").meanQuestionsPerYear!!, 0.0)
        assertEquals(listOf("PW", "Careers360"), table.forChapter("c.a").sourceNames)
        assertNull(table.forChapter("c.b").meanQuestionsPerYear)
    }

    @Test
    fun `a chapter missing from the table reads as no data not zero`() {
        val missing = PyqPriorityTable.EMPTY.forChapter("nope")
        assertEquals(PriorityBand.NO_DATA, missing.band)
        assertNull(missing.meanQuestionsPerYear)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed json is rejected`() {
        parsePyqPriority("{\"chapters\": {")
    }

    @Test
    fun `yield rank puts high first and unknown last`() {
        val ranked = PriorityBand.entries.sortedBy { it.yieldRank() }
        assertEquals(listOf(PriorityBand.HIGH, PriorityBand.MEDIUM, PriorityBand.LOW, PriorityBand.NO_DATA), ranked)
    }

    /** The generated asset: shape, the 1-decimal rule, and "no data is never a number". */
    @Test
    fun `the bundled asset parses and every record is well formed`() {
        val file = File("../app/src/main/assets/neet_pyq_priority.json")
        assertTrue("asset missing: ${file.absolutePath}", file.exists())
        val table = parsePyqPriority(file.readText())
        assertEquals("docs/research/pyq_weightage_draft.json", table.generatedFrom)
        assertEquals(2021, table.firstYear)
        assertEquals(2025, table.lastYear)
        assertTrue(table.method.isNotBlank())
        assertEquals(79, table.chapters.size)
        table.chapters.values.forEach { c ->
            if (c.band == PriorityBand.NO_DATA) {
                assertNull(c.chapterId, c.meanQuestionsPerYear)
            } else {
                val mean = c.meanQuestionsPerYear!!
                assertTrue(c.chapterId, c.yearsUsed >= 2)
                assertEquals(c.chapterId, Math.round(mean * 10) / 10.0, mean, 1e-9)
                assertTrue(c.chapterId, c.sourceNames.isNotEmpty())
            }
        }
    }

    @Test
    fun `within a subject a higher band never has a lower mean than a lower band`() {
        val table = parsePyqPriority(File("../app/src/main/assets/neet_pyq_priority.json").readText())
        table.chapters.values.filter { it.band != PriorityBand.NO_DATA }
            .groupBy { it.chapterId.split('.')[1] }
            .forEach { (subject, chapters) ->
                val bands = chapters.groupBy { it.band }
                fun min(b: PriorityBand) = bands[b]?.minOf { it.meanQuestionsPerYear!! }
                fun max(b: PriorityBand) = bands[b]?.maxOf { it.meanQuestionsPerYear!! }
                val highMin = min(PriorityBand.HIGH)
                val medMax = max(PriorityBand.MEDIUM)
                val medMin = min(PriorityBand.MEDIUM)
                val lowMax = max(PriorityBand.LOW)
                if (highMin != null && medMax != null) assertTrue(subject, highMin >= medMax)
                if (medMin != null && lowMax != null) assertTrue(subject, medMin >= lowMax)
            }
    }
}
