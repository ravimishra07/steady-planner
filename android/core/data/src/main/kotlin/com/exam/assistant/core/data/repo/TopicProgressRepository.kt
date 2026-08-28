package com.exam.assistant.core.data.repo

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.toggledTopicProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What has the student explicitly marked covered — the Syllabus checkmark's one source of truth. */
class TopicProgressRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val dao = PrepTrackerDatabase.get(context).topicProgressDao()

    fun observeAll(attemptId: String): Flow<List<TopicProgress>> =
        dao.observeAll(attemptId).map { list -> list.map { it.toDomain() } }

    suspend fun allOnce(attemptId: String): List<TopicProgress> =
        withContext(dispatchers.io) { dao.allOnce(attemptId).map { it.toDomain() } }

    suspend fun byNode(attemptId: String, nodeId: String): TopicProgress? =
        withContext(dispatchers.io) { dao.byNode(attemptId, nodeId)?.toDomain() }

    suspend fun upsert(progress: TopicProgress) = withContext(dispatchers.io) { dao.upsert(progress.toEntity()) }

    suspend fun upsertAll(progress: List<TopicProgress>) = withContext(dispatchers.io) {
        dao.upsertAll(progress.map { it.toEntity() })
    }

    /** The one entry point for "tick this topic" — centralizes the toggle rule (see [toggledTopicProgress]). */
    suspend fun toggle(attemptId: String, nodeId: String, nowMs: Long): TopicProgress = withContext(dispatchers.io) {
        val current = dao.byNode(attemptId, nodeId)?.toDomain()
        val updated = toggledTopicProgress(current, attemptId, nodeId, nowMs)
        dao.upsert(updated.toEntity())
        updated
    }
}
