package com.steadyline.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    /** 634 raw hours is the SSC CGL Tier-1 total in syllabus_cgl.json. */
    @Test
    fun `matches the web prototype for SSC CGL at 4h and 7h over 118 days`() {
        val c = cushion(rawHours = 634.0, days = 118, weekdayHours = 4.0, weekendHours = 7.0)
        assertEquals(812, c.need)
        assertEquals(568, c.have)
        assertEquals(244, c.gap)
        assertEquals(70, c.coverage)
        assertTrue(c.isShort)
        assertEquals(2.1, c.extraPerDay, 0.001)
        assertEquals(18, c.topicsToDrop)
        assertEquals(45, c.daysToPush)
    }

    @Test
    fun `reports a buffer when the calendar supplies more than the syllabus needs`() {
        val c = cushion(rawHours = 634.0, days = 200, weekdayHours = 8.0, weekendHours = 8.0)
        assertTrue(!c.isShort)
        assertTrue(c.gap < 0)
        assertEquals(0.0, c.extraPerDay, 0.001)
        assertTrue(c.bufferDays > 0)
    }

    @Test
    fun `leftover days count as weekdays`() {
        val week = availableHours(days = 7, weekdayHours = 4.0, weekendHours = 7.0)
        val plusOne = availableHours(days = 8, weekdayHours = 4.0, weekendHours = 7.0)
        assertEquals(5 * 4 + 2 * 7, week)
        assertEquals(4, plusOne - week)
    }

    @Test
    fun `seven hours across three children is 2_5 plus 2_5 plus 2`() {
        assertEquals(listOf(2.5, 2.5, 2.0), splitHours(7.0, 3))
    }

    @Test
    fun `split parts always sum back to the parent`() {
        val totals = listOf(1.0, 2.0, 5.0, 7.0, 9.0, 14.0, 22.0, 26.0, 40.0)
        for (total in totals) {
            for (n in 1..9) {
                val parts = splitHours(total, n)
                assertEquals(n, parts.size)
                assertEquals("total $total over $n children", total, parts.sum(), 0.001)
            }
        }
    }

    @Test
    fun `a count of zero is empty rather than a crash`() {
        assertTrue(splitHours(7.0, 0).isEmpty())
    }
}
