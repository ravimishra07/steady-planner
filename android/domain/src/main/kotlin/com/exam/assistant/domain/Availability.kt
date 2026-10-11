package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** A recurring weekly free-time window — a hard scheduling constraint. */
data class WeeklyAvailability(
    val id: String,
    val attemptId: String,
    val dayOfWeek: DayOfWeek,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

/** A one-off override of the weekly pattern for a specific date (holiday, travel, coaching day). */
data class AvailabilityOverride(
    val id: String,
    val attemptId: String,
    val date: LocalDate,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

/** Windows in effect for [date]: the override if one exists for that date, else the weekly pattern. */
fun effectiveWindowsFor(
    date: LocalDate,
    weekly: List<WeeklyAvailability>,
    overrides: List<AvailabilityOverride>,
): List<Pair<Int, Int>> {
    val dayOverrides = overrides.filter { it.date == date }
    if (dayOverrides.isNotEmpty()) return dayOverrides.map { it.startMinuteOfDay to it.endMinuteOfDay }
    return weekly.filter { it.dayOfWeek == date.dayOfWeek }.map { it.startMinuteOfDay to it.endMinuteOfDay }
}

/**
 * Migration/default helper: a flat weekday/weekend-hours plan (the legacy
 * shape) turned into one reasonable daily window per day of week, anchored
 * at the existing [DAY_TIMELINE_START].
 */
fun defaultWeeklyAvailability(attemptId: String, weekdayHours: Float, weekendHours: Float): List<WeeklyAvailability> =
    DayOfWeek.entries.map { day ->
        val hours = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) weekendHours else weekdayHours
        val minutes = (hours * 60).toInt().coerceAtLeast(0)
        WeeklyAvailability(
            id = "default_${attemptId}_${day.name}",
            attemptId = attemptId,
            dayOfWeek = day,
            startMinuteOfDay = DAY_TIMELINE_START,
            endMinuteOfDay = (DAY_TIMELINE_START + minutes).coerceAtMost(DAY_TIMELINE_END),
        )
    }
