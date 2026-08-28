package com.exam.assistant.core.data.repo

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.TargetNodeOverride
import com.exam.assistant.domain.TargetNodeState
import kotlinx.coroutines.withContext

/** "I am intentionally not preparing this" — never deletes static syllabus data, only overrides target membership. */
class TargetSyllabusRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val dao = PrepTrackerDatabase.get(context).targetNodeOverrideDao()

    suspend fun overridesFor(attemptId: String): List<TargetNodeOverride> =
        withContext(dispatchers.io) { dao.allOnce(attemptId).map { it.toDomain() } }

    suspend fun setState(attemptId: String, nodeId: String, state: TargetNodeState, nowMs: Long) =
        withContext(dispatchers.io) {
            dao.upsert(TargetNodeOverride(attemptId, nodeId, state, nowMs).toEntity())
        }

    suspend fun clearOverride(attemptId: String, nodeId: String) =
        withContext(dispatchers.io) { dao.delete(attemptId, nodeId) }
}
