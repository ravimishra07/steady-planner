package com.exam.assistant.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnalyticsTest {

    private val today = LocalDate.of(2026, 8, 27)
    private val sections = listOf(
        SyllabusSection(
            name = "Quantitative Aptitude",
            questions = 25,
            topics = listOf(
                SyllabusTopicNode("Numbers", 2.0),
                SyllabusTopicNode("Algebra", 3.0),
            ),
        ),
        SyllabusSection(
            name = "English",
            questions = 25,
            topics = listOf(SyllabusTopicNode("Grammar", 4.0)),
        ),
    )

    @Test
    fun weekUsesCompletedSessionsAndActiveDayAverage() {
        val sessions = listOf(
            session("a", today.minusDays(1), 90, completed = true),
            session("b", today.minusDays(1), 30, completed = false),
            session("c", today, 60, completed = true),
        )

        val result = computeInsights(
            sessions = sessions,
            sections = sections,
            doneLeaves = emptySet(),
            plan = InsightPlan(30, 4f, 6f),
            period = InsightPeriod.WEEK,
            today = today,
        )

        assertEquals(150, result.totalStudiedMinutes)
        assertEquals(2, result.completedSessionCount)
        assertEquals(75, result.averagePerActiveDayMinutes)
        assertEquals(84, result.heatmapDays.size)
        assertTrue(result.hasStudyHistory)
    }

    @Test
    fun excludedSectionIsRemovedFromTargetDenominator() {
        val englishLeaf = "t1_1_0"
        val result = computeInsights(
            sessions = emptyList(),
            sections = sections,
            doneLeaves = setOf(englishLeaf),
            excludedSectionKeys = setOf("section_1"),
            plan = InsightPlan(30, 4f, 6f),
            period = InsightPeriod.WEEK,
            today = today,
        )

        assertEquals(2, result.syllabusTotalTopics)
        assertEquals(0, result.syllabusCompletedTopics)
        assertEquals(1, result.excludedTopicCount)
        assertEquals(3, result.officialTopicCount)
        assertEquals(1, result.subjects.size)
    }

    @Test
    fun forecastNeedsThreeActiveDaysBeforeClaimingPace() {
        val result = computeInsights(
            sessions = listOf(session("a", today, 60, completed = true)),
            sections = sections,
            doneLeaves = emptySet(),
            plan = InsightPlan(30, 4f, 6f),
            period = InsightPeriod.WEEK,
            today = today,
        )

        assertFalse(result.forecast.hasReliablePace)
        assertNull(result.forecast.forecastDate)
        assertNull(result.forecast.recentAverageMinutesPerDay)
    }

    private fun session(id: String, date: LocalDate, minutes: Int, completed: Boolean): StudySessionRecord =
        StudySessionRecord(
            id = id,
            date = date,
            startMinuteOfDay = 9 * 60,
            durationMinutes = minutes,
            nodeKey = "t1_0_0",
            title = "Topic",
            sectionName = "Quantitative Aptitude",
            subjectId = "quant",
            completed = completed,
        )
}
