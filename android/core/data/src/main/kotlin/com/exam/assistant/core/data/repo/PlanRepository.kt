package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.rescheduleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class PlanRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val db = PrepTrackerDatabase.get(context)
    private val dao = db.studyPlanBlockDao()

    fun observePlanForDate(attemptId: String, date: LocalDate): Flow<List<StudyPlanBlock>> =
        dao.observeForDate(attemptId, date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    suspend fun planForDate(attemptId: String, date: LocalDate): List<StudyPlanBlock> =
        withContext(dispatchers.io) { dao.forDateOnce(attemptId, date.toEpochDay()).map { it.toDomain() } }

    suspend fun planBetween(attemptId: String, start: LocalDate, end: LocalDate): List<StudyPlanBlock> =
        withContext(dispatchers.io) {
            dao.between(attemptId, start.toEpochDay(), end.toEpochDay()).map { it.toDomain() }
        }

    suspend fun upsert(block: StudyPlanBlock) = withContext(dispatchers.io) { dao.upsert(block.toEntity()) }

    suspend fun upsertAll(blocks: List<StudyPlanBlock>) = withContext(dispatchers.io) {
        dao.upsertAll(blocks.map { it.toEntity() })
    }

    suspend fun byId(id: String): StudyPlanBlock? = withContext(dispatchers.io) { dao.byId(id)?.toDomain() }

    /**
     * Reschedule preserves history: the original block becomes RESCHEDULED
     * and links to its replacement; the replacement links back. Never
     * mutates the original's date/time in place.
     */
    suspend fun reschedule(
        original: StudyPlanBlock,
        newDate: LocalDate,
        newStartMinuteOfDay: Int,
        newBlockId: String,
        nowMs: Long,
    ): StudyPlanBlock = withContext(dispatchers.io) {
        val (closedOriginal, replacement) = rescheduleBlock(original, newDate, newStartMinuteOfDay, newBlockId, nowMs)
        db.withTransaction {
            dao.upsert(closedOriginal.toEntity())
            dao.upsert(replacement.toEntity())
        }
        replacement
    }
}
