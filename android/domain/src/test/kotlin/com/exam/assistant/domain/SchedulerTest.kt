package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

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
