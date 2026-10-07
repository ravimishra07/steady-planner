package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveRevisionTest {
    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `interval table after learn`() {
        assertEquals(1, RevisionSchedule.intervalAfterLearn(StudyOutcome.STRUGGLED))
        assertEquals(3, RevisionSchedule.intervalAfterLearn(StudyOutcome.OKAY))
        assertEquals(7, RevisionSchedule.intervalAfterLearn(StudyOutcome.STRONG))
        assertEquals(3, RevisionSchedule.intervalAfterLearn(null))
    }

    @Test
    fun `interval table after revision`() {
        assertEquals(1, RevisionSchedule.intervalAfterRevision(30, StudyOutcome.STRUGGLED))
        assertEquals(6, RevisionSchedule.intervalAfterRevision(3, StudyOutcome.OKAY))
        assertEquals(30, RevisionSchedule.intervalAfterRevision(20, StudyOutcome.OKAY))
        assertEquals(21, RevisionSchedule.intervalAfterRevision(7, StudyOutcome.STRONG))
        assertEquals(60, RevisionSchedule.intervalAfterRevision(30, StudyOutcome.STRONG))
        assertEquals(6, RevisionSchedule.intervalAfterRevision(null, StudyOutcome.OKAY))
    }

    @Test
    fun `struggled learn is suggested tomorrow`() {
        val state = revisionStateAfterLearn(null, "attempt", "node", today, 1, StudyOutcome.STRUGGLED)

        assertEquals(1, state.intervalDays)
        assertEquals(today.plusDays(1), state.nextDueDate)
    }

    @Test
    fun `existing schedule is not reset by another learn session`() {
        val current = RevisionState("attempt", "node", true, null, today.plusDays(20), 3, 30, 1)

        assertEquals(current, revisionStateAfterLearn(current, "attempt", "node", today, 2, StudyOutcome.STRUGGLED))
    }

    @Test
    fun `review outcome adapts and stays bounded`() {
        val current = RevisionState("attempt", "node", true, null, today, 2, 30, 1)

        assertEquals(1, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.STRUGGLED).intervalDays)
        assertEquals(60, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.STRONG).intervalDays)
        assertEquals(30, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.OKAY).intervalDays)
        assertEquals(3, revisionStateAfterReview(current, "attempt", "node", today, 2, StudyOutcome.OKAY).revisionCount)
    }

    @Test
    fun `learn then Strong rating gives a first interval of 7 - applied once`() {
        val learn = session(StudyActivityType.LEARN)
        val completed = completeStudySession(learn, currentRevision = null, today = today, nowMs = 10)
        // Before the rating the schedule is the provisional Okay interval.
        assertEquals(3, completed.revision?.intervalDays)

        val rated = recordStudyOutcome(completed.session, completed.revision, StudyOutcome.STRONG, nowMs = 20)

        assertEquals(7, rated.revision?.intervalDays)
        assertEquals(today.plusDays(7), rated.revision?.nextDueDate)
        assertEquals(0, rated.revision?.revisionCount)
        assertEquals(StudyOutcome.STRONG, rated.session.outcome)

        // Re-recording the same rating is idempotent: nothing compounds.
        val again = recordStudyOutcome(rated.session, rated.revision, StudyOutcome.STRONG, nowMs = 30)
        assertEquals(7, again.revision?.intervalDays)
        assertEquals(today.plusDays(7), again.revision?.nextDueDate)
    }

    @Test
    fun `revision then Strong rating gives previous x3 capped at 60`() {
        val previous = RevisionState("attempt", "node", true, null, today, 1, 7, 1)
        val completed = completeStudySession(session(StudyActivityType.REVISION), previous, today, nowMs = 10)
        assertEquals(14, completed.revision?.intervalDays) // provisional Okay: 7 x 2

        val rated = recordStudyOutcome(completed.session, completed.revision, StudyOutcome.STRONG, nowMs = 20)

        assertEquals(21, rated.revision?.intervalDays) // 7 x 3
        assertEquals(today.plusDays(21), rated.revision?.nextDueDate)
        assertEquals(2, rated.revision?.revisionCount) // the review is counted once, not by the rating

        val capped = RevisionState("attempt", "node", true, null, today, 4, 30, 1)
        val completedCapped = completeStudySession(session(StudyActivityType.REVISION), capped, today, nowMs = 10)
        val ratedCapped = recordStudyOutcome(completedCapped.session, completedCapped.revision, StudyOutcome.STRONG, nowMs = 20)
        assertEquals(60, ratedCapped.revision?.intervalDays)
    }

    @Test
    fun `revision completion records the due date for on-time tracking`() {
        val due = today.minusDays(2)
        val previous = RevisionState("attempt", "node", true, null, due, 1, 3, 1)

        val completed = completeStudySession(session(StudyActivityType.REVISION), previous, today, nowMs = 10)

        assertEquals(due, completed.session.dueDateAtCompletion)
        assertEquals(3, completed.session.priorIntervalDays)
    }

    @Test
    fun `learn on a topic that already has a schedule keeps it when rated`() {
        val existing = RevisionState("attempt", "node", true, null, today.plusDays(10), 2, 14, 1)
        val completed = completeStudySession(session(StudyActivityType.LEARN), existing, today, nowMs = 10)
        assertEquals(existing, completed.revision)

        val rated = recordStudyOutcome(completed.session, completed.revision, StudyOutcome.STRUGGLED, nowMs = 20)

        assertEquals(existing, rated.revision)
    }

    @Test
    fun `session without a topic produces no revision`() {
        val completed = completeStudySession(session(StudyActivityType.LEARN).copy(nodeId = null), null, today, 10)

        assertNull(completed.revision)
        assertNotNull(completed.session.endedAtEpochMs)
    }

    @Test
    fun `preview of the next due date matches what completion will schedule`() {
        val existing = RevisionState("attempt", "node", true, null, today.plusDays(9), 1, 7, 1)

        assertEquals(today.plusDays(3), nextRevisionDueIfCompleted(null, StudyActivityType.LEARN, today))
        assertEquals(today.plusDays(9), nextRevisionDueIfCompleted(existing, StudyActivityType.LEARN, today))
        assertEquals(today.plusDays(6), nextRevisionDueIfCompleted(null, StudyActivityType.REVISION, today))
        assertEquals(today.plusDays(14), nextRevisionDueIfCompleted(existing, StudyActivityType.REVISION, today))
        assertEquals(
            nextRevisionDueIfCompleted(existing, StudyActivityType.REVISION, today),
            completeStudySession(session(StudyActivityType.REVISION), existing, today, 1).revision?.nextDueDate,
        )
    }

    private fun session(type: StudyActivityType) = StudySession(
        id = "s",
        attemptId = "attempt",
        nodeId = "node",
        subjectId = "physics",
        planBlockId = null,
        activityType = type,
        startedAtEpochMs = 0,
        endedAtEpochMs = null,
        studyDate = today,
        timeZoneId = "UTC",
        focusedSeconds = 1_500,
        pausedSeconds = 0,
        status = StudySessionStatus.RUNNING,
        focusLockUsed = false,
        interruptionCount = 0,
        customTitle = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )
}
