package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveRevisionTest {
    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `struggled learn is suggested tomorrow`() {
        val state = revisionStateAfterLearn(null, "attempt", "node", today, 1, StudyOutcome.STRUGGLED)

        assertEquals(1, state.intervalDays)
        assertEquals(today.plusDays(1), state.nextDueDate)
    }

    @Test
    fun `strong learn receives a wider initial interval`() {
        val state = revisionStateAfterLearn(null, "attempt", "node", today, 1, StudyOutcome.STRONG)

        assertEquals(7, state.intervalDays)
        assertEquals(today.plusDays(7), state.nextDueDate)
    }

    @Test
    fun `review outcome adapts and stays bounded`() {
        val current = RevisionState("attempt", "node", true, null, today, 2, 30, 1)

        assertEquals(1, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.STRUGGLED).intervalDays)
        assertEquals(60, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.STRONG).intervalDays)
        assertEquals(30, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.OKAY).intervalDays)
    }

    @Test
    fun `post session rating recalibrates the existing suggestion without incrementing review count`() {
        val current = RevisionState("attempt", "node", true, null, today.plusDays(3), 0, 3, 1)
        val adjusted = revisionStateWithOutcome(current, today, StudyOutcome.STRONG, 2)

        assertEquals(0, adjusted.revisionCount)
        assertEquals(6, adjusted.intervalDays)
        assertEquals(today.plusDays(6), adjusted.nextDueDate)
    }
}
