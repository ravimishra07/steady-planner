package com.exam.assistant.core.data

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTopicNode
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SyllabusRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    @Volatile
    private var cachedTier1: List<SyllabusSection>? = null

    suspend fun tier1Sections(): List<SyllabusSection> = withContext(dispatchers.default) {
        cachedTier1 ?: loadTier1().also { cachedTier1 = it }
    }

    private fun loadTier1(): List<SyllabusSection> {
        context.assets.open("syllabus_cgl.json").bufferedReader().use { reader ->
            val root = JSONObject(reader.readText())
            val tier1 = root.getJSONArray("tier1")
            return (0 until tier1.length()).map { index ->
                val section = tier1.getJSONObject(index)
                SyllabusSection(
                    name = section.getString("n"),
                    questions = section.optInt("q", 0),
                    topics = parseTopics(section.getJSONArray("t")),
                )
            }
        }
    }

    private fun parseTopics(array: org.json.JSONArray): List<SyllabusTopicNode> =
        (0 until array.length()).map { index ->
            val topic = array.getJSONObject(index)
            SyllabusTopicNode(
                name = topic.getString("n"),
                hours = topic.optDouble("h").takeIf { topic.has("h") },
                children = if (topic.has("c")) parseTopics(topic.getJSONArray("c")) else emptyList(),
            )
        }
}
