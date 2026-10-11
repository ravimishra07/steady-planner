package com.exam.assistant.domain

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Exam date vs finish-by date (PRODUCTION_V1_PLAN §3.2). They are different
 * concepts: the exam date is when NEET happens, the finish-by date is the
 * student's goal for completing the syllabus, ahead of it.
 */

/** Compile-time default for the NEET 2027 date. Not announced by NTA when this shipped. */
const val DEFAULT_NEET_EXAM_DATE = "2027-05-02"

/** Revision window left between finish-by and the exam by default. */
const val DEFAULT_REVISION_WINDOW_DAYS = 90

/** The finish-by date may not be closer than this many days from today. */
const val MIN_FINISH_BY_DAYS_AHEAD = 7

/** Parses the configured exam date; anything unreadable falls back to [DEFAULT_NEET_EXAM_DATE]. */
fun resolveExamDate(configured: String?): LocalDate =
    try {
        LocalDate.parse(configured?.trim().orEmpty())
    } catch (_: DateTimeParseException) {
        LocalDate.parse(DEFAULT_NEET_EXAM_DATE)
    }

/** Exam date − 90 days, kept inside the valid range so it can always be offered as a default. */
fun defaultFinishByDate(examDate: LocalDate, today: LocalDate): LocalDate =
    coerceFinishBy(examDate.minusDays(DEFAULT_REVISION_WINDOW_DAYS.toLong()), examDate, today)

enum class FinishByIssue {
    /** Closer than [MIN_FINISH_BY_DAYS_AHEAD] days from today. */
    TOO_SOON,

    /** After the exam. */
    AFTER_EXAM,
}

/** Null when [finishBy] is on or before [examDate] and at least a week out. */
fun validateFinishBy(finishBy: LocalDate, examDate: LocalDate, today: LocalDate): FinishByIssue? = when {
    finishBy > examDate -> FinishByIssue.AFTER_EXAM
    finishBy < today.plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong()) -> FinishByIssue.TOO_SOON
    else -> null
}

/**
 * Clamps into `[today + 7, examDate]`. When the exam is closer than a week the range is
 * empty; the lower bound wins and [validateFinishBy] still reports [FinishByIssue.AFTER_EXAM].
 */
fun coerceFinishBy(finishBy: LocalDate, examDate: LocalDate, today: LocalDate): LocalDate {
    val lower = today.plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong())
    val upper = maxOf(examDate, lower)
    return finishBy.coerceIn(lower, upper)
}

/** Whole days from [today] to the exam, never negative. The only "days to exam" in the app. */
fun daysToExam(attempt: ExamAttempt, today: LocalDate): Int? = attempt.examDate?.let { daysToTarget(it, today) }
