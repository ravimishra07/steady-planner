package com.exam.assistant.domain

import java.time.LocalDate

/**
 * The student's attempt at one exam. The central object everything else
 * hangs off: which pack, which syllabus version, absolute exam/target dates.
 *
 * [examDate] and [targetCompletionDate] are different concepts — a student
 * may intentionally finish the syllabus before the exam to leave time for
 * revision and mocks. Both are absolute dates; nothing derives them from a
 * stored relative day-count, so they never drift as days pass.
 */
data class ExamAttempt(
    val id: String,
    val examId: String,
    val syllabusVersion: String,

    val examDate: LocalDate?,
    val targetCompletionDate: LocalDate,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    val status: ExamAttemptStatus,
)

enum class ExamAttemptStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

fun daysUntil(date: LocalDate, today: LocalDate): Int =
    java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()

/** Small, deliberately minimal — the exam attempt is the real domain object, not this. */
data class UserProfile(
    val id: String,
    val timeZoneId: String,
    val weekStartsOn: java.time.DayOfWeek,
    val localeTag: String,
    val createdAtEpochMs: Long,
)
