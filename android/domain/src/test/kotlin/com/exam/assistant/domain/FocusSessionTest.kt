package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTest {
    @Test
    fun `running time is derived from deadline and rounded up`() {
        val session = runningSession(endsAtMs = 10_500L)

        assertEquals(2, session.remainingSecondsAt(nowMs = 9_000L))
        assertEquals(1, session.remainingSecondsAt(nowMs = 10_499L))
        assertFalse(session.isExpiredAt(nowMs = 10_499L))
    }

    @Test
    fun `deadline is the exact expiry boundary`() {
        val session = runningSession(endsAtMs = 10_500L)

        assertTrue(session.isExpiredAt(nowMs = 10_500L))
        assertEquals(0, session.remainingSecondsAt(nowMs = 10_500L))
        assertEquals(FocusStatus.DONE, session.withClockAt(nowMs = 10_500L).status)
    }

    @Test
    fun `focused time on stop comes from deadline rather than stale stored seconds`() {
        val session = runningSession(
            durationSec = 300,
            remainingSec = 300,
            endsAtMs = 400_000L,
        )

        assertEquals(120, session.focusedSecondsAt(nowMs = 220_000L))
    }

    @Test
    fun `paused time remains stable without a deadline`() {
        val session = FocusSession(
            status = FocusStatus.PAUSED,
            durationSec = 300,
            remainingSec = 175,
            endsAtMs = null,
        )

        assertEquals(175, session.remainingSecondsAt(nowMs = 999_999L))
        assertEquals(125, session.focusedSecondsAt(nowMs = 999_999L))
        assertFalse(session.isExpiredAt(nowMs = 999_999L))
    }

    private fun runningSession(
        durationSec: Int = 60,
        remainingSec: Int = durationSec,
        endsAtMs: Long,
    ) = FocusSession(
        status = FocusStatus.RUNNING,
        durationSec = durationSec,
        remainingSec = remainingSec,
        endsAtMs = endsAtMs,
    )
}
