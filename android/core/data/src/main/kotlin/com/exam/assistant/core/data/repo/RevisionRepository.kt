package com.exam.assistant.core.data.repo

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.domain.RevisionState
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Answers "when is this topic due again?" — actual review history lives in [StudySessionRepository]. */
class RevisionRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val dao = PrepTrackerDatabase.get(context).revisionStateDao()

    suspend fun byNode(attemptId: String, nodeId: String): RevisionState? =
        withContext(dispatchers.io) { dao.byNode(attemptId, nodeId)?.toDomain() }

    suspend fun dueBy(attemptId: String, date: LocalDate): List<RevisionState> =
        withContext(dispatchers.io) { dao.dueBy(attemptId, date.toEpochDay()).map { it.toDomain() } }
}
