package com.exam.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeedHistoryTest {

    private val sections = listOf(
        SyllabusSection(
            name = "Quantitative Aptitude",
            questions = 25,
            topics = listOf(
                SyllabusTopicNode(
                    name = "Number System",
                    hours = null,
                    children = listOf(
                        SyllabusTopicNode("Divisibility", hours = 2.0),
                        SyllabusTopicNode("LCM & HCF", hours = 2.0),
                    ),
                ),
                SyllabusTopicNode("Percentage", hours = 3.0),
            ),
        ),
    )

    @Test
    fun `no sessions land on today`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, _) = generateBackfillHistory(sections, today, days = 45, weekdayHours = 4f, weekendHours = 7f)
        assertTrue(sessions.none { it.date == today })
        assertTrue(sessions.all { it.date.isBefore(today) })
    }

    @Test
    fun `covers exactly the requested day range`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, _) = generateBackfillHistory(sections, today, days = 45, weekdayHours = 4f, weekendHours = 7f)
        val earliest = today.minusDays(45)
        assertTrue(sessions.all { !it.date.isBefore(earliest) })
        assertTrue(sessions.all { it.date.isBefore(today) })
    }

    @Test
    fun `every generated session is completed and 50 minutes`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, _) = generateBackfillHistory(sections, today, days = 10, weekdayHours = 4f, weekendHours = 7f)
        assertTrue(sessions.isNotEmpty())
        assertTrue(sessions.all { it.completed })
        assertTrue(sessions.all { it.durationMinutes == 50 })
        assertTrue(sessions.all { it.runningEndsAtMs == null })
    }

    @Test
    fun `marks touched leaves done`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, doneLeaves) = generateBackfillHistory(sections, today, days = 10, weekdayHours = 4f, weekendHours = 7f)
        assertTrue(doneLeaves.isNotEmpty())
        assertEquals(sessions.map { it.nodeKey }.toSet(), doneLeaves)
    }

    @Test
    fun `zero days produces nothing`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, doneLeaves) = generateBackfillHistory(sections, today, days = 0, weekdayHours = 4f, weekendHours = 7f)
        assertTrue(sessions.isEmpty())
        assertTrue(doneLeaves.isEmpty())
    }

    @Test
    fun `empty syllabus produces nothing`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, doneLeaves) = generateBackfillHistory(emptyList(), today, days = 45, weekdayHours = 4f, weekendHours = 7f)
        assertTrue(sessions.isEmpty())
        assertTrue(doneLeaves.isEmpty())
    }

    @Test
    fun `sessions within a day do not overlap`() {
        val today = LocalDate.of(2026, 3, 1)
        val (sessions, _) = generateBackfillHistory(sections, today, days = 5, weekdayHours = 6f, weekendHours = 8f)
        sessions.groupBy { it.date }.forEach { (_, daySessions) ->
            val sorted = daySessions.sortedBy { it.startMinuteOfDay }
            for (i in 0 until sorted.size - 1) {
                val end = sorted[i].startMinuteOfDay + sorted[i].durationMinutes
                assertFalse(end > sorted[i + 1].startMinuteOfDay)
            }
        }
    }

    @Test
    fun `demo history covers today plus fifteen preceding days`() {
        val today = LocalDate.of(2026, 3, 16)
        val (sessions, _) = generateDemoHistory(sections, today)

        val completedDates = sessions.filter { it.completed }.map { it.date }.toSet()
        assertEquals((0 until DEMO_HISTORY_DAYS).map { today.minusDays(it.toLong()) }.toSet(), completedDates)
        assertTrue(sessions.any { it.date == today && it.completed })
        assertEquals(2, sessions.count { it.date == today && !it.completed })
    }

    @Test
    fun `demo history is idempotent through stable session ids`() {
        val today = LocalDate.of(2026, 3, 16)
        val (first, _) = generateDemoHistory(sections, today)
        val (second, _) = generateDemoHistory(sections, today)

        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
