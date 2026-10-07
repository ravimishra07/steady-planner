package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * Canonical metrics (PRODUCTION_V1_PLAN §9).
 *
 * Every screen that shows one of these concepts calls the function here. No
 * screen computes its own. All functions are pure: callers pass `today` and
 * `now` in, nothing reads a clock.
 *
 * The coverage unit is whatever ids the caller passes as the target and
 * covered sets. Today those are syllabus leaves (the NCERT sections of the
 * bundled pack). When per-chapter "Learned" arrives (Phase C) the callers
 * pass chapter ids instead and nothing in here changes.
 */

// ---------------------------------------------------------------------------
// Coverage
// ---------------------------------------------------------------------------

data class Coverage(val covered: Int, val total: Int) {
    /** Rounded down: 99.9% is 99%, never 100%. */
    val percent: Int get() = if (total <= 0) 0 else covered * 100 / total
    val remaining: Int get() = (total - covered).coerceAtLeast(0)
}

/** Ids marked covered, from the one source of truth for "what have I covered". */
fun coveredNodeIds(progress: Collection<TopicProgress>): Set<String> =
    progress.asSequence().filter { it.status == TopicProgressStatus.COVERED }.mapTo(mutableSetOf()) { it.nodeId }

/**
 * Covered ÷ target. A covered id outside [targetIds] (an excluded chapter the
 * student had already finished) does not count: one denominator, one numerator
 * drawn from it.
 */
fun coverage(targetIds: Set<String>, coveredIds: Set<String>): Coverage =
    Coverage(covered = targetIds.count { it in coveredIds }, total = targetIds.size)

/** Per-subject coverage over the same target set, keyed by subject id. */
fun ExamPack.subjectCoverage(targetLeafIds: Set<String>, coveredIds: Set<String>): Map<String, Coverage> =
    subjects.associate { subject ->
        subject.id to coverage(subject.leafIds().filterTo(mutableSetOf()) { it in targetLeafIds }, coveredIds)
    }

// ---------------------------------------------------------------------------
// Remaining work
// ---------------------------------------------------------------------------

/** Estimated minutes of every leaf; a leaf with no estimate is 0. */
fun ExamPack.leafMinutes(): Map<String, Int> {
    val result = LinkedHashMap<String, Int>()
    subjects.forEach { subject ->
        subject.nodes.flatMap(SyllabusNode::flatten).filter { it.children.isEmpty() }.forEach { leaf ->
            result[leaf.id] = (leaf.estimatedMinutes ?: 0).coerceAtLeast(0)
        }
    }
    return result
}

/**
 * Σ estimated minutes of uncovered target leaves × [calibrationFactor]. The
 * factor stays 1.0 until personal calibration lands (Phase B); an estimate is
 * an estimate until then.
 */
fun remainingMinutes(
    pack: ExamPack,
    targetLeafIds: Set<String>,
    coveredIds: Set<String>,
    calibrationFactor: Double = 1.0,
): Int = scaled(
    pack.leafMinutes().entries.sumOf { (id, minutes) -> if (id in targetLeafIds && id !in coveredIds) minutes else 0 },
    calibrationFactor,
)

/** Σ estimated minutes of every target leaf, covered or not. */
fun targetMinutes(pack: ExamPack, targetLeafIds: Set<String>, calibrationFactor: Double = 1.0): Int = scaled(
    pack.leafMinutes().entries.sumOf { (id, minutes) -> if (id in targetLeafIds) minutes else 0 },
    calibrationFactor,
)

/** Remaining minutes per subject id over the same target/covered sets as [remainingMinutes]. */
fun remainingMinutesBySubject(
    pack: ExamPack,
    targetLeafIds: Set<String>,
    coveredIds: Set<String>,
    calibrationFactor: Double = 1.0,
): Map<String, Int> {
    val minutes = pack.leafMinutes()
    return pack.subjects.associate { subject ->
        subject.id to scaled(
            subject.leafIds().sumOf { id -> if (id in targetLeafIds && id !in coveredIds) minutes[id] ?: 0 else 0 },
            calibrationFactor,
        )
    }
}

/** Format minutes as whole hours only after summing; never truncate per chapter. */
fun minutesToHours(minutes: Int): Int = (minutes / 60.0).roundToInt()

private fun scaled(minutes: Int, factor: Double): Int = (minutes * factor).roundToInt()

/** Explicit include/exclude overrides as the map [effectiveTargetLeafIds] expects. */
fun targetStates(overrides: Collection<TargetNodeOverride>): Map<String, TargetNodeState> =
    overrides.associate { it.nodeId to it.state }

/**
 * Everything the syllabus-shaped screens show, computed once from one input.
 * Syllabus rings, Insights, the Settings ring, Home and Organise all read this,
 * so they cannot disagree on the target set, the covered set or the remaining work.
 */
data class SyllabusMetrics(
    val targetLeafIds: Set<String>,
    val coveredIds: Set<String>,
    val overall: Coverage,
    val bySubject: Map<String, Coverage>,
    val targetMinutes: Int,
    val remainingMinutes: Int,
    val remainingMinutesBySubject: Map<String, Int>,
)

fun syllabusMetrics(
    pack: ExamPack,
    overrides: Map<String, TargetNodeState>,
    progress: Collection<TopicProgress>,
    calibrationFactor: Double = 1.0,
): SyllabusMetrics {
    val target = pack.effectiveTargetLeafIds(overrides)
    val covered = coveredNodeIds(progress)
    return SyllabusMetrics(
        targetLeafIds = target,
        coveredIds = covered,
        overall = coverage(target, covered),
        bySubject = pack.subjectCoverage(target, covered),
        targetMinutes = targetMinutes(pack, target, calibrationFactor),
        remainingMinutes = remainingMinutes(pack, target, covered, calibrationFactor),
        remainingMinutesBySubject = remainingMinutesBySubject(pack, target, covered, calibrationFactor),
    )
}

// ---------------------------------------------------------------------------
// Studied time
// ---------------------------------------------------------------------------

/** A session whose focused time counts: completed, including a partial "Save & end". */
fun StudySession.countsAsStudy(): Boolean = status == StudySessionStatus.COMPLETED && focusedSeconds > 0

/** Focused seconds of counting sessions in [from]..[to] (open-ended when null). Never planned length. */
fun studiedSeconds(sessions: Iterable<StudySession>, from: LocalDate? = null, to: LocalDate? = null): Int =
    sessions.asSequence()
        .filter { it.countsAsStudy() }
        .filter { (from == null || it.studyDate >= from) && (to == null || it.studyDate <= to) }
        .sumOf { it.focusedSeconds }

fun studiedMinutes(sessions: Iterable<StudySession>, from: LocalDate? = null, to: LocalDate? = null): Int =
    studiedSeconds(sessions, from, to) / 60

/** Whole studied minutes per calendar day. */
fun studiedMinutesByDate(sessions: Iterable<StudySession>): Map<LocalDate, Int> =
    sessions.asSequence().filter { it.countsAsStudy() }
        .groupBy { it.studyDate }
        .mapValues { (_, day) -> day.sumOf { it.focusedSeconds } / 60 }

// ---------------------------------------------------------------------------
// Plan adherence
// ---------------------------------------------------------------------------

data class PlanAdherence(val plannedMinutes: Int, val completedMinutes: Int) {
    val percent: Int? get() = plannedMinutes.takeIf { it > 0 }
        ?.let { ((completedMinutes.toDouble() / it) * 100).roundToInt().coerceIn(0, 100) }
}

/** Has this block's end time passed? Blocks still ahead of the clock are not "behind". */
fun StudyPlanBlock.hasEnded(today: LocalDate, nowMinuteOfDay: Int): Boolean =
    scheduledDate < today || (scheduledDate == today && startMinuteOfDay + plannedMinutes <= nowMinuteOfDay)

/**
 * Completed planned minutes ÷ planned minutes, over blocks in [from]..[to]
 * whose end time has already passed. Rescheduled blocks are superseded by their
 * replacement and skipped blocks were dropped by the student; neither counts.
 * A block is credited with the focused time of its linked sessions (capped at
 * its planned length); a block marked COMPLETED with no session on record is
 * credited in full.
 */
fun planAdherence(
    blocks: List<StudyPlanBlock>,
    sessions: List<StudySession>,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    nowMinuteOfDay: Int,
): PlanAdherence {
    val focusedByBlock = sessions.asSequence()
        .filter { it.countsAsStudy() && it.planBlockId != null }
        .groupBy { it.planBlockId }
        .mapValues { (_, linked) -> linked.sumOf { it.focusedSeconds } / 60 }
    val due = blocks.filter {
        it.scheduledDate in from..to &&
            it.status != PlanBlockStatus.RESCHEDULED &&
            it.status != PlanBlockStatus.SKIPPED &&
            it.activityType != StudyActivityType.BREAK &&
            it.activityType != StudyActivityType.CLASS &&
            it.hasEnded(today, nowMinuteOfDay)
    }
    val completed = due.sumOf { block ->
        val focused = focusedByBlock[block.id]
        when {
            focused != null -> minOf(focused, block.plannedMinutes)
            block.status == PlanBlockStatus.COMPLETED -> block.plannedMinutes
            else -> 0
        }
    }
    return PlanAdherence(plannedMinutes = due.sumOf { it.plannedMinutes }, completedMinutes = completed)
}

// ---------------------------------------------------------------------------
// Pace, forecast, required pace
// ---------------------------------------------------------------------------

const val PACE_WINDOW_DAYS = 14
const val PACE_MIN_ACTIVE_DAYS = 7
const val FORECAST_FAST_FACTOR = 1.15
const val FORECAST_SLOW_FACTOR = 0.85

data class Pace(val minutesPerDay: Double, val activeDays: Int, val windowDays: Int = PACE_WINDOW_DAYS)

/**
 * Studied minutes in the last [PACE_WINDOW_DAYS] days ÷ [PACE_WINDOW_DAYS], or
 * null with fewer than [PACE_MIN_ACTIVE_DAYS] active days. With [learnOnly] the
 * minutes are Learn sessions only (the finish forecast); the active-day gate
 * still counts any study, so the habit evidence is the same either way.
 */
fun pace(sessions: List<StudySession>, today: LocalDate, learnOnly: Boolean = false): Pace? {
    val start = today.minusDays((PACE_WINDOW_DAYS - 1).toLong())
    val window = sessions.filter { it.countsAsStudy() && it.studyDate in start..today }
    val activeDays = window.map { it.studyDate }.distinct().size
    if (activeDays < PACE_MIN_ACTIVE_DAYS) return null
    val counted = if (learnOnly) window.filter { it.activityType == StudyActivityType.LEARN } else window
    return Pace(counted.sumOf { it.focusedSeconds } / 60.0 / PACE_WINDOW_DAYS, activeDays)
}

data class FinishForecast(val earliest: LocalDate, val expected: LocalDate, val latest: LocalDate)

/** `today + remaining ÷ pace`, with the range at ×1.15 (earliest) and ×0.85 (latest) of pace. Null without a usable pace. */
fun finishForecast(remainingMinutes: Int, learnPace: Pace?, today: LocalDate): FinishForecast? {
    val perDay = learnPace?.minutesPerDay?.takeIf { it > 0.0 } ?: return null
    fun finishAt(rate: Double): LocalDate =
        today.plusDays(if (remainingMinutes <= 0) 0 else ceil(remainingMinutes / rate).toLong())
    return FinishForecast(
        earliest = finishAt(perDay * FORECAST_FAST_FACTOR),
        expected = finishAt(perDay),
        latest = finishAt(perDay * FORECAST_SLOW_FACTOR),
    )
}

data class AvailableStudyTime(val minutes: Int, val days: Int)

/**
 * Minutes the student can actually study in [from]..[to] inclusive: per date,
 * the free-time windows in effect (weekly pattern or that date's override),
 * capped by the daily target for weekdays/weekends. Not calendar days.
 */
fun availableStudyTime(
    from: LocalDate,
    to: LocalDate,
    weekly: List<WeeklyAvailability>,
    overrides: List<AvailabilityOverride>,
    weekdayTargetMinutes: Int,
    weekendTargetMinutes: Int,
): AvailableStudyTime {
    if (to < from) return AvailableStudyTime(0, 0)
    var minutes = 0
    var days = 0
    var date = from
    while (date <= to) {
        val windowMinutes = effectiveWindowsFor(date, weekly, overrides).sumOf { (start, end) -> (end - start).coerceAtLeast(0) }
        val target = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) weekendTargetMinutes else weekdayTargetMinutes
        val usable = minOf(windowMinutes, target.coerceAtLeast(0))
        if (usable > 0) {
            minutes += usable
            days++
        }
        date = date.plusDays(1)
    }
    return AvailableStudyTime(minutes, days)
}

data class RequiredPace(val remainingMinutes: Int, val available: AvailableStudyTime) {
    /** Minutes needed on each day the student has study time; null if there is work left but no such day. */
    val minutesPerStudyDay: Int? get() = when {
        remainingMinutes <= 0 -> 0
        available.days <= 0 -> null
        else -> ceil(remainingMinutes.toDouble() / available.days).toInt()
    }

    /** Remaining ÷ available. Above 1.0 the plan cannot fit. */
    val load: Double? get() = if (available.minutes <= 0) null else remainingMinutes.toDouble() / available.minutes
    val fits: Boolean get() = remainingMinutes <= available.minutes
}

fun requiredPace(remainingMinutes: Int, available: AvailableStudyTime): RequiredPace =
    RequiredPace(remainingMinutes.coerceAtLeast(0), available)

// ---------------------------------------------------------------------------
// Subject status
// ---------------------------------------------------------------------------

const val SUBJECT_STATUS_MARGIN_PERCENT = 5
const val SUBJECT_STATUS_HIDDEN_DAYS = 7

enum class SubjectStatus { AHEAD, ON_TRACK, BEHIND }

/**
 * Subject coverage vs a straight-line expectation from [startedOn] to
 * [targetDate], ±[SUBJECT_STATUS_MARGIN_PERCENT]. Null (hidden) for the first
 * [SUBJECT_STATUS_HIDDEN_DAYS] days. The schedule-based expectation replaces
 * the straight line once the planner owns a full schedule (Phase B).
 */
fun subjectStatus(percent: Int, startedOn: LocalDate, targetDate: LocalDate, today: LocalDate): SubjectStatus? {
    val elapsed = ChronoUnit.DAYS.between(startedOn, today).toInt()
    if (elapsed < SUBJECT_STATUS_HIDDEN_DAYS) return null
    val totalDays = ChronoUnit.DAYS.between(startedOn, targetDate).toInt().coerceAtLeast(1)
    val expected = elapsed.coerceIn(0, totalDays) * 100 / totalDays
    return when {
        percent >= expected + SUBJECT_STATUS_MARGIN_PERCENT -> SubjectStatus.AHEAD
        percent <= expected - SUBJECT_STATUS_MARGIN_PERCENT -> SubjectStatus.BEHIND
        else -> SubjectStatus.ON_TRACK
    }
}

// ---------------------------------------------------------------------------
// Streak and dates
// ---------------------------------------------------------------------------

/** Dates with at least one counting session. */
fun studyDates(sessions: Iterable<StudySession>): Set<LocalDate> =
    sessions.asSequence().filter { it.countsAsStudy() }.mapTo(mutableSetOf()) { it.studyDate }

/** Consecutive study days ending today, or ending yesterday if today has none yet. */
fun currentStreak(studyDates: Set<LocalDate>, today: LocalDate): Int {
    var cursor = if (today in studyDates) today else today.minusDays(1)
    var count = 0
    while (cursor in studyDates) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

fun longestStreak(studyDates: Set<LocalDate>): Int {
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    studyDates.sorted().forEach { date ->
        current = if (previous != null && previous!!.plusDays(1) == date) current + 1 else 1
        longest = maxOf(longest, current)
        previous = date
    }
    return longest
}

/** Days from [today] to [targetDate], never negative. The same function serves exam date and finish-by date. */
fun daysToTarget(targetDate: LocalDate, today: LocalDate): Int =
    ChronoUnit.DAYS.between(today, targetDate).toInt().coerceAtLeast(0)

// ---------------------------------------------------------------------------
// Revision
// ---------------------------------------------------------------------------

/** Days ahead that count as "this week". */
const val REVISION_WEEK_DAYS = 7

private fun RevisionState.dueOrNull(): LocalDate? = nextDueDate.takeIf { enabled }

/** Due today or overdue, most overdue first. Home and Insights both use exactly this set. */
fun revisionsDueNow(states: Collection<RevisionState>, today: LocalDate): List<RevisionState> =
    states.filter { s -> s.dueOrNull()?.let { it <= today } == true }.sortedBy { it.nextDueDate }

/** The overdue subset: due strictly before today. */
fun revisionsOverdue(states: Collection<RevisionState>, today: LocalDate): List<RevisionState> =
    states.filter { s -> s.dueOrNull()?.let { it < today } == true }

/** Everything due by the end of the next [REVISION_WEEK_DAYS] days, including what is due now and overdue. */
fun revisionsDueThisWeek(states: Collection<RevisionState>, today: LocalDate): List<RevisionState> {
    val end = today.plusDays((REVISION_WEEK_DAYS - 1).toLong())
    return states.filter { s -> s.dueOrNull()?.let { it <= end } == true }
}

/** Revision sessions completed in [from]..[to], including partial saves. */
fun revisionsCompleted(sessions: Iterable<StudySession>, from: LocalDate, to: LocalDate): Int =
    sessions.count { it.activityType == StudyActivityType.REVISION && it.countsAsStudy() && it.studyDate in from..to }

data class RevisionOnTime(val onTime: Int, val due: Int) {
    val percent: Int? get() = if (due <= 0) null else onTime * 100 / due
}

/**
 * Revisions done on or before their due date ÷ revisions that fell due in
 * [from]..[to]. A revision "fell due" in the period if it was completed with a
 * recorded due date in the period, or is still pending and already overdue with
 * its due date in the period. Completions with no recorded due date (Learn
 * sessions, partial saves, sessions from before it was recorded) are left out.
 */
fun revisionOnTime(
    sessions: Iterable<StudySession>,
    states: Collection<RevisionState>,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
): RevisionOnTime {
    val completed = sessions.filter {
        it.activityType == StudyActivityType.REVISION &&
            it.status == StudySessionStatus.COMPLETED &&
            it.dueDateAtCompletion != null &&
            it.dueDateAtCompletion!! in from..to
    }
    val onTime = completed.count { it.studyDate <= it.dueDateAtCompletion!! }
    val stillOverdue = revisionsOverdue(states, today).count { it.nextDueDate!! in from..to }
    return RevisionOnTime(onTime = onTime, due = completed.size + stillOverdue)
}
