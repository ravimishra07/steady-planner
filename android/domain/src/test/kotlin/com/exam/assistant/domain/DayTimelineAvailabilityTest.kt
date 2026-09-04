package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `placement rejects collisions and midnight overflow`() {
        val session = StudySessionRecord(
            id = "existing",
            date = today,
            startMinuteOfDay = 10 * 60,
            durationMinutes = 60,
            nodeKey = "node",
            title = "Topic",
            sectionName = "Physics",
            subjectId = "physics",
        )

        assertEquals(
            StudyPlacementIssue.OVERLAPS_STUDY,
            validateStudyPlacement(listOf(session), today, 10 * 60 + 30, 30),
        )
        assertEquals(
            StudyPlacementIssue.OVERLAPS_FIXED,
            validateStudyPlacement(emptyList(), today, 16 * 60, 30, listOf(coaching)),
        )
        assertEquals(
            StudyPlacementIssue.OUTSIDE_DAY,
            validateStudyPlacement(emptyList(), today, 22 * 60 + 45, 30),
        )
        assertNull(validateStudyPlacement(listOf(session), today, 11 * 60, 30))
    }

    @Test
    fun `recovery preserves future work and moves missed sessions in order`() {
        fun session(id: String, start: Int, duration: Int) = StudySessionRecord(
            id = id,
            date = today,
            startMinuteOfDay = start,
            durationMinutes = duration,
            nodeKey = id,
            title = id,
            sectionName = "Physics",
            subjectId = "physics",
        )
        val sessions = listOf(
            session("missed-1", 6 * 60, 50),
            session("missed-2", 7 * 60, 50),
            session("future", 20 * 60, 60),
        )

        val moves = planMissedDayRecovery(
            sessions = sessions,
            date = today,
            nowMinuteOfDay = 19 * 60,
            fixedCommitmentsByDate = mapOf(today to listOf(coaching)),
        )

        assertEquals(SessionPlacement("missed-1", today, 19 * 60), moves[0])
        assertEquals(SessionPlacement("missed-2", today, 21 * 60), moves[1])
    }

    @Test
    fun `only gaps of thirty minutes are actionable`() {
        assertTrue(isActionableStudyGap(30))
        assertEquals(false, isActionableStudyGap(29))
    }
}
