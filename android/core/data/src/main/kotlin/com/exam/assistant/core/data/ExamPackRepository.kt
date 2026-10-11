package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.ExamSubject
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.SyllabusNode
import com.exam.assistant.domain.SyllabusNodeKind
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first

private val Context.customSyllabusDataStore by preferencesDataStore(name = "custom_syllabus")

data class CustomSyllabusChapter(
    val id: String,
    val examId: String,
    val subjectId: String,
    val subjectName: String,
    val title: String,
    val estimatedMinutes: Int,
    val topics: List<String> = emptyList(),
)

/**
 * Replaces [SyllabusRepository]'s positional parsing. Loads the versioned,
 * stable-ID [ExamPack] from the bundled asset — parsed off the critical
 * path, cached in memory, never re-parsed per screen.
 */
class ExamPackRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val bundledCache = mutableMapOf<String, ExamPack>()

    suspend fun examPack(): ExamPack = withContext(dispatchers.default) {
        bundledPack(NEET_EXAM_ID)
    }

    /**
     * Returns only the pack that belongs to [examId]. Exams without a bundled
     * native pack start empty and receive only user-authored chapters. This
     * prevents accidentally planning one exam's work for another exam.
     */
    suspend fun examPackFor(examId: String): ExamPack = withContext(dispatchers.default) {
        packWithCustom(examId, customChapters(examId))
    }

    suspend fun packWithCustom(examId: String, custom: List<CustomSyllabusChapter>): ExamPack = withContext(dispatchers.default) {
        val base = bundledPack(examId)
        mergeCustom(base, custom)
    }

    suspend fun customChapters(examId: String): List<CustomSyllabusChapter> = withContext(dispatchers.io) {
        val raw = context.customSyllabusDataStore.data.first()[customKey(examId)]
        if (raw.isNullOrBlank()) return@withContext emptyList()
        runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                CustomSyllabusChapter(
                    id = obj.getString("id"),
                    examId = examId,
                    subjectId = obj.getString("subjectId"),
                    subjectName = obj.getString("subjectName"),
                    title = obj.getString("title"),
                    estimatedMinutes = obj.getInt("minutes"),
                    topics = obj.optJSONArray("topics")?.let { topics ->
                        (0 until topics.length()).map(topics::getString)
                    }.orEmpty(),
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun saveCustomChapters(examId: String, chapters: List<CustomSyllabusChapter>) = withContext(dispatchers.io) {
        val array = JSONArray()
        chapters.forEach { chapter ->
            array.put(JSONObject().apply {
                put("id", chapter.id)
                put("subjectId", chapter.subjectId)
                put("subjectName", chapter.subjectName)
                put("title", chapter.title)
                put("minutes", chapter.estimatedMinutes)
                put("topics", JSONArray(chapter.topics))
            })
        }
        context.customSyllabusDataStore.edit { it[customKey(examId)] = array.toString() }
    }

    suspend fun clearCustomChapters(examId: String) = withContext(dispatchers.io) {
        context.customSyllabusDataStore.edit { it.remove(customKey(examId)) }
    }

    private fun customKey(examId: String) = stringPreferencesKey("chapters_$examId")

    private fun mergeCustom(base: ExamPack, custom: List<CustomSyllabusChapter>): ExamPack {
        if (custom.isEmpty()) return base
        val bySubject = custom.groupBy { it.subjectId }
        val existing = base.subjects.map { subject ->
            val additions = bySubject[subject.id].orEmpty().mapIndexed { index, chapter -> chapter.toNode(subject.nodes.size + index) }
            subject.copy(nodes = subject.nodes + additions)
        }
        val existingIds = existing.map { it.id }.toSet()
        val newSubjects = custom.filterNot { it.subjectId in existingIds }.groupBy { it.subjectId }.map { (subjectId, chapters) ->
            ExamSubject(
                id = subjectId,
                name = chapters.first().subjectName,
                order = existing.size,
                nodes = chapters.mapIndexed { index, chapter -> chapter.toNode(index) },
            )
        }
        return base.copy(subjects = existing + newSubjects)
    }

    private fun CustomSyllabusChapter.toNode(order: Int): SyllabusNode = SyllabusNode(
        id = id,
        title = title,
        kind = SyllabusNodeKind.CHAPTER,
        order = order,
        estimatedMinutes = if (topics.isEmpty()) estimatedMinutes else null,
        children = topics.mapIndexed { index, topic ->
            SyllabusNode(
                id = "${id}_topic_$index",
                title = topic,
                kind = SyllabusNodeKind.TOPIC,
                order = index,
                estimatedMinutes = (estimatedMinutes / topics.size).coerceAtLeast(1),
            )
        },
    )

    private fun bundledPack(examId: String): ExamPack = synchronized(bundledCache) {
        bundledCache[examId] ?: when (examId) {
            NEET_EXAM_ID -> loadExamPack("syllabus_neet.json")
            else -> ExamPack(
                schemaVersion = 1,
                examId = examId,
                displayName = examId.uppercase(),
                syllabusVersion = "custom-1",
                subjects = emptyList(),
            )
        }.also { bundledCache[examId] = it }
    }

    private fun loadExamPack(assetName: String): ExamPack {
        context.assets.open(assetName).bufferedReader().use { reader ->
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
                estimatedMinutes = when {
                    json.has("m") -> json.optInt("m")
                    json.has("h") -> (json.optDouble("h") * 60).toInt()
                    else -> null
                },
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
