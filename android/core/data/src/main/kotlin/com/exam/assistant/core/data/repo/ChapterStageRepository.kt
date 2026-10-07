package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.ChapterStageEntity
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toDomainOrNull
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.ChapterStageMark
import com.exam.assistant.domain.RevisionState
import kotlinx.coroutines.withContext

/**
 * Depth stages per chapter (§5.1). Learned is not handled here: it goes through
 * [TopicProgressRepository.setCovered] so coverage keeps one source.
 */
class ChapterStageRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val db = PrepTrackerDatabase.get(context)
    private val dao = db.chapterStageDao()

    suspend fun marksFor(attemptId: String): List<ChapterStageMark> =
        withContext(dispatchers.io) { dao.allOnce(attemptId).mapNotNull { it.toDomainOrNull() } }

    /** Focus revision counters by node, the base of each chapter's "Revised ×N". */
    suspend fun revisionStatesFor(attemptId: String): Map<String, RevisionState> =
        withContext(dispatchers.io) {
            db.revisionStateDao().allEnabled(attemptId).associate { it.nodeId to it.toDomain() }
        }

    /** One-tap tick: present → removed, absent → added. */
    suspend fun toggle(attemptId: String, chapterId: String, stage: ChapterStage, nowMs: Long) =
        withContext(dispatchers.io) {
            db.withTransaction {
                if (dao.byKey(attemptId, chapterId, stage.name) != null) {
                    dao.delete(attemptId, chapterId, stage.name)
                } else {
                    dao.upsert(ChapterStageEntity(attemptId, chapterId, stage.name, 1, nowMs))
                }
            }
        }

    /** Adds [delta] to a counted stage (Revised). Never below zero; zero removes the row. */
    suspend fun adjustCount(attemptId: String, chapterId: String, stage: ChapterStage, delta: Int, nowMs: Long) =
        withContext(dispatchers.io) {
            db.withTransaction {
                val next = ((dao.byKey(attemptId, chapterId, stage.name)?.count ?: 0) + delta).coerceAtLeast(0)
                if (next == 0) {
                    dao.delete(attemptId, chapterId, stage.name)
                } else {
                    dao.upsert(ChapterStageEntity(attemptId, chapterId, stage.name, next, nowMs))
                }
            }
        }
}
