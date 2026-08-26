package com.exam.assistant.domain

/**
 * The arithmetic the product rests on. Pure Kotlin — no Android, no I/O.
 *
 * Values must match the web prototype exactly; [SchedulerTest] pins them.
 */

/** Time the syllabus needs beyond a single read: revision passes and mocks. */
const val REVISION_MULTIPLIER = 1.28

/** SSC CGL Tier-1 total from syllabus_cgl.json. */
const val SSC_CGL_RAW_HOURS = 634.0

data class Cushion(
    /** Hours the syllabus needs, revision included. */
    val need: Int,
    /** Hours the calendar actually supplies before the exam. */
    val have: Int,
    /** [need] minus [have]. Positive means short. */
    val gap: Int,
    /** Percentage of the syllabus covered, capped at 100. */
    val coverage: Int,
    /** Extra hours per day that would close the gap. */
    val extraPerDay: Double,
    /** Roughly how many topics to drop instead, at ~14h each. */
    val topicsToDrop: Int,
    /** Or how many days to push the exam back. */
    val daysToPush: Int,
    /** When ahead: whole spare days in hand. */
    val bufferDays: Int,
) {
    val isShort: Boolean get() = gap > 0
}

fun needHours(rawHours: Double): Int = Math.round(rawHours * REVISION_MULTIPLIER).toInt()

/**
 * Full weeks give five weekdays and two weekend days; the leftover days are
 * counted as weekdays, matching the web prototype.
 */
fun availableHours(days: Int, weekdayHours: Double, weekendHours: Double): Int {
    val weeks = days / 7
    val remainder = days % 7
    return Math.round(weeks * (5 * weekdayHours + 2 * weekendHours) + remainder * weekdayHours).toInt()
}

fun cushion(rawHours: Double, days: Int, weekdayHours: Double, weekendHours: Double): Cushion {
    val need = needHours(rawHours)
    val have = availableHours(days, weekdayHours, weekendHours)
    val gap = need - have
    return Cushion(
        need = need,
        have = have,
        gap = gap,
        coverage = if (need == 0) 0 else ((have.toDouble() / need) * 100).roundToIntCoerced(),
        extraPerDay = if (gap > 0) (Math.round(gap.toDouble() / days * 10) / 10.0) else 0.0,
        topicsToDrop = if (gap > 0) Math.ceil(gap / 14.0).toInt() else 0,
        daysToPush = if (gap > 0) Math.ceil(gap / ((weekdayHours + weekendHours) / 2)).toInt() else 0,
        bufferDays = if (gap <= 0) Math.floor(Math.abs(gap.toDouble()) / weekdayHours).toInt() else 0,
    )
}

private fun Double.roundToIntCoerced(): Int = Math.round(this).toInt().coerceIn(0, 100)

/**
 * Splits a parent's hours across its children in half-hour steps so the parts
 * sum back to the parent exactly — 7h over three children is 2.5 + 2.5 + 2,
 * never 2.33 recurring.
 */
fun splitHours(total: Double, count: Int): List<Double> {
    if (count <= 0) return emptyList()
    val base = Math.floor(total / count * 2) / 2
    val parts = MutableList(count) { base }
    var remaining = Math.round((total - base * count) * 2) / 2.0
    var i = 0
    while (remaining > 0.001 && i < count * 8) {
        parts[i % count] = parts[i % count] + 0.5
        remaining -= 0.5
        i++
    }
    return parts
}
