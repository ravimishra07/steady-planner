package com.exam.assistant.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Whether the remaining work fits before the finish-by date (PRODUCTION_V1_PLAN §3.4, §4.4).
 *
 * It is read off the planner's own simulation, so onboarding, Today and Insights
 * cannot disagree with what is actually scheduled. Hours are estimates until
 * calibration (§4.1) is active.
 */
data class PlanFit(
    val finishBy: LocalDate,
    /** Learn work still to place, calibrated. */
    val learnMinutes: Int,
    /** Practice work still to place (one block per chapter not yet practiced). */
    val practiceMinutes: Int,
    /** Learn plus Practice minutes the planner could not place on or before [finishBy]. */
    val shortByMinutes: Int,
    /** Days from finish-by to the exam: the revision window. Zero without an exam date. */
    val revisionWindowDays: Int,
    /** True once the hours are scaled by the student's own pace; until then they are the bundled estimates. */
    val calibrated: Boolean = false,
    /** Ways to close the gap, with their computed effect. Empty when it fits. */
    val options: List<PlanFitOption> = emptyList(),
) {
    val requiredMinutes: Int get() = learnMinutes + practiceMinutes
    val scheduledMinutes: Int get() = (requiredMinutes - shortByMinutes).coerceAtLeast(0)
    val fits: Boolean get() = shortByMinutes <= 0

    /** Share of the remaining work the plan covers by finish-by, rounded down. 100 when it fits. */
    val coveragePercent: Int get() = if (requiredMinutes <= 0 || fits) 100 else scheduledMinutes * 100 / requiredMinutes
}

/** One way out of a plan that does not fit. Each has been checked by re-running the planner. */
sealed interface PlanFitOption {
    /** Add [minutes] to every day's target (weekdays and weekends). */
    data class AddMinutesPerDay(val minutes: Int) : PlanFitOption

    /** Park these chapters, lowest PYQ yield first. [minutesFreed] is their Learn plus Practice work. */
    data class ParkChapters(val chapterIds: List<String>, val minutesFreed: Int) : PlanFitOption {
        val count: Int get() = chapterIds.size
    }

    /** Move finish-by to [date]. */
    data class MoveFinishBy(val date: LocalDate) : PlanFitOption

    /** Keep the plan as it is and finish about [percent]% of the remaining work. */
    data class AcceptCoverage(val percent: Int) : PlanFitOption
}

/** Step the "add minutes" search moves in, and its ceiling. */
const val PLAN_FIT_MINUTES_STEP = 15
const val PLAN_FIT_MAX_EXTRA_MINUTES = 8 * 60

/**
 * The deadline-mode fit for these inputs, or null in steady mode (no deadline, so never "short").
 * With [withOptions] a plan that does not fit also lists its choices, each verified by planning again.
 */
fun PlanInputs.planFit(nowMs: Long, withOptions: Boolean = true): PlanFit? {
    if (mode != PlanMode.DEADLINE) return null
    val fit = plan(horizonDays = 1, nowMs = nowMs).fit ?: return null
    if (fit.fits || !withOptions) return fit
    return fit.copy(options = planFitOptions(fit, nowMs))
}

private fun PlanInputs.fitsAt(nowMs: Long): Boolean = planFit(nowMs, withOptions = false)?.fits == true

private fun PlanInputs.planFitOptions(fit: PlanFit, nowMs: Long): List<PlanFitOption> = buildList {
    addMinutesOption(nowMs)?.let(::add)
    parkOption(fit, nowMs)?.let(::add)
    moveFinishByOption(nowMs)?.let(::add)
    add(PlanFitOption.AcceptCoverage(fit.coveragePercent))
}

private fun Int.plusCapped(extra: Int): Int = (toLong() + extra).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun PlanInputs.addMinutesOption(nowMs: Long): PlanFitOption? {
    // Unlimited daily targets: only the free windows limit the plan, so more minutes cannot help.
    if (weekdayTargetMinutes >= Int.MAX_VALUE / 2 || weekendTargetMinutes >= Int.MAX_VALUE / 2) return null
    fun fitsWith(units: Int) = copy(
        weekdayTargetMinutes = weekdayTargetMinutes.plusCapped(units * PLAN_FIT_MINUTES_STEP),
        weekendTargetMinutes = weekendTargetMinutes.plusCapped(units * PLAN_FIT_MINUTES_STEP),
    ).fitsAt(nowMs)
    val maxUnits = PLAN_FIT_MAX_EXTRA_MINUTES / PLAN_FIT_MINUTES_STEP
    if (!fitsWith(maxUnits)) return null
    var low = 1
    var high = maxUnits
    while (low < high) {
        val mid = (low + high) / 2
        if (fitsWith(mid)) high = mid else low = mid + 1
    }
    return PlanFitOption.AddMinutesPerDay(low * PLAN_FIT_MINUTES_STEP)
}

private fun PlanInputs.parkOption(fit: PlanFit, nowMs: Long): PlanFitOption? {
    val candidates = chapterNeeds(nowMs).filter { it.totalMinutes > 0 }.sortedWith(
        compareBy<ChapterNeed>(
            { parkRank(priorities[it.chapterId]?.band ?: PriorityBand.NO_DATA) },
            { priorities[it.chapterId]?.meanQuestionsPerYear ?: 0.0 },
            { -it.totalMinutes },
            { it.chapterId },
        ),
    )
    val parked = mutableListOf<ChapterNeed>()
    var freed = 0
    for (chapter in candidates) {
        parked += chapter
        freed += chapter.totalMinutes
        if (freed < fit.shortByMinutes) continue
        val remaining = includedLeafIds - parked.flatMap { it.leafIds }.toSet()
        if (copy(includedLeafIds = remaining).fitsAt(nowMs)) {
            return PlanFitOption.ParkChapters(parked.map { it.chapterId }, freed)
        }
    }
    return null
}

/** Park low yield first, then chapters we know nothing about, then medium, then high. */
private fun parkRank(band: PriorityBand): Int = when (band) {
    PriorityBand.LOW -> 0
    PriorityBand.NO_DATA -> 1
    PriorityBand.MEDIUM -> 2
    PriorityBand.HIGH -> 3
}

private fun PlanInputs.moveFinishByOption(nowMs: Long): PlanFitOption? {
    val latest = examDate?.minusDays(1) ?: targetDate.plusDays(365)
    val maxShift = ChronoUnit.DAYS.between(targetDate, latest).toInt()
    if (maxShift <= 0) return null
    fun fitsAt(shift: Int) = copy(targetDate = targetDate.plusDays(shift.toLong())).fitsAt(nowMs)
    if (!fitsAt(maxShift)) return null
    var low = 1
    var high = max(maxShift, 1)
    while (low < high) {
        val mid = (low + high) / 2
        if (fitsAt(mid)) high = mid else low = mid + 1
    }
    return PlanFitOption.MoveFinishBy(targetDate.plusDays(low.toLong()))
}
