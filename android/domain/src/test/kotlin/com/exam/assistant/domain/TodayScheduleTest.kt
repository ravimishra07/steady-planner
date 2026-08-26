package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayScheduleTest {

    @Test
    fun `buildTimeline inserts gaps of at least forty five minutes`() {
        val items = buildTimeline(demoTodayBlocks())
        assertTrue(items.any { it is TimelineItem.Gap })
        val gap = items.filterIsInstance<TimelineItem.Gap>().first()
        assertEquals(270, gap.minutes)
    }

    @Test
    fun `doneMinutes counts only completed study blocks`() {
        val minutes = doneMinutes(demoTodayBlocks(), setOf("0", "2"))
        assertEquals(165, minutes)
    }

    @Test
    fun `weekAround starts on Sunday`() {
        val anchor = java.time.LocalDate.of(2026, 8, 26) // Wednesday
        val week = weekAround(anchor)
        assertEquals(java.time.DayOfWeek.SUNDAY, week.first().dayOfWeek)
        assertEquals(7, week.size)
    }
}
