package com.exam.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class InsightPeriod(val days: Long?) {
    DAY(1),
    WEEK(7),
    MONTH(30),
    THREE_MONTHS(90),
    ALL(null),
}

/** Everything about the student's plan that Insights needs; none of it is derived here. */
data class InsightPlan(
    val startedOn: LocalDate,
    val targetDate: LocalDate,
    val weeklyAvailability: List<WeeklyAvailability> = emptyList(),
    val availabilityOverrides: List<AvailabilityOverride> = emptyList(),
    val weekdayTargetMinutes: Int = 4 * 60,
    val weekendTargetMinutes: Int = 7 * 60,
)

data class StudyDayInsight(
    val date: LocalDate,
    val minutes: Int,
    val sessions: Int,
)

/** One subject's coverage. The unit counted is the syllabus leaf: an NCERT section. */
data class SubjectInsight(
    val key: String,
    val name: String,
    val subjectId: String,
    val percent: Int,
    val remainingSections: Int,
    val totalSections: Int,
    val completedSections: Int,
    /** Null while hidden (first 7 days). */
    val status: SubjectStatus?,
)

data class RevisionInsight(
    /** Due today or overdue. */
    val dueNow: Int = 0,
    /** The overdue subset of [dueNow]. */
    val overdue: Int = 0,
    /** Due within the next 7 days, including [dueNow]. */
    val dueThisWeek: Int = 0,
    /** Revision sessions completed in the last 7 days. */
    val completedThisWeek: Int = 0,
    /** Over the selected period; null with nothing due in it. */
    val onTimePercent: Int? = null,
    val dueTitles: List<String> = emptyList(),
)

data class ForecastInsight(
    val targetDate: LocalDate,
    val forecastDate: LocalDate?,
    val daysDelta: Int?,
    val recentAverageMinutesPerDay: Int?,
    /** Per day the student has study time, not per calendar day. */
    val requiredMinutesPerDay: Int?,
    val extraMinutesPerDay: Int?,
    val hasReliablePace: Boolean,
    val forecastEarliestDate: LocalDate? = null,
    val forecastLatestDate: LocalDate? = null,
    val evidenceActiveDays: Int = 0,
    val evidenceWindowDays: Int = PACE_WINDOW_DAYS,
)

data class InsightsData(
    val period: InsightPeriod,
    val hasStudyHistory: Boolean,
    val hasStudyInPeriod: Boolean,
    val periodStart: LocalDate,
    val periodDays: List<StudyDayInsight>,
    val trendDays: List<StudyDayInsight>,
    /** Always the last 12 weeks, whatever the selected period. */
    val heatmapDays: List<StudyDayInsight>,
    val totalStudiedMinutes: Int,
    val completedSessionCount: Int,
    val averagePerActiveDayMinutes: Int?,
    val bestDay: StudyDayInsight?,
    /** Planned minutes of blocks in the period whose end time has passed. */
    val plannedMinutes: Int,
    val completedPlannedMinutes: Int,
    val extraStudyMinutes: Int,
    val planPercent: Int?,
    val planDeltaMinutes: Int,
    val syllabusTotalSections: Int,
    val syllabusCompletedSections: Int,
    val syllabusRemainingSections: Int,
    val syllabusPercent: Int,
    val officialSectionCount: Int,
    val excludedSectionCount: Int,
    val targetMinutes: Int,
    val remainingMinutes: Int,
    val subjects: List<SubjectInsight>,
    val forecast: ForecastInsight,
    val revision: RevisionInsight,
    val averageSessionMinutes: Int?,
    val longestSessionMinutes: Int?,
    val averageBreakMinutes: Int?,
    val studyDaysInPeriod: Int,
    val currentStreak: Int,
    val longestStreak: Int,
)

const val HEATMAP_DAYS = 84

/**
 * Calculates all Insights values from prepared local state. Each number comes
 * from [Metrics.kt][coverage] and friends; this function only chooses the
 * period window and assembles the result.
 */
fun computeInsights(
    pack: ExamPack,
    syllabus: SyllabusMetrics,
    sessions: List<StudySession>,
    planBlocks: List<StudyPlanBlock>,
    revisionStates: List<RevisionState>,
    plan: InsightPlan,
    period: InsightPeriod,
    today: LocalDate,
    nowMinuteOfDay: Int,
): InsightsData {
    val counted = sessions.filter { it.countsAsStudy() }
    val periodStart = period.days?.let { today.minusDays(it - 1) }
        ?: counted.minOfOrNull { it.studyDate }?.coerceAtMost(today)
        ?: today
    val minutesByDate = studiedMinutesByDate(sessions)
    val sessionsByDate = counted.groupingBy { it.studyDate }.eachCount()
    fun day(date: LocalDate) = StudyDayInsight(date, minutesByDate[date] ?: 0, sessionsByDate[date] ?: 0)

    val periodDays = (0..ChronoUnit.DAYS.between(periodStart, today)).map { day(periodStart.plusDays(it)) }
    val trendDays = aggregateTrend(periodDays, period)
    val heatmapDays = (0 until HEATMAP_DAYS).map { day(today.minusDays((HEATMAP_DAYS - 1 - it).toLong())) }

    val periodSessions = counted.filter { it.studyDate in periodStart..today }
    val totalMinutes = studiedMinutes(sessions, periodStart, today)
    val activeDays = periodDays.filter { it.minutes > 0 || it.sessions > 0 }
    val adherence = planAdherence(planBlocks, sessions, periodStart, today, today, nowMinuteOfDay)
    val plannedSessions = periodSessions.filter { it.planBlockId != null }
    val extraStudyMinutes = (totalMinutes - plannedSessions.sumOf { it.focusedSeconds } / 60).coerceAtLeast(0)

    val overall = syllabus.overall
    val officialLeafCount = pack.leafIds().size
    val remaining = syllabus.remainingMinutes
    val subjects = pack.subjects.mapNotNull { subject ->
        val cover = syllabus.bySubject.getValue(subject.id)
        if (cover.total == 0) return@mapNotNull null
        SubjectInsight(
            key = subject.id,
            name = subject.name,
            subjectId = subject.id,
            percent = cover.percent,
            remainingSections = cover.remaining,
            totalSections = cover.total,
            completedSections = cover.covered,
            status = subjectStatus(cover.percent, plan.startedOn, plan.targetDate, today),
        )
    }

    val forecast = forecastInsight(sessions, remaining, plan, today)

    val weekStart = today.minusDays((REVISION_WEEK_DAYS - 1).toLong())
    val dueNow = revisionsDueNow(revisionStates, today)
    val revision = RevisionInsight(
        dueNow = dueNow.size,
        overdue = revisionsOverdue(revisionStates, today).size,
        dueThisWeek = revisionsDueThisWeek(revisionStates, today).size,
        completedThisWeek = revisionsCompleted(sessions, weekStart, today),
        onTimePercent = revisionOnTime(sessions, revisionStates, periodStart, today, today).percent,
        dueTitles = dueNow.mapNotNull { pack.findNode(it.nodeId)?.title },
    )

    val dates = studyDates(sessions)
    return InsightsData(
        period = period,
        hasStudyHistory = counted.isNotEmpty(),
        hasStudyInPeriod = periodSessions.isNotEmpty(),
        periodStart = periodStart,
        periodDays = periodDays,
        trendDays = trendDays,
        heatmapDays = heatmapDays,
        totalStudiedMinutes = totalMinutes,
        completedSessionCount = periodSessions.size,
        averagePerActiveDayMinutes = activeDays.takeIf { it.isNotEmpty() }?.let { totalMinutes / it.size },
        bestDay = activeDays.maxByOrNull { it.minutes },
        plannedMinutes = adherence.plannedMinutes,
        completedPlannedMinutes = adherence.completedMinutes,
        extraStudyMinutes = extraStudyMinutes,
        planPercent = adherence.percent,
        planDeltaMinutes = adherence.completedMinutes - adherence.plannedMinutes,
        syllabusTotalSections = overall.total,
        syllabusCompletedSections = overall.covered,
        syllabusRemainingSections = overall.remaining,
        syllabusPercent = overall.percent,
        officialSectionCount = officialLeafCount,
        excludedSectionCount = officialLeafCount - overall.total,
        targetMinutes = syllabus.targetMinutes,
        remainingMinutes = remaining,
        subjects = subjects,
        forecast = forecast,
        revision = revision,
        averageSessionMinutes = periodSessions.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.focusedSeconds } / 60 / list.size },
        longestSessionMinutes = periodSessions.maxOfOrNull { it.focusedSeconds / 60 },
        averageBreakMinutes = averageBreakMinutes(periodSessions),
        studyDaysInPeriod = activeDays.size,
        currentStreak = currentStreak(dates, today),
        longestStreak = longestStreak(dates),
    )
}

private fun aggregateTrend(days: List<StudyDayInsight>, period: InsightPeriod): List<StudyDayInsight> {
    if (days.isEmpty()) return emptyList()
    val bucketSize = when (period) {
        InsightPeriod.DAY, InsightPeriod.WEEK -> 1
        InsightPeriod.MONTH -> 5
        InsightPeriod.THREE_MONTHS -> 7
        InsightPeriod.ALL -> ceil(days.size / 8.0).toInt().coerceAtLeast(1)
    }
    return days.chunked(bucketSize).map { bucket ->
        StudyDayInsight(
            date = bucket.first().date,
            minutes = bucket.sumOf { it.minutes },
            sessions = bucket.sumOf { it.sessions },
        )
    }
}

private fun forecastInsight(
    sessions: List<StudySession>,
    remainingMinutes: Int,
    plan: InsightPlan,
    today: LocalDate,
): ForecastInsight {
    val allPace = pace(sessions, today)
    val learnPace = pace(sessions, today, learnOnly = true)
    val forecast = finishForecast(remainingMinutes, learnPace, today)
    val available = availableStudyTime(
        from = today,
        to = plan.targetDate,
        weekly = plan.weeklyAvailability,
        overrides = plan.availabilityOverrides,
        weekdayTargetMinutes = plan.weekdayTargetMinutes,
        weekendTargetMinutes = plan.weekendTargetMinutes,
    )
    val required = requiredPace(remainingMinutes, available).minutesPerStudyDay?.takeIf { remainingMinutes > 0 }
    val recentAverage = allPace?.minutesPerDay?.roundToInt()
    return ForecastInsight(
        targetDate = plan.targetDate,
        forecastDate = forecast?.expected,
        daysDelta = forecast?.let { ChronoUnit.DAYS.between(plan.targetDate, it.expected).toInt() },
        recentAverageMinutesPerDay = recentAverage,
        requiredMinutesPerDay = required,
        extraMinutesPerDay = if (recentAverage != null && required != null && required > recentAverage) required - recentAverage else 0,
        hasReliablePace = forecast != null,
        forecastEarliestDate = forecast?.earliest,
        forecastLatestDate = forecast?.latest,
        evidenceActiveDays = allPace?.activeDays ?: sessions.activeDaysInPaceWindow(today),
        evidenceWindowDays = PACE_WINDOW_DAYS,
    )
}

private fun List<StudySession>.activeDaysInPaceWindow(today: LocalDate): Int {
    val start = today.minusDays((PACE_WINDOW_DAYS - 1).toLong())
    return filter { it.countsAsStudy() && it.studyDate in start..today }.map { it.studyDate }.distinct().size
}

private fun averageBreakMinutes(sessions: List<StudySession>): Int? {
    val gaps = sessions.groupBy { it.studyDate }.values.flatMap { day ->
        day.sortedBy { it.startedAtEpochMs }.zipWithNext().mapNotNull { (first, second) ->
            val firstEnd = first.endedAtEpochMs ?: (first.startedAtEpochMs + first.focusedSeconds * 1_000L)
            val gap = ((second.startedAtEpochMs - firstEnd) / 60_000L).toInt()
            gap.takeIf { it in 1..180 }
        }
    }
    return gaps.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

fun formatInsightDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(java.util.Locale.getDefault()),
)

fun formatInsightMinutes(minutes: Int?): String {
    if (minutes == null) return "—"
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours == 0 -> "${remainder}m"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}m"
    }
}
