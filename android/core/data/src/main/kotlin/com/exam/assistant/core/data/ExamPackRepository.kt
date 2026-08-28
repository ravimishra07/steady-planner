package com.exam.assistant.core.data

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.ExamSubject
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.SyllabusNodeKind
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Replaces [SyllabusRepository]'s positional parsing. Loads the versioned,
 * stable-ID [ExamPack] from the bundled asset — parsed off the critical
 * path, cached in memory, never re-parsed per screen.
 */
class ExamPackRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    @Volatile
    private var cached: ExamPack? = null

    suspend fun examPack(): ExamPack = withContext(dispatchers.default) {
        cached ?: loadExamPack().also { cached = it }
    }

    private fun loadExamPack(): ExamPack {
        context.assets.open("syllabus_cgl.json").bufferedReader().use { reader ->
            val root = JSONObject(reader.readText())
            val tier1 = root.getJSONArray("tier1")
            return ExamPack(
                schemaVersion = root.optInt("schemaVersion", 1),
                examId = root.getString("examId"),
                displayName = root.getString("displayName"),
                syllabusVersion = root.getString("syllabusVersion"),
                subjects = (0 until tier1.length()).map { index ->
                    parseSubject(tier1.getJSONObject(index), order = index)
                },
            )
        }
    }

    private fun parseSubject(json: JSONObject, order: Int): ExamSubject = ExamSubject(
        id = json.getString("id"),
        name = json.getString("n"),
        order = order,
        questions = json.optInt("q", 0).takeIf { json.has("q") },
        nodes = parseNodes(json.getJSONArray("t"), depth = 0),
    )

    private fun parseNodes(array: JSONArray, depth: Int): List<SyllabusNode> =
        (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val children = if (json.has("c")) parseNodes(json.getJSONArray("c"), depth + 1) else emptyList()
            SyllabusNode(
                id = json.getString("id"),
                title = json.getString("n"),
                kind = kindForDepth(depth),
                order = index,
                estimatedMinutes = json.optDouble("h").takeIf { json.has("h") }?.let { (it * 60).toInt() },
                children = children,
            )
        }

    /** This pack is 3 levels deep under each subject: chapter -> topic -> subtopic. */
    private fun kindForDepth(depth: Int): SyllabusNodeKind = when (depth) {
        0 -> SyllabusNodeKind.CHAPTER
        1 -> SyllabusNodeKind.TOPIC
        else -> SyllabusNodeKind.SUBTOPIC
    }
}
