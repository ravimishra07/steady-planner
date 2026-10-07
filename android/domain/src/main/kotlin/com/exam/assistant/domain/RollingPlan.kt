package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

const val DEFAULT_PLANNING_HORIZON_DAYS = 21
const val DEFAULT_STUDY_SESSION_MINUTES = 50
const val DEFAULT_STUDY_BREAK_MINUTES = 10

/** Practice block length as a share of the chapter's estimate (PRODUCTION_V1_PLAN §4.3). */
const val PRACTICE_FRACTION = 0.25
const val PRACTICE_MIN_MINUTES = 20
const val PRACTICE_MAX_MINUTES = 90

/** A revision block is 20-30 minutes; 25 by default. */
const val REVISION_BLOCK_MINUTES = 25
const val REVISION_MIN_BLOCK_MINUTES = 20

/** Revision-engine blocks may take at most this share of a day's target minutes. */
const val REVISION_DAY_CAP_FRACTION = 0.4

/** One revision pass over a chapter in the revision phase. */
const val REVISION_PASS_MINUTES = 45

/** NEET is a 200-minute paper. A mock only goes in a free window that long. */
const val MOCK_TEST_MINUTES = 200

/** Mock density by distance to the exam: more than this many days, one per 14 days. */
const val MOCK_FAR_DAYS = 56

/** From [MOCK_NEAR_DAYS] to [MOCK_FAR_DAYS] days, one per week; closer than that, three per week. */
const val MOCK_NEAR_DAYS = 28

/** Smallest block the planner will cut to fit a leftover window. */
const val MIN_AUTO_BLOCK_MINUTES = 15

/** How far steady mode looks for a finish date when the student has no exam date. */
const val PROJECTION_DAYS = 730L

/** Ids of blocks the revision engine placed start with this, so they are told apart from a student's own revision blocks. */
const val ENGINE_REVISION_ID_PREFIX = "rev_"

/** Block sizes the planner uses. Defaults are the constants above. */
data class PlannerConfig(
    val practiceFraction: Double = PRACTICE_FRACTION,
    val practiceMinMinutes: Int = PRACTICE_MIN_MINUTES,
    val practiceMaxMinutes: Int = PRACTICE_MAX_MINUTES,
    val revisionBlockMinutes: Int = REVISION_BLOCK_MINUTES,
    val revisionMinBlockMinutes: Int = REVISION_MIN_BLOCK_MINUTES,
    val revisionDayCapFraction: Double = REVISION_DAY_CAP_FRACTION,
    val revisionPassMinutes: Int = REVISION_PASS_MINUTES,
    val mockMinutes: Int = MOCK_TEST_MINUTES,
    val minBlockMinutes: Int = MIN_AUTO_BLOCK_MINUTES,
    /**
     * Coaching backlog (§5.4): percent of each day for live work (0–100). The rest goes to
     * backlog lectures and DPPs, when the student has any. No backlog, no change.
     */
    val liveBacklogSplit: Int = DEFAULT_LIVE_BACKLOG_SPLIT,
    val backlogLectureMinutes: Int = BACKLOG_LECTURE_MINUTES,
    val backlogDppMinutes: Int = BACKLOG_DPP_MINUTES,
    /** Block titles: `%1$s` is the chapter's display title, `%2$d` the lecture or DPP number. */
    val backlogLectureTitle: String = "Backlog: %1${'$'}s lecture %2${'$'}d",
    val backlogDppTitle: String = "Backlog: %1${'$'}s DPP %2${'$'}d",
)

data class RemainingWork(
    val nodeId: String,
    val subjectId: String,
    val estimatedMinutes: Int,
    val remainingMinutes: Int,
)

data class RollingPlanResult(
    val blocks: List<StudyPlanBlock>,
    /** Learn work not yet placed when the emitted horizon ends. */
    val remainingWork: List<RemainingWork>,
    /** Learn minutes not placed when the emitted horizon ends. */
    val unscheduledMinutes: Int,
    /** Deadline mode only: whether the work fits before finish-by. Null in steady mode. */
    val fit: PlanFit? = null,
    /** Mock tests the whole simulation placed, including days past the emitted horizon. */
    val mockTestsPlanned: Int = 0,
    /**
     * The day the last Learn or Practice block lands, when the simulation was run to the end
     * ([PlanInputs.projectedFinish]) and every remaining chapter fits. Null otherwise.
     */
    val projectedFinish: LocalDate? = null,
)

enum class PlanningOrder {
    DEFAULT,
    HIGH_YIELD_FIRST,
    SHORTEST_FIRST,
}

/** Everything the planner reads. One value, so feasibility and the planner cannot be fed different inputs. */
data class PlanInputs(
    val attemptId: String,
    val pack: ExamPack,
    val startDate: LocalDate,
    /** Finish-by date in deadline mode. Steady mode ignores it except as an upper bound. */
    val targetDate: LocalDate,
    val weeklyAvailability: List<WeeklyAvailability>,
    val availabilityOverrides: List<AvailabilityOverride> = emptyList(),
    val includedLeafIds: Set<String> = pack.leafIds().toSet(),
    val coveredLeafIds: Set<String> = emptySet(),
    val focusedLearnMinutesByNode: Map<String, Int> = emptyMap(),
    val preservedBlocks: List<StudyPlanBlock> = emptyList(),
    val weekdayTargetMinutes: Int = Int.MAX_VALUE,
    val weekendTargetMinutes: Int = Int.MAX_VALUE,
    val preferredSessionMinutes: Int = DEFAULT_STUDY_SESSION_MINUTES,
    val breakMinutes: Int = DEFAULT_STUDY_BREAK_MINUTES,
    val planningOrder: PlanningOrder = PlanningOrder.DEFAULT,
    val mode: PlanMode = PlanMode.DEADLINE,
    val examDate: LocalDate? = null,
    val allowLearnInRevisionPhase: Boolean = false,
    /** Personal pace factor (§4.1) applied to every Learn estimate. */
    val calibrationFactor: Double = 1.0,
    /** Whether [calibrationFactor] was measured on enough chapters to call the hours "yours". */
    val calibrationActive: Boolean = false,
    val priorities: Map<String, ChapterPriority> = emptyMap(),
    /**
     * Droppers (§3.3): once every chapter that still needs Learn work is placed, the rest of each
     * day goes to revision passes over the chapters already learned, even before finish-by.
     */
    val revisionShaped: Boolean = false,
    val revisionStates: Collection<RevisionState> = emptyList(),
    /** STRUGGLED outcome counts by node id; weak chapters are revised first within a band. */
    val weakNodeCounts: Map<String, Int> = emptyMap(),
    /** Chapters that already had a completed Practice session. */
    val practicedChapterIds: Set<String> = emptySet(),
    /** Mock tests done before [startDate]; they count towards density. */
    val recentMockDates: Collection<LocalDate> = emptyList(),
    /** Coaching lectures and DPPs behind the batch, oldest first (§5.4). Empty: planner unchanged. */
    val coachingBacklog: List<BacklogItem> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val config: PlannerConfig = PlannerConfig(),
)

/**
 * Builds a deterministic rolling plan from the stable ExamPack tree.
 *
 * A leaf's learn workload is its estimated minutes (times the personal
 * calibration factor) minus actual focused learn time and intent that must be
 * preserved. Covered and excluded leaves never receive another automatic LEARN
 * block. The caller may safely replace future regenerable rows (see
 * [isRegenerableFrom]) with [RollingPlanResult.blocks]; completed, running,
 * moved, and manual rows are supplied as [preservedBlocks] and are never
 * recreated.
 *
 * Only [horizonDays] days are emitted, but in deadline mode the simulation runs
 * on to the finish-by date so [RollingPlanResult.fit] reports the same answer
 * the whole plan would give.
 */
fun generateRollingPlan(
    attemptId: String,
    pack: ExamPack,
    startDate: LocalDate,
    targetDate: LocalDate,
    weeklyAvailability: List<WeeklyAvailability>,
    availabilityOverrides: List<AvailabilityOverride> = emptyList(),
    includedLeafIds: Set<String> = pack.leafIds().toSet(),
    coveredLeafIds: Set<String> = emptySet(),
    focusedLearnMinutesByNode: Map<String, Int> = emptyMap(),
    preservedBlocks: List<StudyPlanBlock> = emptyList(),
    weekdayTargetMinutes: Int = Int.MAX_VALUE,
    weekendTargetMinutes: Int = Int.MAX_VALUE,
    horizonDays: Int = DEFAULT_PLANNING_HORIZON_DAYS,
    preferredSessionMinutes: Int = DEFAULT_STUDY_SESSION_MINUTES,
    breakMinutes: Int = DEFAULT_STUDY_BREAK_MINUTES,
    planningOrder: PlanningOrder = PlanningOrder.DEFAULT,
    nowMs: Long,
    mode: PlanMode = PlanMode.DEADLINE,
    examDate: LocalDate? = null,
    allowLearnInRevisionPhase: Boolean = false,
    calibrationFactor: Double = 1.0,
    priorities: Map<String, ChapterPriority> = emptyMap(),
    revisionShaped: Boolean = false,
    revisionStates: Collection<RevisionState> = emptyList(),
    weakNodeCounts: Map<String, Int> = emptyMap(),
    practicedChapterIds: Set<String> = emptySet(),
    recentMockDates: Collection<LocalDate> = emptyList(),
    zone: ZoneId = ZoneId.systemDefault(),
    config: PlannerConfig = PlannerConfig(),
): RollingPlanResult = PlanInputs(
    attemptId = attemptId,
    pack = pack,
    startDate = startDate,
    targetDate = targetDate,
    weeklyAvailability = weeklyAvailability,
    availabilityOverrides = availabilityOverrides,
    includedLeafIds = includedLeafIds,
    coveredLeafIds = coveredLeafIds,
    focusedLearnMinutesByNode = focusedLearnMinutesByNode,
    preservedBlocks = preservedBlocks,
    weekdayTargetMinutes = weekdayTargetMinutes,
    weekendTargetMinutes = weekendTargetMinutes,
    preferredSessionMinutes = preferredSessionMinutes,
    breakMinutes = breakMinutes,
    planningOrder = planningOrder,
    mode = mode,
    examDate = examDate,
    allowLearnInRevisionPhase = allowLearnInRevisionPhase,
    calibrationFactor = calibrationFactor,
    priorities = priorities,
    revisionShaped = revisionShaped,
    revisionStates = revisionStates,
    weakNodeCounts = weakNodeCounts,
    practicedChapterIds = practicedChapterIds,
    recentMockDates = recentMockDates,
    zone = zone,
    config = config,
).plan(horizonDays, nowMs)

// ---------------------------------------------------------------------------
// Work model
// ---------------------------------------------------------------------------

private class LeafWork(val nodeId: String, val subjectId: String, val estimate: Int, var remaining: Int)

private class ChapterWork(
    val id: String,
    val subjectId: String,
    val leaves: List<LeafWork>,
    var practiceRemaining: Int,
    /** Position in the syllabus; the final tie-break everywhere. */
    val syllabusIndex: Int,
) {
    val learnRemaining: Int get() = leaves.sumOf { it.remaining }
    val done: Boolean get() = learnRemaining == 0 && practiceRemaining == 0
    fun nextLeaf(): LeafWork? = leaves.firstOrNull { it.remaining > 0 }
}

/** Per-chapter work at the start of a plan, before anything is scheduled. */
data class ChapterNeed(
    val chapterId: String,
    val subjectId: String,
    val leafIds: List<String>,
    val learnMinutes: Int,
    val practiceMinutes: Int,
) {
    val totalMinutes: Int get() = learnMinutes + practiceMinutes
}

private class WorkModel(
    val chapters: List<ChapterWork>,
    val nodeChapter: Map<String, String>,
    val nodeSubject: Map<String, String>,
    val nodeLeaves: Map<String, List<String>>,
)

private fun PlanInputs.buildWork(nowMs: Long): WorkModel {
    val clock = clockAt(nowMs)
    val plannedMinutesByNode = preservedBlocks
        .filter { it.status == PlanBlockStatus.PLANNED && !it.endedUnrun(clock) }
        // A titled block on a chapter is coaching backlog, not Learn work on that chapter.
        .filter { it.customTitle == null }
        .mapNotNull { block -> block.nodeId?.let { it to block.plannedMinutes } }
        .groupingBy { it.first }
        .fold(0) { total, entry -> total + entry.second }
    val practiceCovered = practicedChapterIds + preservedBlocks
        .filter {
            it.activityType == StudyActivityType.PRACTICE &&
                it.status != PlanBlockStatus.SKIPPED && it.status != PlanBlockStatus.RESCHEDULED
        }
        .mapNotNull { it.nodeId }

    val nodeChapter = HashMap<String, String>()
    val nodeSubject = HashMap<String, String>()
    val nodeLeaves = HashMap<String, List<String>>()
    val chapters = ArrayList<ChapterWork>()
    var index = 0
    pack.subjects.forEach { subject ->
        subject.nodes.forEach { chapter ->
            val byId = chapter.flatten().associateBy { it.id }
            byId.values.forEach { node ->
                nodeChapter[node.id] = chapter.id
                nodeSubject[node.id] = subject.id
                nodeLeaves[node.id] = node.leafIds()
            }
            val leaves = chapter.leafIds().mapNotNull { leafId ->
                val leaf = byId.getValue(leafId)
                if (leaf.id !in includedLeafIds || leaf.id in coveredLeafIds) return@mapNotNull null
                val estimate = scaledEstimate(leaf.estimatedMinutes ?: return@mapNotNull null)
                val accountedFor = focusedLearnMinutesByNode[leaf.id].orZero() + plannedMinutesByNode[leaf.id].orZero()
                LeafWork(leaf.id, subject.id, estimate, (estimate - accountedFor).coerceAtLeast(0))
            }
            val needsPractice = leaves.isNotEmpty() && chapter.id !in practiceCovered
            val chapterEstimate = chapter.leafIds()
                .filter { it in includedLeafIds }
                .sumOf { id -> scaledEstimate(byId.getValue(id).estimatedMinutes ?: 0) }
            chapters += ChapterWork(
                id = chapter.id,
                subjectId = subject.id,
                leaves = leaves,
                practiceRemaining = if (needsPractice) practiceMinutesFor(chapterEstimate) else 0,
                syllabusIndex = index++,
            )
        }
    }
    return WorkModel(chapters.filter { !it.done }, nodeChapter, nodeSubject, nodeLeaves)
}

private fun PlanInputs.scaledEstimate(minutes: Int): Int = (minutes.coerceAtLeast(0) * calibrationFactor).roundToInt()

private fun PlanInputs.practiceMinutesFor(chapterEstimateMinutes: Int): Int =
    (chapterEstimateMinutes * config.practiceFraction).roundToInt()
        .coerceIn(config.practiceMinMinutes, config.practiceMaxMinutes)

private class Clock(val date: LocalDate, val minute: Int, val roundedMinute: Int)

private fun PlanInputs.clockAt(nowMs: Long): Clock {
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val minute = now.hour * 60 + now.minute
    return Clock(now.toLocalDate(), minute, ceil(minute / 5.0).toInt() * 5)
}

/** A planned block that ended before now without being done. It reserves neither time nor work. */
private fun StudyPlanBlock.endedUnrun(clock: Clock): Boolean =
    scheduledDate < clock.date ||
        (scheduledDate == clock.date && startMinuteOfDay + plannedMinutes <= clock.minute)

/** What each chapter still needs at the start of the plan: used to pick chapters to park. */
fun PlanInputs.chapterNeeds(nowMs: Long): List<ChapterNeed> = buildWork(nowMs).chapters.map { chapter ->
    ChapterNeed(
        chapterId = chapter.id,
        subjectId = chapter.subjectId,
        leafIds = chapter.leaves.map { it.nodeId },
        learnMinutes = chapter.learnRemaining,
        practiceMinutes = chapter.practiceRemaining,
    )
}

// ---------------------------------------------------------------------------
// The simulation
// ---------------------------------------------------------------------------

private val CLOSED_PLAN_STATUSES = setOf(
    PlanBlockStatus.SKIPPED,
    PlanBlockStatus.RESCHEDULED,
)

private val NON_STUDY_TYPES = setOf(StudyActivityType.CLASS, StudyActivityType.BREAK)

private class DayPlacer(windows: List<Pair<Int, Int>>, private val breakMinutes: Int) {
    private val free = windows.filter { it.second > it.first }.sortedBy { it.first }
        .map { intArrayOf(it.first, it.second) }.toMutableList()

    /** Puts a block of up to [want] minutes (at least [minAcceptable]) in the earliest window it fits. */
    fun place(want: Int, minAcceptable: Int): Pair<Int, Int>? {
        for (i in free.indices) {
            val start = free[i][0]
            val end = free[i][1]
            if (end - start < minAcceptable) continue
            val duration = minOf(want, end - start)
            val next = minOf(end, start + duration + breakMinutes.coerceAtLeast(0))
            if (next >= end) free.removeAt(i) else free[i][0] = next
            return start to duration
        }
        return null
    }

    fun hasRoomFor(minutes: Int): Boolean = free.any { it[1] - it[0] >= minutes }
}

private fun StudyPlanBlock.countsAsStudy(): Boolean =
    activityType !in NON_STUDY_TYPES

/**
 * When the planner would finish the remaining Learn and Practice work at the student's own
 * hours, or null if it does not finish before the exam. Steady mode's "projected finish"
 * (PRODUCTION_V1_PLAN §3.1): it simulates every day to the end but emits nothing.
 */
fun PlanInputs.projectedFinish(nowMs: Long): LocalDate? =
    plan(horizonDays = 1, nowMs = nowMs, simulateToEnd = true).projectedFinish

/**
 * Plans [horizonDays] days from [PlanInputs.startDate]; deadline mode simulates on to finish-by for [RollingPlanResult.fit].
 * With [simulateToEnd] steady mode also runs to the exam (or [PROJECTION_DAYS] without one), only to find the finish date.
 */
fun PlanInputs.plan(horizonDays: Int, nowMs: Long, simulateToEnd: Boolean = false): RollingPlanResult {
    val deadline = mode == PlanMode.DEADLINE
    val lastExamDay = examDate?.minusDays(1)
    // Last date anything may be planned on. The exam day itself is not planned.
    val hardEnd: LocalDate = when {
        deadline -> if (lastExamDay != null && lastExamDay > targetDate) lastExamDay else targetDate
        simulateToEnd -> lastExamDay ?: startDate.plusDays(PROJECTION_DAYS)
        else -> lastExamDay ?: startDate.plusDays(horizonDays.coerceAtLeast(1).toLong() - 1)
    }
    if (horizonDays <= 0 || startDate > hardEnd) return RollingPlanResult(emptyList(), emptyList(), 0)

    val emitEnd = minOf(hardEnd, startDate.plusDays((horizonDays - 1).toLong()))
    val learnEnd: LocalDate? = if (deadline) targetDate else null
    val simEnd = when {
        learnEnd != null -> minOf(hardEnd, maxOf(emitEnd, learnEnd))
        simulateToEnd -> hardEnd
        else -> emitEnd
    }

    val clock = clockAt(nowMs)
    val nowDate = clock.date
    val nowMinute = clock.roundedMinute

    val model = buildWork(nowMs)
    val queues: List<MutableList<ChapterWork>> = orderedQueues(model)
    val learnNeeded = model.chapters.sumOf { it.learnRemaining }
    val practiceNeeded = model.chapters.sumOf { it.practiceRemaining }
    val passChapters = revisionPassOrder(model)

    // Revision engine: due states for target nodes, most overdue first.
    val pendingRevisions = revisionStates
        .filter { it.enabled && it.nextDueDate != null && isTargetNode(it.nodeId, model) }
        .sortedWith(compareBy({ it.nextDueDate }, { it.nodeId }))
        .toMutableList()
    val manualRevisionNodes = preservedBlocks
        .filter { it.activityType == StudyActivityType.REVISION && it.status == PlanBlockStatus.PLANNED }
        .mapNotNull { it.nodeId }.toSet()
    pendingRevisions.removeAll { it.nodeId in manualRevisionNodes }

    val mockDates = (recentMockDates + preservedBlocks
        .filter { it.activityType == StudyActivityType.MOCK_TEST && it.status !in CLOSED_PLAN_STATUSES }
        .map { it.scheduledDate }).toMutableList()

    // Coaching backlog: a lecture or DPP already on the plan (kept, moved or done) is not placed again.
    val chapterTitles = pack.chapters().associate { it.id to it.title }
    fun backlogTitle(item: BacklogItem): String = String.format(
        if (item.kind == BacklogKind.LECTURE) config.backlogLectureTitle else config.backlogDppTitle,
        chapterTitles[item.chapterId].orEmpty(),
        item.number,
    )
    val keptTitles = preservedBlocks.filter { it.status !in CLOSED_PLAN_STATUSES }.mapNotNullTo(HashSet()) { it.customTitle }
    val backlogQueue = ArrayDeque(coachingBacklog.filter { backlogTitle(it) !in keptTitles })

    val blocks = mutableListOf<StudyPlanBlock>()
    var subjectCursor = 0
    var passCursor = 0
    var mockCount = 0
    var snapshotAtLearnEnd: Pair<Int, Int>? = null
    var snapshotAtEmitEnd: List<RemainingWork>? = null
    var lastWorkDate: LocalDate? = null

    fun learnRemainingNow() = model.chapters.sumOf { it.learnRemaining }
    fun practiceRemainingNow() = model.chapters.sumOf { it.practiceRemaining }
    fun workSnapshot(): List<RemainingWork> = model.chapters.flatMap { chapter ->
        chapter.leaves.filter { it.remaining > 0 }.map { RemainingWork(it.nodeId, it.subjectId, it.estimate, it.remaining) }
    }

    fun pickChapter(): ChapterWork? {
        if (queues.isEmpty()) return null
        for (offset in queues.indices) {
            val queue = queues[(subjectCursor + offset) % queues.size]
            while (queue.isNotEmpty() && queue.first().done) queue.removeAt(0)
            if (queue.isNotEmpty()) {
                subjectCursor = (subjectCursor + offset + 1) % queues.size
                return queue.first()
            }
        }
        return null
    }

    var date = startDate
    while (date <= simEnd) {
        val today = date
        if (learnEnd != null && snapshotAtLearnEnd == null && today > learnEnd) {
            snapshotAtLearnEnd = learnRemainingNow() to practiceRemainingNow()
        }
        if (snapshotAtEmitEnd == null && today > emitEnd) snapshotAtEmitEnd = workSnapshot()
        date = date.plusDays(1)
        if (today < nowDate) continue

        val emit = today <= emitEnd
        val inRevisionPhase = deadline && today > targetDate
        val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val dailyTarget = (if (weekend) weekendTargetMinutes else weekdayTargetMinutes).coerceAtLeast(0)

        val dayPreserved = preservedBlocks.filter { it.scheduledDate == today }
        val preservedStudy = dayPreserved
            .filter {
                (it.status == PlanBlockStatus.COMPLETED || (it.status == PlanBlockStatus.PLANNED && !it.endedUnrun(clock))) &&
                    it.countsAsStudy()
            }
            .sumOf { it.plannedMinutes }
        var budget = (dailyTarget - preservedStudy).coerceAtLeast(0)
        val preservedRevision = dayPreserved
            .filter {
                it.activityType == StudyActivityType.REVISION &&
                    (it.status == PlanBlockStatus.PLANNED || it.status == PlanBlockStatus.COMPLETED)
            }
            .sumOf { it.plannedMinutes }

        val occupied = dayPreserved
            .filter { it.status !in CLOSED_PLAN_STATUSES }
            .map { it.startMinuteOfDay until (it.startMinuteOfDay + it.plannedMinutes) }
        var windows = subtractOccupied(effectiveWindowsFor(today, weeklyAvailability, availabilityOverrides), occupied)
        if (today == nowDate) {
            // Never place anything in the past: start at now, rounded up to 5 minutes.
            windows = windows.mapNotNull { (start, end) -> maxOf(start, nowMinute).let { s -> if (end > s) s to end else null } }
        }
        val widestWindow = windows.maxOfOrNull { it.second - it.first } ?: 0
        val placer = DayPlacer(windows, breakMinutes)
        var blockIndex = 0

        fun add(
            type: StudyActivityType,
            nodeId: String?,
            subjectId: String?,
            start: Int,
            minutes: Int,
            source: PlanBlockSource,
            title: String? = null,
        ) {
            if (!emit) return
            val prefix = if (source == PlanBlockSource.REVISION_ENGINE) ENGINE_REVISION_ID_PREFIX else "auto_"
            blocks += StudyPlanBlock(
                id = deterministicBlockId(prefix, attemptId, today, start, blockIndex, nodeId, type),
                attemptId = attemptId,
                nodeId = nodeId,
                subjectId = subjectId,
                customTitle = title,
                activityType = type,
                scheduledDate = today,
                startMinuteOfDay = start,
                plannedMinutes = minutes,
                status = PlanBlockStatus.PLANNED,
                source = source,
                rescheduledFromId = null,
                replacedById = null,
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
            )
        }

        // 1. A mock test, if the density schedule calls for one and a 200-minute window is free.
        if (mockDue(today, inRevisionPhase, mockDates) && placer.hasRoomFor(config.mockMinutes)) {
            placer.place(config.mockMinutes, config.mockMinutes)?.let { (start, minutes) ->
                add(StudyActivityType.MOCK_TEST, null, null, start, minutes, PlanBlockSource.AUTO)
                blockIndex++
                budget = (budget - minutes).coerceAtLeast(0)
                mockDates += today
                mockCount++
            }
        }

        // 2. Due revisions from the revision engine, most overdue first, within the day's cap.
        val cap = if (inRevisionPhase) budget else {
            (dailyTarget.toLong() * config.revisionDayCapFraction).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() - preservedRevision
        }
        var revisionBudget = minOf(cap.coerceAtLeast(0), budget)
        while (revisionBudget >= config.revisionMinBlockMinutes && pendingRevisions.isNotEmpty()) {
            val due = pendingRevisions.first()
            if (due.nextDueDate!! > today) break
            val want = minOf(config.revisionBlockMinutes, revisionBudget)
            val placed = placer.place(want, config.revisionMinBlockMinutes) ?: break
            pendingRevisions.removeAt(0)
            add(
                StudyActivityType.REVISION, due.nodeId, model.nodeSubject[due.nodeId],
                placed.first, placed.second, PlanBlockSource.REVISION_ENGINE,
            )
            blockIndex++
            budget -= placed.second
            revisionBudget -= placed.second
        }

        // 2b. Coaching backlog: its share of the day goes to whole lecture and DPP blocks, before
        // Learn fills the live share. A day that can hold one backlog item gets at least one, so a
        // small share still moves the backlog.
        if (!inRevisionPhase && backlogQueue.isNotEmpty()) {
            var backlogBudget = minOf(budget, backlogDailyMinutes(dailyTarget, config.liveBacklogSplit))
            var placedToday = 0
            while (backlogQueue.isNotEmpty() && budget > 0) {
                val item = backlogQueue.first()
                val want = if (item.kind == BacklogKind.LECTURE) config.backlogLectureMinutes else config.backlogDppMinutes
                if (want <= 0) {
                    backlogQueue.removeFirst()
                    continue
                }
                val withinShare = want <= backlogBudget
                if (!withinShare && (placedToday > 0 || backlogBudget <= 0 || want > budget)) break
                val placed = placer.place(want, want) ?: break
                backlogQueue.removeFirst()
                add(
                    if (item.kind == BacklogKind.LECTURE) StudyActivityType.LEARN else StudyActivityType.PRACTICE,
                    item.chapterId, item.subjectId, placed.first, placed.second, PlanBlockSource.AUTO,
                    title = backlogTitle(item),
                )
                blockIndex++
                placedToday++
                budget -= placed.second
                backlogBudget -= placed.second
            }
        }

        // 3. The phase's own work: Learn then Practice, or revision passes in the revision phase.
        val learnAllowed = !inRevisionPhase || allowLearnInRevisionPhase
        if (learnAllowed) {
            // A subject whose next block does not fit is skipped for now; the day ends once every subject has failed in a row.
            var failures = 0
            while (budget > 0 && failures < queues.size) {
                val chapter = pickChapter() ?: break
                val leaf = chapter.nextLeaf()
                val type: StudyActivityType
                val want: Int
                val minAcceptable: Int
                if (leaf != null) {
                    type = StudyActivityType.LEARN
                    want = minOf(preferredSessionMinutes.coerceAtLeast(1), leaf.remaining, budget)
                    minAcceptable = minOf(want, config.minBlockMinutes)
                } else {
                    type = StudyActivityType.PRACTICE
                    val full = minOf(chapter.practiceRemaining, config.practiceMaxMinutes)
                    want = minOf(full, budget)
                    // Practice is one block per chapter, not scraps. Only when no window today could
                    // ever hold it whole is it allowed to be cut down to the practice minimum.
                    minAcceptable = if (widestWindow >= full) full else minOf(full, config.practiceMinMinutes)
                }
                val placed = if (want >= minAcceptable) placer.place(want, minAcceptable) else null
                if (placed == null) {
                    failures++
                    continue
                }
                failures = 0
                add(type, leaf?.nodeId ?: chapter.id, chapter.subjectId, placed.first, placed.second, PlanBlockSource.AUTO)
                blockIndex++
                if (leaf != null) leaf.remaining -= placed.second else chapter.practiceRemaining -= placed.second
                lastWorkDate = today
                budget -= placed.second
            }
        }
        // Revision passes: the whole revision phase, and for a dropper whatever is left of the day once Learn is placed.
        val passPool = when {
            inRevisionPhase -> passChapters
            revisionShaped -> passChapters.filter { ref -> model.chapters.none { it.id == ref.id && it.learnRemaining > 0 } }
            else -> emptyList()
        }
        if (passPool.isNotEmpty()) {
            while (budget > 0) {
                val chapter = passPool[passCursor % passPool.size]
                val want = minOf(config.revisionPassMinutes, budget)
                val placed = placer.place(want, minOf(want, config.minBlockMinutes)) ?: break
                add(StudyActivityType.REVISION, chapter.id, chapter.subjectId, placed.first, placed.second, PlanBlockSource.AUTO)
                blockIndex++
                passCursor++
                budget -= placed.second
            }
        }
    }
    if (snapshotAtLearnEnd == null) snapshotAtLearnEnd = learnRemainingNow() to practiceRemainingNow()
    val remainingAtEmit = snapshotAtEmitEnd ?: workSnapshot()

    val fit = if (deadline) {
        val (learnLeft, practiceLeft) = snapshotAtLearnEnd!!
        PlanFit(
            finishBy = targetDate,
            learnMinutes = learnNeeded,
            practiceMinutes = practiceNeeded,
            shortByMinutes = learnLeft + practiceLeft,
            revisionWindowDays = examDate?.let { ChronoUnit.DAYS.between(targetDate, it).toInt().coerceAtLeast(0) } ?: 0,
            calibrated = calibrationActive,
        )
    } else {
        null
    }
    return RollingPlanResult(
        blocks = blocks,
        remainingWork = remainingAtEmit,
        unscheduledMinutes = remainingAtEmit.sumOf { it.remainingMinutes },
        fit = fit,
        mockTestsPlanned = mockCount,
        projectedFinish = if (simulateToEnd && learnRemainingNow() + practiceRemainingNow() == 0) lastWorkDate ?: startDate else null,
    )
}

private fun PlanInputs.isTargetNode(nodeId: String, model: WorkModel): Boolean =
    nodeId in includedLeafIds || model.nodeLeaves[nodeId]?.any { it in includedLeafIds } == true

/** Chapter queues, one per subject, in the planning order. Subjects rotate block by block. */
private fun PlanInputs.orderedQueues(model: WorkModel): List<MutableList<ChapterWork>> =
    model.chapters.groupBy { it.subjectId }.values.map { chapters ->
        val ordered = when (planningOrder) {
            PlanningOrder.DEFAULT -> chapters.sortedBy { it.syllabusIndex }
            PlanningOrder.SHORTEST_FIRST ->
                chapters.sortedWith(compareBy({ it.learnRemaining + it.practiceRemaining }, { it.syllabusIndex }))
            PlanningOrder.HIGH_YIELD_FIRST -> chapters.sortedWith(
                compareBy<ChapterWork>({ bandOf(it.id).yieldRank() }, { -(priorities[it.id]?.meanQuestionsPerYear ?: 0.0) }, { it.syllabusIndex }),
            )
        }
        ordered.toMutableList()
    }

private fun PlanInputs.bandOf(chapterId: String): PriorityBand = priorities[chapterId]?.band ?: PriorityBand.NO_DATA

/** Revision-phase pass order: high-yield first, then weak chapters, then the rest in syllabus order. */
private fun PlanInputs.revisionPassOrder(model: WorkModel): List<ChapterNeedRef> {
    val weakByChapter = HashMap<String, Int>()
    weakNodeCounts.forEach { (nodeId, count) ->
        model.nodeChapter[nodeId]?.let { weakByChapter.merge(it, count, Int::plus) }
    }
    var index = 0
    return pack.subjects.flatMap { subject ->
        subject.nodes.mapNotNull { chapter ->
            val order = index++
            if (chapter.leafIds().none { it in includedLeafIds }) null else ChapterNeedRef(chapter.id, subject.id, order)
        }
    }.sortedWith(
        compareBy<ChapterNeedRef>(
            { bandOf(it.id).yieldRank() },
            { -(weakByChapter[it.id] ?: 0) },
            { -(priorities[it.id]?.meanQuestionsPerYear ?: 0.0) },
            { it.order },
        ),
    )
}

private data class ChapterNeedRef(val id: String, val subjectId: String, val order: Int)

/**
 * Mock density (§4.3): more than 8 weeks to the exam one per 14 days, 4 to 8 weeks
 * one per 7 days, the last 4 weeks three per 7 days (never on back-to-back days).
 * Deadline mode places them in the revision phase only; steady mode only once the
 * exam is within 8 weeks.
 */
private fun PlanInputs.mockDue(date: LocalDate, inRevisionPhase: Boolean, mockDates: List<LocalDate>): Boolean {
    val exam = examDate ?: return false
    val daysToExam = ChronoUnit.DAYS.between(date, exam)
    if (daysToExam <= 0) return false
    val eligible = when (mode) {
        PlanMode.DEADLINE -> inRevisionPhase
        PlanMode.STEADY -> daysToExam <= MOCK_FAR_DAYS
    }
    if (!eligible || mockDates.any { it == date }) return false
    val (windowDays, maxInWindow) = when {
        daysToExam > MOCK_FAR_DAYS -> 14 to 1
        daysToExam >= MOCK_NEAR_DAYS -> 7 to 1
        else -> 7 to 3
    }
    val windowStart = date.minusDays((windowDays - 1).toLong())
    if (mockDates.count { it >= windowStart && it < date } >= maxInWindow) return false
    if (maxInWindow > 1 && mockDates.any { it >= date.minusDays(1) && it < date }) return false
    return true
}

internal fun subtractOccupied(
    windows: List<Pair<Int, Int>>,
    occupied: List<IntRange>,
): List<Pair<Int, Int>> {
    val mergedOccupied = occupied
        .map { it.first to (it.last + 1) }
        .filter { it.second > it.first }
        .sortedBy { it.first }
        .fold(mutableListOf<Pair<Int, Int>>()) { merged, interval ->
            val last = merged.lastOrNull()
            if (last == null || interval.first > last.second) merged += interval
            else merged[merged.lastIndex] = last.first to maxOf(last.second, interval.second)
            merged
        }

    return windows.sortedBy { it.first }.flatMap { (start, end) ->
        if (end <= start) return@flatMap emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var cursor = start
        mergedOccupied.forEach { (busyStart, busyEnd) ->
            if (busyEnd <= cursor || busyStart >= end) return@forEach
            if (busyStart > cursor) result += cursor to minOf(busyStart, end)
            cursor = maxOf(cursor, busyEnd)
        }
        if (cursor < end) result += cursor to end
        result
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun deterministicBlockId(
    prefix: String,
    attemptId: String,
    date: LocalDate,
    startMinute: Int,
    blockIndex: Int,
    nodeId: String?,
    type: StudyActivityType,
): String {
    val key = when {
        nodeId == null -> type.name
        type == StudyActivityType.LEARN -> nodeId
        else -> "$nodeId:${type.name}"
    }
    return "${prefix}${attemptId}_${date}_${startMinute}_${blockIndex}_${key.hashCode().toUInt().toString(16)}"
}

// ---------------------------------------------------------------------------
// Which stored blocks a replan may replace
// ---------------------------------------------------------------------------

/**
 * True for a future, still-planned block the planner created itself, so a replan
 * may delete and recreate it. A block the student moved ([StudyPlanBlock.rescheduledFromId]),
 * added by hand, started, completed, skipped or missed is never regenerable.
 * A revision block counts only if the engine placed it ([ENGINE_REVISION_ID_PREFIX]), not
 * one the student added from the revision list.
 */
fun StudyPlanBlock.isRegenerable(): Boolean =
    status == PlanBlockStatus.PLANNED &&
        rescheduledFromId == null &&
        (source == PlanBlockSource.AUTO || (source == PlanBlockSource.REVISION_ENGINE && id.startsWith(ENGINE_REVISION_ID_PREFIX)))

/** Regenerable and not before [fromDate] / [fromMinute] today: the rows a replan from that moment replaces. */
fun StudyPlanBlock.isReplacedByReplanFrom(fromDate: LocalDate, fromMinute: Int): Boolean =
    isRegenerable() && (scheduledDate > fromDate || (scheduledDate == fromDate && startMinuteOfDay >= fromMinute))
