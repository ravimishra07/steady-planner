package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.DEFAULT_PLANNING_HORIZON_DAYS
import com.exam.assistant.domain.PlanBlockSource
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.RollingPlanResult
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PlanningOrder
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.effectiveTargetLeafIds
import com.exam.assistant.domain.generateRollingPlan
import java.time.LocalDate
import kotlinx.coroutines.withContext

/**
 * Owns the rolling-plan transaction. All automatic future intent is derived
 * from canonical Room state and stable ExamPack node IDs.
 */
class RollingPlanRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
    private val examPackRepository: ExamPackRepository,
) {
    private val db = PrepTrackerDatabase.get(context)

    suspend fun replenish(
        fromDate: LocalDate = LocalDate.now(),
        horizonDays: Int = DEFAULT_PLANNING_HORIZON_DAYS,
        nowMs: Long = System.currentTimeMillis(),
    ): RollingPlanResult? {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return null
        val pack = examPackRepository.examPackFor(attempt.examId)
        return withContext(dispatchers.io) {
            db.withTransaction {
                val preferences = db.studyPreferenceDao().forAttempt(attempt.id)?.toDomain()
                val overrides = db.targetNodeOverrideDao().allOnce(attempt.id)
                    .associate { it.nodeId to TargetNodeState.valueOf(it.state) }
                val covered = db.topicProgressDao().allOnce(attempt.id)
                    .filter { TopicProgressStatus.valueOf(it.status) == TopicProgressStatus.COVERED }
                    .mapTo(mutableSetOf()) { it.nodeId }
                val focusedByNode = db.studySessionDao().completedLearnSumsByNode(attempt.id)
                    .associate { it.nodeId to (it.totalSeconds / 60) }
                val preserved = db.studyPlanBlockDao()
                    .between(attempt.id, fromDate.toEpochDay(), attempt.targetCompletionDate.toEpochDay())
                    .map { it.toDomain() }
                    .filterNot {
                        it.source == PlanBlockSource.AUTO &&
                            it.status == PlanBlockStatus.PLANNED &&
                            it.scheduledDate >= fromDate
                    }
                val result = generateRollingPlan(
                    attemptId = attempt.id,
                    pack = pack,
                    startDate = fromDate,
                    targetDate = attempt.targetCompletionDate,
                    weeklyAvailability = db.availabilityDao().weeklyFor(attempt.id).map { it.toDomain() },
                    availabilityOverrides = db.availabilityDao().overridesFor(attempt.id).map { it.toDomain() },
                    includedLeafIds = pack.effectiveTargetLeafIds(overrides),
                    coveredLeafIds = covered,
                    focusedLearnMinutesByNode = focusedByNode,
                    preservedBlocks = preserved,
                    weekdayTargetMinutes = preferences?.weekdayTargetMinutes ?: 4 * 60,
                    weekendTargetMinutes = preferences?.weekendTargetMinutes ?: 7 * 60,
                    preferredSessionMinutes = preferences?.preferredSessionMinutes ?: 50,
                    breakMinutes = preferences?.shortBreakMinutes ?: 10,
                    planningOrder = preferences?.planningOrder ?: com.exam.assistant.domain.PlanningOrder.DEFAULT,
                    horizonDays = horizonDays,
                    nowMs = nowMs,
                )
                db.studyPlanBlockDao().deleteFutureAuto(attempt.id, fromDate.toEpochDay())
                db.studyPlanBlockDao().upsertAll(result.blocks.map { it.toEntity() })
                result
            }
        }
    }

    /** Calculates a real draft without mutating Room, used by Organise before confirmation. */
    suspend fun preview(
        pack: ExamPack,
        overridesByNodeId: Map<String, TargetNodeState>,
        planningOrder: PlanningOrder,
        fromDate: LocalDate = LocalDate.now(),
        horizonDays: Int = 7,
        nowMs: Long = System.currentTimeMillis(),
    ): RollingPlanResult? {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return null
        return withContext(dispatchers.io) {
            val preferences = db.studyPreferenceDao().forAttempt(attempt.id)?.toDomain()
            val covered = db.topicProgressDao().allOnce(attempt.id)
                .filter { TopicProgressStatus.valueOf(it.status) == TopicProgressStatus.COVERED }
                .mapTo(mutableSetOf()) { it.nodeId }
            val focusedByNode = db.studySessionDao().completedLearnSumsByNode(attempt.id)
                .associate { it.nodeId to (it.totalSeconds / 60) }
            val preserved = db.studyPlanBlockDao()
                .between(attempt.id, fromDate.toEpochDay(), attempt.targetCompletionDate.toEpochDay())
                .map { it.toDomain() }
                .filterNot {
                    it.source == PlanBlockSource.AUTO &&
                        it.status == PlanBlockStatus.PLANNED &&
                        it.scheduledDate >= fromDate
                }
            generateRollingPlan(
                attemptId = attempt.id,
                pack = pack,
                startDate = fromDate,
                targetDate = attempt.targetCompletionDate,
                weeklyAvailability = db.availabilityDao().weeklyFor(attempt.id).map { it.toDomain() },
                availabilityOverrides = db.availabilityDao().overridesFor(attempt.id).map { it.toDomain() },
                includedLeafIds = pack.effectiveTargetLeafIds(overridesByNodeId),
                coveredLeafIds = covered,
                focusedLearnMinutesByNode = focusedByNode,
                preservedBlocks = preserved,
                weekdayTargetMinutes = preferences?.weekdayTargetMinutes ?: 4 * 60,
                weekendTargetMinutes = preferences?.weekendTargetMinutes ?: 7 * 60,
                preferredSessionMinutes = preferences?.preferredSessionMinutes ?: 50,
                breakMinutes = preferences?.shortBreakMinutes ?: 10,
                planningOrder = planningOrder,
                horizonDays = horizonDays,
                nowMs = nowMs,
            )
        }
    }
}
