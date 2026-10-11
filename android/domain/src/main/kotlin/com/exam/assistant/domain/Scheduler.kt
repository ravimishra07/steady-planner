package com.exam.assistant.domain

// Hour splitting for the syllabus tree. Pure Kotlin: no Android, no I/O.
//
// Feasibility ("does it fit before finish-by?") is not computed here: it is PlanFit, read off
// the planner itself, so it cannot disagree with the plan.

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
