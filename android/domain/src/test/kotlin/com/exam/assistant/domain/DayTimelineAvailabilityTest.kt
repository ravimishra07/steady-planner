package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayTimelineAvailabilityTest {

    private val today = LocalDate.of(2026, 9, 1)
    private val coaching = FixedCommitmentBlock("coaching", "coaching", 16 * 60, 19 * 60)

    @Test
    fun `fixed commitments appear and are removed from free gaps`() {
        val entries = buildDayTimeline(
            sessions = emptyList(),
            sections = emptyList(),
            pendingRevisions = emptyList(),
            date = today.plusDays(1),
            today = today,
            nowMinuteOfDay = 12 * 60,
            fixedCommitments = listOf(coaching),
            dayStartMinute = 6 * 60,
            dayEndMinute = 23 * 60,
        )

        assertTrue(entries.any { it == DayTimelineEntry.Fixed(coaching) })
        assertEquals(
            listOf(6 * 60 to 16 * 60, 19 * 60 to 23 * 60),
            entries.filterIsInstance<DayTimelineEntry.Gap>().map { it.startMinuteOfDay to it.endMinuteOfDay },
        )
    }

    @Test
    fun `next free slot never lands inside a fixed commitment`() {
        assertEquals(
            19 * 60,
            findNextFreeSlot(
                sessions = emptyList(),
                date = today,
                minMinutes = 60,
                notBefore = 16 * 60,
                fixedCommitments = listOf(coaching),
                dayStartMinute = 6 * 60,
                dayEndMinute = 23 * 60,
            ),
        )
    }

    @Test
    fun `auto revision uses a free window around fixed commitments`() {
        val entries = buildDayTimeline(
            sessions = emptyList(),
            sections = emptyList(),
            pendingRevisions = listOf(
                RevisionSuggestion("node", "Topic", "Quant", "quant", today.minusDays(3)),
            ),
            date = today,
            today = today,
            nowMinuteOfDay = 17 * 60,
            fixedCommitments = listOf(coaching),
            dayStartMinute = 6 * 60,
            dayEndMinute = 23 * 60,
        )

        val revision = entries.filterIsInstance<DayTimelineEntry.Study>().single().block
        assertEquals(19 * 60, revision.startMinuteOfDay)
    }
}
