package com.exam.assistant.domain

import com.exam.assistant.domain.MetricsFixture.nowMinute
import com.exam.assistant.domain.MetricsFixture.today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyAnalyticsTest {
    private val syllabus = syllabusMetrics(MetricsFixture.pack, MetricsFixture.overrides, MetricsFixture.topicProgress)

    private fun insights(
        period: InsightPeriod,
        sessions: List<StudySession> = MetricsFixture.sessions,
    ) = computeInsights(
        pack = MetricsFixture.pack,
        syllabus = syllabus,
        sessions = sessions,
        planBlocks = MetricsFixture.planBlocks,
        revisionStates = MetricsFixture.revisionStates,
        plan = MetricsFixture.plan,
        period = period,
        today = today,
        nowMinuteOfDay = nowMinute,
    )

    @Test
    fun weekUsesCompletedAndPartialSessionsAndTheActiveDayAverage() {
        val result = insights(InsightPeriod.WEEK)

        assertEquals(95, result.totalStudiedMinutes)
        assertEquals(4, result.completedSessionCount)
        assertEquals(4, result.studyDaysInPeriod)
        assertEquals(23, result.averagePerActiveDayMinutes) // 95 / 4
        assertEquals(84, result.heatmapDays.size)
        assertEquals(today, result.heatmapDays.last().date)
        assertTrue(result.hasStudyHistory)
    }

    @Test
    fun dayPeriodOnlyContainsToday() {
        val result = insights(InsightPeriod.DAY)

        assertEquals(10, result.totalStudiedMinutes)
        assertEquals(1, result.completedSessionCount)
    }

    @Test
    fun allPeriodStartsAtTheFirstStudyDay() {
        assertEquals(today.minusDays(4), insights(InsightPeriod.ALL).periodStart)
    }

    @Test
    fun planFigureUsesBlocksThatHaveEndedAndExtraStudyIsTheRest() {
        val result = insights(InsightPeriod.WEEK)

        assertEquals(100, result.plannedMinutes)
        assertEquals(60, result.completedPlannedMinutes)
        assertEquals(60, result.planPercent)
        assertEquals(-40, result.planDeltaMinutes)
        // 95 studied - 60 on planned blocks (s1 50 + s2 10).
        assertEquals(35, result.extraStudyMinutes)
    }

    @Test
    fun syllabusFiguresUseTheTargetDenominatorAndRealSubjectIds() {
        val result = insights(InsightPeriod.WEEK)

        assertEquals(6, result.syllabusTotalSections)
        assertEquals(3, result.syllabusCompletedSections)
        assertEquals(50, result.syllabusPercent)
        assertEquals(8, result.officialSectionCount)
        assertEquals(2, result.excludedSectionCount)
        assertEquals(listOf("physics", "botany"), result.subjects.map { it.subjectId })
        assertEquals(210, result.remainingMinutes)
    }

    @Test
    fun aFullyExcludedSubjectLeavesTheSubjectList() {
        val noPhysics = syllabusMetrics(
            MetricsFixture.pack,
            mapOf("P1" to TargetNodeState.EXCLUDED, "P2" to TargetNodeState.EXCLUDED, "P3" to TargetNodeState.EXCLUDED),
            MetricsFixture.topicProgress,
        )
        val result = computeInsights(
            MetricsFixture.pack, noPhysics, MetricsFixture.sessions, MetricsFixture.planBlocks,
            MetricsFixture.revisionStates, MetricsFixture.plan, InsightPeriod.WEEK, today, nowMinute,
        )

        assertEquals(listOf("botany"), result.subjects.map { it.subjectId })
    }

    @Test
    fun forecastNeedsSevenActiveDaysBeforeClaimingPace() {
        val result = insights(InsightPeriod.WEEK).forecast

        assertFalse(result.hasReliablePace)
        assertNull(result.forecastDate)
        assertNull(result.recentAverageMinutesPerDay)
        assertEquals(4, result.evidenceActiveDays)
    }

    @Test
    fun forecastAndRequiredPaceUseTheSameRemainingWork() {
        val sessions = (0 until 7).map { MetricsFixture.session("d$it", daysAgo = it, seconds = 3_600) }
        val result = insights(InsightPeriod.WEEK, sessions).forecast

        // 210 min remaining at 30 min/day (7 x 60 / 14).
        assertEquals(today.plusDays(7), result.forecastDate)
        assertEquals(30, result.recentAverageMinutesPerDay)
        // 4h/day windows, 4h targets, 61 days: 210 / 61 days, rounded up.
        assertEquals(4, result.requiredMinutesPerDay)
        assertEquals(0, result.extraMinutesPerDay)
    }

    @Test
    fun revisionInsightsComeFromRevisionState() {
        val revision = insights(InsightPeriod.WEEK).revision

        assertEquals(2, revision.dueNow)
        assertEquals(1, revision.overdue)
        assertEquals(3, revision.dueThisWeek)
        assertEquals(2, revision.completedThisWeek)
        assertEquals(33, revision.onTimePercent)
        assertEquals(listOf("p1a", "p2a"), revision.dueTitles)
    }

    @Test
    fun subjectStatusIsHiddenInTheFirstSevenDays() {
        val fresh = computeInsights(
            MetricsFixture.pack, syllabus, MetricsFixture.sessions, MetricsFixture.planBlocks, MetricsFixture.revisionStates,
            MetricsFixture.plan.copy(startedOn = today.minusDays(2)), InsightPeriod.WEEK, today, nowMinute,
        )

        assertTrue(fresh.subjects.all { it.status == null })
        assertTrue(insights(InsightPeriod.WEEK).subjects.all { it.status != null })
    }

    @Test
    fun noHistoryMeansNoStudyHistory() {
        assertFalse(insights(InsightPeriod.WEEK, sessions = emptyList()).hasStudyHistory)
    }
}
