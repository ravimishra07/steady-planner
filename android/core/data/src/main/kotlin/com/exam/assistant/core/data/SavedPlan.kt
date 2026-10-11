package com.exam.assistant.core.data

import java.net.URLDecoder

/**
 * What onboarding writes and the rest of the app reads later.
 * Kept flat so DataStore does not need a serializer on the critical path.
 */
data class SavedPlan(
    val examId: String,
    val daysUntilExam: Int,
    val workId: String,
    val weekdayHours: Float,
    val weekendHours: Float,
    val studyPlace: String,
    val targetDateEpochDay: Long? = null,
    val coachingId: String = "",
    val wakeMinute: Int = 6 * 60,
    val sleepMinute: Int = 23 * 60,
    val useProvidedSyllabus: Boolean = true,
    val coveredSubjectIds: Set<String> = emptySet(),
    val commitments: Set<String> = emptySet(),
)

data class SavedCommitment(
    val id: String,
    val kind: String,
    val startMinute: Int,
    val endMinute: Int,
    /** Sunday = 0 through Saturday = 6, matching java.time via value modulo 7. */
    val days: Set<Int>,
    val customLabel: String? = null,
)

/** Fail closed per row: malformed legacy/user data is ignored instead of blocking startup. */
fun SavedPlan.decodedCommitments(): List<SavedCommitment> = commitments.mapNotNull { encoded ->
    val parts = encoded.split('|')
    if (parts.size !in 5..6) return@mapNotNull null
    val start = parts[2].toIntOrNull() ?: return@mapNotNull null
    val end = parts[3].toIntOrNull() ?: return@mapNotNull null
    val days = parts[4].split(',').mapNotNull(String::toIntOrNull).filter { it in 0..6 }.toSet()
    if (parts[0].isBlank() || parts[1].isBlank() || end <= start || days.isEmpty()) return@mapNotNull null
    val customLabel = parts.getOrNull(5)
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    SavedCommitment(parts[0], parts[1], start, end, days, customLabel)
}
