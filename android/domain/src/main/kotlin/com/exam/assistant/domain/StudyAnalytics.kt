package com.exam.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class InsightPeriod(val label: String, val days: Long?) {
    DAY("1D", 1),
    WEEK("7D", 7),
    MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    ALL("All", null),
}

data class InsightPlan(
    val daysUntilTarget: Int,
    val weekdayHours: Float,
    val weekendHours: Float,
)

data class StudyDayInsight(
    val date: LocalDate,
    val minutes: Int,
    val sessions: Int,
    val plannedMinutes: Int,
)

data class SubjectInsight(
    val key: String,
    val name: String,
    val subjectId: String,
    val percent: Int,
    val remainingTopics: Int,
    val totalTopics: Int,
    val completedTopics: Int,
    val status: SubjectStatus,
)

enum class SubjectStatus { AHEAD, ON_TRACK, BEHIND }

data class RevisionInsight(
    val dueNow: Int = 0,
    val dueThisWeek: Int = 0,
    val completedThisWeek: Int = 0,
    val onTimePercent: Int? = null,
    val dueTitles: List<String> = emptyList(),
)

data class ForecastInsight(
    val targetDate: LocalDate,
    val forecastDate: LocalDate?,
    val daysDelta: Int?,
    val recentAverageMinutesPerDay: Int?,
    val requiredMinutesPerDay: Int?,
    val extraMinutesPerDay: Int?,
    val hasReliablePace: Boolean,
)

data class InsightsData(
    val period: InsightPeriod,
    val hasStudyHistory: Boolean,
    val hasStudyInPeriod: Boolean,
    val periodStart: LocalDate,
    val periodDays: List<StudyDayInsight>,
    val trendDays: List<StudyDayInsight>,
    val heatmapDays: List<StudyDayInsight>,
    val totalStudiedMinutes: Int,
    val completedSessionCount: Int,
    val averagePerActiveDayMinutes: Int?,
    val bestDay: StudyDayInsight?,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val planPercent: Int?,
    val planDeltaMinutes: Int,
    val syllabusTotalTopics: Int,
    val syllabusCompletedTopics: Int,
    val syllabusRemainingTopics: Int,
    val syllabusPercent: Int,
    val officialTopicCount: Int,
    val targetHours: Int,
    val completedTargetHours: Int,
    val excludedTopicCount: Int,
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

/**
 * Calculates all Insights values from prepared local state. The UI only renders
 * this result, keeping period math and target-syllabus rules testable on the JVM.
 */
fun computeInsights(
    sessions: List<StudySessionRecord>,
    sections: List<SyllabusSection>,
    doneLeaves: Set<String>,
    excludedSectionKeys: Set<String> = emptySet(),
    plan: InsightPlan,
    period: InsightPeriod,
    today: LocalDate = LocalDate.now(),
): InsightsData {
    val completed = sessions.filter { it.completed }
    val periodStart = period.days?.let { today.minusDays(it - 1) }
        ?: completed.minOfOrNull { it.date } ?: today
    val periodDates = (0 until (ChronoUnit.DAYS.between(periodStart, today).toInt() + 1))
        .map { periodStart.plusDays(it.toLong()) }
    val periodSessions = sessions.filter { it.date in periodStart..today }
    val completedPeriod = periodSessions.filter { it.completed }
    val periodDays = periodDates.map { date -> dayInsight(date, periodSessions) }
    val trendDays = aggregateTrend(periodDays, period)
    val heatmapStart = today.minusDays(83)
    val heatmapDays = (0..83).map { offset ->
        val date = heatmapStart.plusDays(offset.toLong())
        dayInsight(date, sessions)
    }

    val totalMinutes = completedPeriod.sumOf { it.durationMinutes }
    val activeDays = periodDays.filter { it.minutes > 0 }
    val bestDay = activeDays.maxByOrNull { it.minutes }
    val plannedMinutes = periodDays.sumOf { it.plannedMinutes }
    val planPercent = plannedMinutes.takeIf { it > 0 }?.let {
        ((totalMinutes.toDouble() / it) * 100).roundToInt().coerceAtMost(100)
    }

    val targetSections = sections.mapIndexedNotNull { index, section ->
        if ("section_$index" in excludedSectionKeys) null else index to section
    }
    val officialLeaves = sections.flatMapIndexed { index, section -> sectionLeafKeys(index, section) }
    val targetLeaves = targetSections.flatMap { (index, section) -> sectionLeafKeys(index, section) }
    val completedTopics = targetLeaves.count { it in doneLeaves }
    val totalTopics = targetLeaves.size
    val excludedTopics = officialLeaves.size - totalTopics
    val completedPercent = if (totalTopics == 0) 0 else (completedTopics * 100 / totalTopics)
    val targetHours = targetSections.sumOf { (_, section) -> section.topics.sumOf(::topicHours) }.roundToInt()
    val completedTargetHours = if (totalTopics == 0) 0 else {
        (targetHours * (completedTopics.toDouble() / totalTopics)).roundToInt()
    }

    val subjects = targetSections.map { (index, section) ->
        val leaves = sectionLeafKeys(index, section)
        val done = leaves.count { it in doneLeaves }
        val percent = if (leaves.isEmpty()) 0 else done * 100 / leaves.size
        val status = when {
            percent >= completedPercent + 8 -> SubjectStatus.AHEAD
            percent <= completedPercent - 8 -> SubjectStatus.BEHIND
            else -> SubjectStatus.ON_TRACK
        }
        SubjectInsight(
            key = "section_$index",
            name = section.name,
            subjectId = sectionSubjectId(index),
            percent = percent,
            remainingTopics = leaves.size - done,
            totalTopics = leaves.size,
            completedTopics = done,
            status = status,
        )
    }

    val forecast = forecastInsight(
        completed = completed,
        remainingTopics = totalTopics - completedTopics,
        targetTopics = totalTopics,
        targetHours = targetHours,
        plan = plan,
        today = today,
    )
    val revision = revisionInsight(sessions, today)
    val behaviorSessions = completedPeriod
    val averageBreak = averageBreakMinutes(behaviorSessions)

    return InsightsData(
        period = period,
        hasStudyHistory = completed.isNotEmpty(),
        hasStudyInPeriod = completedPeriod.isNotEmpty(),
        periodStart = periodStart,
        periodDays = periodDays,
        trendDays = trendDays,
        heatmapDays = heatmapDays,
        totalStudiedMinutes = totalMinutes,
        completedSessionCount = completedPeriod.size,
        averagePerActiveDayMinutes = activeDays.takeIf { it.isNotEmpty() }?.let { totalMinutes / it.size },
        bestDay = bestDay,
        plannedMinutes = plannedMinutes,
        actualMinutes = totalMinutes,
        planPercent = planPercent,
        planDeltaMinutes = totalMinutes - plannedMinutes,
        syllabusTotalTopics = totalTopics,
        syllabusCompletedTopics = completedTopics,
        syllabusRemainingTopics = totalTopics - completedTopics,
        syllabusPercent = completedPercent,
        officialTopicCount = officialLeaves.size,
        targetHours = targetHours,
        completedTargetHours = completedTargetHours,
        excludedTopicCount = excludedTopics,
        subjects = subjects,
        forecast = forecast,
        revision = revision,
        averageSessionMinutes = behaviorSessions.takeIf { it.isNotEmpty() }?.let { behaviorSessions.sumOf(StudySessionRecord::durationMinutes) / it.size },
        longestSessionMinutes = behaviorSessions.maxOfOrNull(StudySessionRecord::durationMinutes),
        averageBreakMinutes = averageBreak,
        studyDaysInPeriod = activeDays.size,
        currentStreak = currentStreak(completed.map { it.date }.toSet(), today),
        longestStreak = longestStreak(completed.map { it.date }.toSet()),
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
            plannedMinutes = bucket.sumOf { it.plannedMinutes },
        )
    }
}

private fun dayInsight(date: LocalDate, sessions: List<StudySessionRecord>): StudyDayInsight {
    val daySessions = sessions.filter { it.date == date }
    return StudyDayInsight(
        date = date,
        minutes = daySessions.filter { it.completed }.sumOf { it.durationMinutes },
        sessions = daySessions.count { it.completed },
        plannedMinutes = daySessions.sumOf { it.durationMinutes },
    )
}

private fun sectionLeafKeys(index: Int, section: SyllabusSection): List<String> =
    section.topics.flatMapIndexed { topicIndex, topic -> leafKeys(topic, "t1_${index}_$topicIndex") }

private fun forecastInsight(
    completed: List<StudySessionRecord>,
    remainingTopics: Int,
    targetTopics: Int,
    targetHours: Int,
    plan: InsightPlan,
    today: LocalDate,
): ForecastInsight {
    val targetDate = today.plusDays(plan.daysUntilTarget.coerceAtLeast(0).toLong())
    val recentStart = today.minusDays(13)
    val recent = completed.filter { it.date in recentStart..today }
    val activeRecentDays = recent.map { it.date }.distinct().size
    val recentAverage = recent.sumOf { it.durationMinutes }.takeIf { activeRecentDays >= 3 }?.div(14)
    val remainingMinutes = if (targetTopics == 0) 0 else {
        (targetHours * 60 * (remainingTopics.toDouble() / targetTopics)).roundToInt()
    }
    val targetDays = ChronoUnit.DAYS.between(today, targetDate).toInt().coerceAtLeast(1)
    val required = if (remainingMinutes > 0) ceil(remainingMinutes.toDouble() / targetDays).toInt() else 0
    val forecastDate = recentAverage?.takeIf { it > 0 }?.let { pace ->
        today.plusDays(ceil(remainingMinutes.toDouble() / pace).toLong().coerceAtLeast(0))
    }
    return ForecastInsight(
        targetDate = targetDate,
        forecastDate = forecastDate,
        daysDelta = forecastDate?.let { ChronoUnit.DAYS.between(targetDate, it).toInt() },
        recentAverageMinutesPerDay = recentAverage,
        requiredMinutesPerDay = required.takeIf { remainingMinutes > 0 },
        extraMinutesPerDay = if (recentAverage != null && required > recentAverage) required - recentAverage else 0,
        hasReliablePace = recentAverage != null,
    )
}

private fun revisionInsight(sessions: List<StudySessionRecord>, today: LocalDate): RevisionInsight {
    val dueDays = (0..6).map { today.plusDays(it.toLong()) }
    val dueByDay = dueDays.associateWith { dueDate ->
        revisionSuggestions(sessions, dueDate)
    }
    val completedStart = today.minusDays(6)
    val completedThisWeek = sessions.count { it.isRevision && it.completed && it.date in completedStart..today }
    val dueNow = dueByDay[today].orEmpty()
    val dueThisWeek = dueByDay.values.sumOf { it.size }
    val totalWork = dueThisWeek + completedThisWeek
    return RevisionInsight(
        dueNow = dueNow.size,
        dueThisWeek = dueThisWeek,
        completedThisWeek = completedThisWeek,
        onTimePercent = totalWork.takeIf { it > 0 }?.let { completedThisWeek * 100 / it },
        dueTitles = dueNow.map { it.title },
    )
}

private fun averageBreakMinutes(sessions: List<StudySessionRecord>): Int? {
    val gaps = sessions.groupBy { it.date }.values.flatMap { day ->
        day.sortedBy { it.startMinuteOfDay }.zipWithNext().mapNotNull { (first, second) ->
            val gap = second.startMinuteOfDay - (first.startMinuteOfDay + first.durationMinutes)
            gap.takeIf { it in 1..180 }
        }
    }
    return gaps.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

private fun longestStreak(dates: Set<LocalDate>): Int {
    if (dates.isEmpty()) return 0
    val sorted = dates.sorted()
    var longest = 1
    var current = 1
    sorted.zipWithNext().forEach { (previous, next) ->
        if (previous.plusDays(1) == next) {
            current++
            longest = max(longest, current)
        } else {
            current = 1
        }
    }
    return longest
}

private fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int {
    if (dates.isEmpty()) return 0
    var cursor = if (today in dates) today else today.minusDays(1)
    var count = 0
    while (cursor in dates) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

fun formatInsightDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM"))

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
