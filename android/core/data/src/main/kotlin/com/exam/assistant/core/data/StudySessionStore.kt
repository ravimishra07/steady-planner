package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.parseStoreDate
import com.exam.assistant.domain.toStoreString
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.studySessionDataStore by preferencesDataStore(name = "study_sessions")

class StudySessionStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val sessionsKey = stringPreferencesKey("sessions_json")

    suspend fun loadAll(): List<StudySessionRecord> = withContext(dispatchers.io) {
        val raw = context.studySessionDataStore.data.first()[sessionsKey]
        if (raw.isNullOrBlank()) return@withContext emptyList()
        parseSessions(raw)
    }

    suspend fun sessionsFor(date: LocalDate): List<StudySessionRecord> =
        loadAll().filter { it.date == date }

    suspend fun upsert(session: StudySessionRecord) = withContext(dispatchers.io) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == session.id }
        if (index >= 0) all[index] = session else all += session
        saveAll(all)
    }

    /** Merges many records in one read-modify-write — for upsert() in a loop use this instead. */
    suspend fun upsertAll(sessions: List<StudySessionRecord>) = withContext(dispatchers.io) {
        val all = loadAll().associateBy { it.id }.toMutableMap()
        sessions.forEach { all[it.id] = it }
        saveAll(all.values.toList())
    }

    suspend fun clear() = withContext(dispatchers.io) {
        context.studySessionDataStore.edit { it.remove(sessionsKey) }
        Unit
    }

    private suspend fun saveAll(sessions: List<StudySessionRecord>) {
        context.studySessionDataStore.edit {
            it[sessionsKey] = encodeSessions(sessions)
        }
    }

    private fun parseSessions(raw: String): List<StudySessionRecord> {
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val date = parseStoreDate(obj.getString("date")) ?: return@mapNotNull null
            StudySessionRecord(
                id = obj.getString("id"),
                date = date,
                startMinuteOfDay = obj.getInt("startMin"),
                durationMinutes = obj.getInt("durationMin"),
                nodeKey = obj.getString("nodeKey"),
                title = obj.getString("title"),
                sectionName = obj.getString("section"),
                subjectId = obj.getString("subjectId"),
                isRevision = obj.optBoolean("revision", false),
                completed = obj.optBoolean("completed", false),
                runningEndsAtMs = if (obj.has("endsAtMs") && !obj.isNull("endsAtMs")) {
                    obj.getLong("endsAtMs")
                } else {
                    null
                },
            )
        }
    }

    private fun encodeSessions(sessions: List<StudySessionRecord>): String {
        val array = JSONArray()
        sessions.forEach { session ->
            val obj = JSONObject()
            obj.put("id", session.id)
            obj.put("date", session.date.toStoreString())
            obj.put("startMin", session.startMinuteOfDay)
            obj.put("durationMin", session.durationMinutes)
            obj.put("nodeKey", session.nodeKey)
            obj.put("title", session.title)
            obj.put("section", session.sectionName)
            obj.put("subjectId", session.subjectId)
            obj.put("revision", session.isRevision)
            obj.put("completed", session.completed)
            if (session.runningEndsAtMs != null) obj.put("endsAtMs", session.runningEndsAtMs)
            array.put(obj)
        }
        return array.toString()
    }
}
