package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.BatchPositionEntity
import com.exam.assistant.core.data.db.ChapterCoachingEntity
import com.exam.assistant.core.data.db.ChapterStageEntity
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toDomainOrNull
import com.exam.assistant.domain.BatchPosition
import com.exam.assistant.domain.ChapterCoaching
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.MAX_COACHING_COUNT
import com.exam.assistant.domain.ChapterStageMark
import com.exam.assistant.domain.RevisionState
import kotlinx.coroutines.withContext

/**
 * Per-chapter tracking beyond Learned: depth stages (§5.1) and coaching lecture/DPP counts
 * with the batch position (§5.4).
 *
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

    suspend fun coachingFor(attemptId: String): List<ChapterCoaching> =
        withContext(dispatchers.io) { db.coachingDao().allOnce(attemptId).map { it.toDomain() } }

    suspend fun batchPosition(attemptId: String): BatchPosition? =
        withContext(dispatchers.io) { db.coachingDao().positionFor(attemptId)?.toDomain() }

    /** Saves one chapter's counts, kept in range. All-zero counts remove the row. */
    suspend fun saveCoaching(attemptId: String, coaching: ChapterCoaching) = withContext(dispatchers.io) {
        val clean = coaching.normalized()
        if (clean.isEmpty) {
            db.coachingDao().delete(attemptId, clean.chapterId)
        } else {
            db.coachingDao().upsert(
                ChapterCoachingEntity(attemptId, clean.chapterId, clean.lectures, clean.dpps, clean.lecturesDone, clean.dppsDone),
            )
        }
    }

    /**
     * "Taught in class today": the batch is at [chapterId], lecture [lecture]. A chapter whose
     * lecture count is below that grows to match, since the batch has plainly added a lecture.
     */
    suspend fun setBatchPosition(attemptId: String, chapterId: String, lecture: Int, nowMs: Long) =
        withContext(dispatchers.io) {
            val at = lecture.coerceIn(1, MAX_COACHING_COUNT)
            db.withTransaction {
                db.coachingDao().upsertPosition(BatchPositionEntity(attemptId, chapterId, at, nowMs))
                val existing = db.coachingDao().allOnce(attemptId).firstOrNull { it.chapterId == chapterId }
                if (existing == null) {
                    db.coachingDao().upsert(ChapterCoachingEntity(attemptId, chapterId, at, 0, 0, 0))
                } else if (existing.lectures < at) {
                    db.coachingDao().upsert(existing.copy(lectures = at))
                }
            }
        }
}
