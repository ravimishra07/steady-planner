package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamDatesTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val exam = LocalDate.parse(DEFAULT_NEET_EXAM_DATE)

    @Test
    fun `configured date is parsed and a bad value falls back to the default`() {
        assertEquals(LocalDate.of(2027, 5, 4), resolveExamDate("2027-05-04"))
        assertEquals(exam, resolveExamDate("soon"))
        assertEquals(exam, resolveExamDate(null))
        assertEquals(LocalDate.of(2027, 5, 2), exam)
    }

    @Test
    fun `finish-by defaults to ninety days before the exam`() {
        assertEquals(exam.minusDays(90), defaultFinishByDate(exam, today))
        assertEquals(LocalDate.of(2027, 2, 1), defaultFinishByDate(exam, today))
    }

    @Test
    fun `default finish-by never lands inside the next week`() {
        val nearExam = today.plusDays(30)
        assertEquals(today.plusDays(7), defaultFinishByDate(nearExam, today))
    }

    @Test
    fun `finish-by must be on or before the exam and at least a week out`() {
        assertNull(validateFinishBy(exam, exam, today))
        assertNull(validateFinishBy(today.plusDays(7), exam, today))
        assertEquals(FinishByIssue.AFTER_EXAM, validateFinishBy(exam.plusDays(1), exam, today))
        assertEquals(FinishByIssue.TOO_SOON, validateFinishBy(today.plusDays(6), exam, today))
    }

    @Test
    fun `coercion clamps both ends`() {
        assertEquals(exam, coerceFinishBy(exam.plusDays(40), exam, today))
        assertEquals(today.plusDays(7), coerceFinishBy(today, exam, today))
    }

    @Test
    fun `days to exam uses the exam date not the finish-by date`() {
        val attempt = ExamAttempt(
            id = "a", examId = NEET_EXAM_ID, syllabusVersion = "v",
            examDate = exam, targetCompletionDate = exam.minusDays(90),
            createdAtEpochMs = 0, updatedAtEpochMs = 0, status = ExamAttemptStatus.ACTIVE,
        )
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(today, exam).toInt(), daysToExam(attempt, today))
        assertEquals(PlanMode.DEADLINE, attempt.planMode)
    }
}
