package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.core.data.R
import com.exam.assistant.domain.BacklogItem
import com.exam.assistant.domain.Calibration
import com.exam.assistant.domain.DEFAULT_LIVE_BACKLOG_SPLIT
import com.exam.assistant.domain.PlannerConfig
import com.exam.assistant.domain.chapters
import com.exam.assistant.domain.coachingBacklogItems
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
import com.exam.assistant.domain.StartingPoint
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.effectiveTargetLeafIds
import com.exam.assistant.domain.isReplacedByReplanFrom
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.missedDayCount
import com.exam.assistant.domain.missedPlannedBlocks
import com.exam.assistant.domain.personalCalibration
import com.exam.assistant.domain.plan
import com.exam.assistant.domain.planFit
import com.exam.assistant.domain.projectedFinish
import com.exam.assistant.domain.skipMissedBlocks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Internal so BackupRepository can export and restore it (one DataStore per file per process).
internal val Context.planPromptDataStore by preferencesDataStore(name = "plan_prompts")
private val missedResolvedThroughKey = longPreferencesKey("missed_resolved_through")
private val missedDismissedOnKey = longPreferencesKey("missed_dismissed_on")
private val freshStartOnKey = longPreferencesKey("fresh_start_on")
private val liveBacklogSplitKey = intPreferencesKey("live_backlog_split")

/**
 * "You missed N blocks. Rebalance the next days?" Blocks are counted from [since] up to yesterday.
 * [days] is the recent run of days with missed blocks ([missedDayCount]).
 */
data class MissedPrompt(val count: Int, val since: LocalDate, val days: Int = 1)

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
        val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
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

    /** Percent of each day for live coaching work (§5.4); the rest goes to backlog. */
    suspend fun liveBacklogSplit(): Int =
        appContext.planPromptDataStore.data.first()[liveBacklogSplitKey]?.coerceIn(0, 100) ?: DEFAULT_LIVE_BACKLOG_SPLIT

    /** Saves the live/backlog split and replans from today so the change shows at once. */
    suspend fun setLiveBacklogSplit(percent: Int) {
        appContext.planPromptDataStore.edit { it[liveBacklogSplitKey] = percent.coerceIn(0, 100) }
        replenish()
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
        val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
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
        val inputs = draftInputs(draft, nowMs)
        return withContext(dispatchers.default) { inputs.planFit(nowMs) }
    }

    /**
     * Steady mode's projected finish for a draft: the day the planner would place the last Learn or
     * Practice block at the draft's hours, or null if the work does not end before the exam.
     */
    suspend fun draftProjection(draft: PlanDraft, nowMs: Long = System.currentTimeMillis()): LocalDate? {
        val inputs = draftInputs(draft, nowMs)
        return withContext(dispatchers.default) { inputs.projectedFinish(nowMs) }
    }

    /** The projected finish for the saved plan (steady mode), from the same planner as [replenish]. */
    suspend fun projectedFinish(nowMs: Long = System.currentTimeMillis()): LocalDate? {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return null
        val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
        val priorities = examPackRepository.pyqPriorities().chapters
        val inputs = withContext(dispatchers.io) { loadInputs(attempt, pack, priorities, LocalDate.now(), 1, nowMs) }
        return withContext(dispatchers.default) { inputs.projectedFinish(nowMs) }
    }

    private suspend fun draftInputs(draft: PlanDraft, nowMs: Long): PlanInputs {
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
        return withContext(dispatchers.io) {
            loadInputs(
                attempt, pack, priorities, LocalDate.now(), 1, nowMs,
                overridesByNodeId = parked.associateWith { TargetNodeState.EXCLUDED },
            )
        }.let { loaded ->
            loaded.copy(
                weekdayTargetMinutes = draft.weekdayTargetMinutes,
                weekendTargetMinutes = draft.weekendTargetMinutes,
                weeklyAvailability = draft.weeklyAvailability,
                availabilityOverrides = emptyList(),
                coveredLeafIds = draftCoveredLeaves(pack, loaded.coveredLeafIds, draft.coveredChapterIds),
                revisionShaped = draft.startingPoint?.revisionShaped ?: loaded.revisionShaped,
            )
        }
    }

    /**
     * Finished work as the student has it on screen: chapters ticked in the draft count, chapters that
     * were finished when the screen opened but are unticked now do not, and any partial progress
     * elsewhere stays. This is what makes "edit plan" subtract finished work from the need.
     */
    private fun draftCoveredLeaves(pack: ExamPack, saved: Set<String>, finished: Set<String>): Set<String> {
        val chapters = pack.subjects.flatMap { it.nodes }
        val wasFinished = chapters.filter { it.leafIds().isNotEmpty() && saved.containsAll(it.leafIds()) }.map { it.id }.toSet()
        val unticked = chapters.filter { it.id in wasFinished && it.id !in finished }.flatMap { it.leafIds() }.toSet()
        val ticked = chapters.filter { it.id in finished }.flatMap { it.leafIds() }
        return saved - unticked + ticked
    }

    /** The student's pace against the estimates, from completed Learn sessions. [Calibration.NONE] with no plan. */
    suspend fun calibration(): Calibration {
        val attempt = withContext(dispatchers.io) { db.examAttemptDao().activeOnce()?.toDomain() } ?: return Calibration.NONE
        val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
        return withContext(dispatchers.io) { calibrationFor(attempt, pack) }
    }

    /** Planner-made blocks from earlier days that were never done, since [since] (inclusive). */
    suspend fun missedBlocks(since: LocalDate, today: LocalDate = LocalDate.now()): List<StudyPlanBlock> =
        missedWithPlannedDates(since, today).first

    /** The missed blocks, plus every earlier date that had planner blocks (for [missedDayCount]). */
    private suspend fun missedWithPlannedDates(
        since: LocalDate,
        today: LocalDate,
    ): Pair<List<StudyPlanBlock>, Set<LocalDate>> =
        withContext(dispatchers.io) {
            val attempt = db.examAttemptDao().activeOnce()?.toDomain()
                ?: return@withContext emptyList<StudyPlanBlock>() to emptySet()
            val blocks = db.studyPlanBlockDao().between(attempt.id, since.toEpochDay(), today.minusDays(1).toEpochDay())
                .map { it.toDomain() }
            val sessions = db.studySessionDao().between(attempt.id, since.toEpochDay(), today.toEpochDay()).map { it.toDomain() }
            val planned = blocks.filter { it.status != PlanBlockStatus.RESCHEDULED }.mapTo(mutableSetOf()) { it.scheduledDate }
            missedPlannedBlocks(blocks, sessions, today, since) to planned
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
        val (missed, planned) = missedWithPlannedDates(since, today)
        return if (missed.isNotEmpty()) MissedPrompt(missed.size, since, missedDayCount(missed, planned)) else null
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

    /**
     * "Start fresh from today" (PRODUCTION_V1_PLAN §5.5): the same move as [rebalanceMissed], and
     * today is remembered so Today shows no catch-up count for the rest of the day.
     */
    suspend fun startFresh(
        since: LocalDate,
        today: LocalDate = LocalDate.now(),
        nowMs: Long = System.currentTimeMillis(),
    ): RollingPlanResult? {
        val result = rebalanceMissed(since, today, nowMs)
        appContext.planPromptDataStore.edit { it[freshStartOnKey] = today.toEpochDay() }
        return result
    }

    /** Did the student start fresh on [today]? */
    suspend fun startedFreshOn(today: LocalDate = LocalDate.now()): Boolean =
        appContext.planPromptDataStore.data.first()[freshStartOnKey] == today.toEpochDay()

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
            revisionShaped = attempt.startingPoint?.revisionShaped ?: false,
            revisionStates = db.revisionStateDao().allEnabled(attempt.id).map { it.toDomain() },
            weakNodeCounts = db.studySessionDao().struggledCountsByNode(attempt.id).associate { it.nodeId to it.total },
            practicedChapterIds = practicedChapters,
            recentMockDates = recentMocks,
            coachingBacklog = coachingBacklogFor(attempt, pack),
            config = PlannerConfig(
                liveBacklogSplit = liveBacklogSplit(),
                backlogLectureTitle = appContext.getString(R.string.plan_backlog_lecture_title),
                backlogDppTitle = appContext.getString(R.string.plan_backlog_dpp_title),
            ),
        )
    }

    /** Coaching lectures and DPPs behind the batch, in syllabus order. Empty for a student who does not use coaching. */
    private suspend fun coachingBacklogFor(attempt: ExamAttempt, pack: ExamPack): List<BacklogItem> {
        val coaching = db.coachingDao().allOnce(attempt.id).map { it.toDomain() }
        if (coaching.isEmpty()) return emptyList()
        val position = db.coachingDao().positionFor(attempt.id)?.toDomain()
        val subjectOf = pack.subjects.flatMap { subject -> subject.nodes.map { it.id to subject.id } }.toMap()
        return coachingBacklogItems(coaching, position, pack.chapters().map { it.id }, subjectOf)
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
    /** Chapters the student marked as finished (Learned) in this setup. */
    val coveredChapterIds: Set<String> = emptySet(),
    val startingPoint: StartingPoint? = null,
)
