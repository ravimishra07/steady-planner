package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.Calibration
import com.exam.assistant.domain.DEFAULT_PLANNING_HORIZON_DAYS
import com.exam.assistant.domain.ExamAttempt
import com.exam.assistant.domain.ExamAttemptStatus
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.WeeklyAvailability
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.PlanFit
import com.exam.assistant.domain.PlanInputs
import com.exam.assistant.domain.PlanMode
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.PlanningOrder
import com.exam.assistant.domain.RollingPlanResult
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.effectiveTargetLeafIds
import com.exam.assistant.domain.isReplacedByReplanFrom
import com.exam.assistant.domain.missedPlannedBlocks
import com.exam.assistant.domain.personalCalibration
import com.exam.assistant.domain.plan
import com.exam.assistant.domain.planFit
import com.exam.assistant.domain.skipMissedBlocks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.planPromptDataStore by preferencesDataStore(name = "plan_prompts")
private val missedResolvedThroughKey = longPreferencesKey("missed_resolved_through")
private val missedDismissedOnKey = longPreferencesKey("missed_dismissed_on")

/** "You missed N blocks. Rebalance the next days?" Blocks are counted from [since] up to yesterday. */
data class MissedPrompt(val count: Int, val since: LocalDate)

/**
 * Owns the rolling-plan transaction. All automatic future intent is derived
 * from canonical Room state and stable ExamPack node IDs. Every consumer that
 * needs a plan, a fit or a calibration goes through here, so they are all
 * computed from the same inputs by the same domain functions.
 */
class RollingPlanRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
    private val examPackRepository: ExamPackRepository,
) {
    private val appContext = context.applicationContext
    private val db = PrepTrackerDatabase.get(context)

    /** The day the horizon was last topped up in this process, so reopening the app the same day does not replan again. */
    @Volatile
    private var lastTopUpDate: LocalDate? = null

    /**
     * Regenerates future planner-made blocks from [fromDate] (never before now today) out to
     * [horizonDays]. Completed, started, manual, moved and past blocks stay. Safe to call on
     * every app open: that is the daily top-up.
     */
    suspend fun replenish(
        fromDate: LocalDate = LocalDate.now(),
        horizonDays: Int = DEFAULT_PLANNING_HORIZON_DAYS,
        nowMs: Long = System.currentTimeMillis(),
    ): RollingPlanResult? {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return null
        val pack = examPackRepository.examPackFor(attempt.examId)
        val priorities = examPackRepository.pyqPriorities().chapters
        return withContext(dispatchers.io) {
            db.withTransaction {
                val inputs = loadInputs(attempt, pack, priorities, fromDate, horizonDays, nowMs)
                val result = inputs.plan(horizonDays, nowMs)
                db.studyPlanBlockDao().deleteReplaceable(attempt.id, fromDate.toEpochDay(), fromMinuteFor(fromDate, nowMs))
                db.studyPlanBlockDao().upsertAll(result.blocks.map { it.toEntity() })
                if (fromDate == LocalDate.now()) lastTopUpDate = fromDate
                result
            }
        }
    }

    /** The daily top-up back to the planning horizon, once per calendar day per process. No WorkManager yet. */
    suspend fun topUpIfNewDay(today: LocalDate = LocalDate.now()) {
        if (lastTopUpDate == today) return
        replenish(fromDate = today)
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
        val priorities = examPackRepository.pyqPriorities().chapters
        return withContext(dispatchers.io) {
            val inputs = loadInputs(attempt, pack, priorities, fromDate, horizonDays, nowMs, overridesByNodeId, planningOrder)
            inputs.plan(horizonDays, nowMs)
        }
    }

    /**
     * Whether the remaining work fits before finish-by, with the ways to fix it when it does not.
     * Null in steady mode and with no plan. The same planner as [replenish], so it cannot disagree.
     */
    suspend fun planFit(nowMs: Long = System.currentTimeMillis(), withOptions: Boolean = true): PlanFit? {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return null
        if (attempt.planMode != PlanMode.DEADLINE) return null
        val pack = examPackRepository.examPackFor(attempt.examId)
        val priorities = examPackRepository.pyqPriorities().chapters
        val inputs = withContext(dispatchers.io) {
            loadInputs(attempt, pack, priorities, LocalDate.now(), 1, nowMs)
        }
        return withContext(dispatchers.default) { inputs.planFit(nowMs, withOptions) }
    }

    /**
     * The same fit, for a plan the student is still shaping in onboarding. The student's real
     * history (covered work, focused time, revision states, calibration, kept blocks) comes
     * from Room, so editing a plan subtracts what is already done; [draft] supplies what they are
     * changing. Null in steady mode.
     */
    suspend fun draftFit(draft: PlanDraft, nowMs: Long = System.currentTimeMillis()): PlanFit? {
        if (draft.mode != PlanMode.DEADLINE) return null
        val existing = withContext(dispatchers.io) {
            db.examAttemptDao().activeOnce()?.toDomain()?.takeIf { it.examId == NEET_EXAM_ID }
        }
        val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
        val priorities = examPackRepository.pyqPriorities().chapters
        val attempt = (existing ?: ExamAttempt(
            id = DRAFT_ATTEMPT_ID, examId = NEET_EXAM_ID, syllabusVersion = pack.syllabusVersion,
            examDate = draft.examDate, targetCompletionDate = draft.finishBy,
            createdAtEpochMs = nowMs, updatedAtEpochMs = nowMs, status = ExamAttemptStatus.ACTIVE,
        )).copy(examDate = draft.examDate, targetCompletionDate = draft.finishBy, planMode = draft.mode)
        val parked = pack.subjects.flatMap { it.nodes }.map { it.id }.filter { it !in draft.includedChapterIds }
        val inputs = withContext(dispatchers.io) {
            loadInputs(
                attempt, pack, priorities, LocalDate.now(), 1, nowMs,
                overridesByNodeId = parked.associateWith { TargetNodeState.EXCLUDED },
            )
        }.copy(
            weekdayTargetMinutes = draft.weekdayTargetMinutes,
            weekendTargetMinutes = draft.weekendTargetMinutes,
            weeklyAvailability = draft.weeklyAvailability,
            availabilityOverrides = emptyList(),
        )
        return withContext(dispatchers.default) { inputs.planFit(nowMs) }
    }

    /** The student's pace against the estimates, from completed Learn sessions. [Calibration.NONE] with no plan. */
    suspend fun calibration(): Calibration {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return Calibration.NONE
        val pack = examPackRepository.examPackFor(attempt.examId)
        return withContext(dispatchers.io) { calibrationFor(attempt, pack) }
    }

    /** Planner-made blocks from earlier days that were never done, since [since] (inclusive). */
    suspend fun missedBlocks(since: LocalDate, today: LocalDate = LocalDate.now()): List<StudyPlanBlock> =
        withContext(dispatchers.io) {
            val attempt = db.examAttemptDao().activeOnce()?.toDomain() ?: return@withContext emptyList()
            val blocks = db.studyPlanBlockDao().between(attempt.id, since.toEpochDay(), today.minusDays(1).toEpochDay())
                .map { it.toDomain() }
            val sessions = db.studySessionDao().between(attempt.id, since.toEpochDay(), today.toEpochDay()).map { it.toDomain() }
            missedPlannedBlocks(blocks, sessions, today, since)
        }

    /**
     * The "whole day missed" prompt: set when at least one planner-made block from an earlier day was
     * never done, since the last time the student answered, and not dismissed for today.
     */
    suspend fun missedPrompt(today: LocalDate = LocalDate.now()): MissedPrompt? {
        val prefs = appContext.planPromptDataStore.data.first()
        if (prefs[missedDismissedOnKey] == today.toEpochDay()) return null
        val resolved = prefs[missedResolvedThroughKey]?.let(LocalDate::ofEpochDay)
        val since = maxOf(resolved ?: today.minusDays(MISSED_LOOKBACK_DAYS), today.minusDays(MISSED_LOOKBACK_DAYS))
        val count = missedBlocks(since, today).size
        return if (count > 0) MissedPrompt(count, since) else null
    }

    /** "Not now": hide the prompt for the rest of [today]. It comes back tomorrow if blocks are still missed. */
    suspend fun dismissMissedPrompt(today: LocalDate = LocalDate.now()) {
        appContext.planPromptDataStore.edit { it[missedDismissedOnKey] = today.toEpochDay() }
    }

    /**
     * "Rebalance the next days": the missed blocks become SKIPPED and the plan is regenerated from today,
     * which puts their unfinished work back in the queue.
     */
    suspend fun rebalanceMissed(
        since: LocalDate,
        today: LocalDate = LocalDate.now(),
        nowMs: Long = System.currentTimeMillis(),
    ): RollingPlanResult? {
        val missed = missedBlocks(since, today)
        withContext(dispatchers.io) {
            val skipped = skipMissedBlocks(missed, nowMs)
            if (skipped.isNotEmpty()) db.studyPlanBlockDao().markSkipped(skipped.map { it.id }, nowMs)
            appContext.planPromptDataStore.edit { it[missedResolvedThroughKey] = today.toEpochDay() }
        }
        return replenish(fromDate = today, nowMs = nowMs)
    }

    private suspend fun calibrationFor(attempt: ExamAttempt, pack: ExamPack): Calibration {
        val covered = coveredLeafIds(attempt)
        val focused = db.studySessionDao().completedLearnSumsByNode(attempt.id).associate { it.nodeId to (it.totalSeconds / 60) }
        return personalCalibration(pack, focused, covered)
    }

    private suspend fun coveredLeafIds(attempt: ExamAttempt): Set<String> =
        db.topicProgressDao().allOnce(attempt.id)
            .filter { TopicProgressStatus.valueOf(it.status) == TopicProgressStatus.COVERED }
            .mapTo(mutableSetOf()) { it.nodeId }

    /** Everything the planner reads, loaded once. Replan, preview and fit all start here. */
    private suspend fun loadInputs(
        attempt: ExamAttempt,
        pack: ExamPack,
        priorities: Map<String, com.exam.assistant.domain.ChapterPriority>,
        fromDate: LocalDate,
        horizonDays: Int,
        nowMs: Long,
        overridesByNodeId: Map<String, TargetNodeState>? = null,
        planningOrderOverride: PlanningOrder? = null,
    ): PlanInputs {
        val preferences = db.studyPreferenceDao().forAttempt(attempt.id)?.toDomain()
        val overrides = overridesByNodeId ?: db.targetNodeOverrideDao().allOnce(attempt.id)
            .associate { it.nodeId to TargetNodeState.valueOf(it.state) }
        val covered = coveredLeafIds(attempt)
        val focusedByNode = db.studySessionDao().completedLearnSumsByNode(attempt.id).associate { it.nodeId to (it.totalSeconds / 60) }
        val calibration = personalCalibration(pack, focusedByNode, covered)
        val deadline = attempt.planMode == PlanMode.DEADLINE
        val rangeEnd = if (deadline) {
            maxOf(attempt.targetCompletionDate, attempt.examDate ?: attempt.targetCompletionDate)
        } else {
            fromDate.plusDays(horizonDays.toLong() + 60)
        }
        val fromMinute = fromMinuteFor(fromDate, nowMs)
        val inRange = db.studyPlanBlockDao()
            .between(attempt.id, fromDate.minusDays(MOCK_LOOKBACK_DAYS).toEpochDay(), rangeEnd.toEpochDay())
            .map { it.toDomain() }
        val preserved = inRange
            .filter { it.scheduledDate >= fromDate }
            .filterNot { it.isReplacedByReplanFrom(fromDate, fromMinute) }
        val recentMocks = inRange
            .filter {
                it.scheduledDate < fromDate &&
                    it.activityType == StudyActivityType.MOCK_TEST &&
                    it.status == PlanBlockStatus.COMPLETED
            }
            .map { it.scheduledDate }
        val practicedNodes = db.studySessionDao().completedNodeIdsOfType(attempt.id, StudyActivityType.PRACTICE.name).toSet()
        val practicedChapters = pack.subjects.flatMap { it.nodes }
            .filter { chapter -> practicedNodes.isNotEmpty() && chapter.flattenIds().any { it in practicedNodes } }
            .mapTo(mutableSetOf()) { it.id }
        return PlanInputs(
            attemptId = attempt.id,
            pack = pack,
            startDate = fromDate,
            targetDate = if (deadline) attempt.targetCompletionDate else fromDate.plusDays(horizonDays.toLong()),
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
            planningOrder = planningOrderOverride ?: preferences?.planningOrder ?: PlanningOrder.DEFAULT,
            mode = attempt.planMode,
            examDate = attempt.examDate,
            allowLearnInRevisionPhase = preferences?.allowLearnInRevisionPhase ?: false,
            calibrationFactor = calibration.factor,
            calibrationActive = calibration.active,
            priorities = priorities,
            revisionStates = db.revisionStateDao().allEnabled(attempt.id).map { it.toDomain() },
            weakNodeCounts = db.studySessionDao().struggledCountsByNode(attempt.id).associate { it.nodeId to it.total },
            practicedChapterIds = practicedChapters,
            recentMockDates = recentMocks,
        )
    }

    /** The minute of day a replan from [fromDate] starts at: now when that is today, else the start of the day. */
    private fun fromMinuteFor(fromDate: LocalDate, nowMs: Long): Int {
        val now = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
        return if (now.toLocalDate() == fromDate) now.hour * 60 + now.minute else 0
    }

    private fun com.exam.assistant.domain.SyllabusNode.flattenIds(): List<String> =
        listOf(id) + children.flatMap { it.flattenIds() }

    private companion object {
        const val MOCK_LOOKBACK_DAYS = 14L
        const val MISSED_LOOKBACK_DAYS = 14L
        const val DRAFT_ATTEMPT_ID = "draft"
    }
}

/** What the student is choosing in onboarding, before it is saved. */
data class PlanDraft(
    val mode: PlanMode,
    val examDate: LocalDate,
    val finishBy: LocalDate,
    val weekdayTargetMinutes: Int,
    val weekendTargetMinutes: Int,
    val weeklyAvailability: List<WeeklyAvailability>,
    /** Chapters (top-level syllabus nodes) the student is preparing; every other chapter is parked. */
    val includedChapterIds: Set<String>,
)
