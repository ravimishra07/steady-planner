package com.exam.assistant.domain

import com.exam.assistant.domain.MetricsFixture.nowMinute
import com.exam.assistant.domain.MetricsFixture.today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PRODUCTION_V1_PLAN §9 acceptance: one fixture state, read the way each screen
 * reads it, and the same concept gives the same number. The syllabus-shaped
 * screens all go through [syllabusMetrics]; the rest through the functions named below.
 */
class CrossScreenConsistencyTest {
    private val pack = MetricsFixture.pack
    private val overrides = targetStates(
        MetricsFixture.overrides.map { (id, state) -> TargetNodeOverride(MetricsFixture.attemptId, id, state, 1L) },
    )

    // Each consumer builds its own SyllabusMetrics from raw repository output, as its ViewModel does.
    private val syllabusScreen = syllabusMetrics(pack, overrides, MetricsFixture.topicProgress)
    private val settingsRing = syllabusMetrics(pack, overrides, MetricsFixture.topicProgress)
    private val home = syllabusMetrics(pack, overrides, MetricsFixture.topicProgress)
    private val organise = syllabusMetrics(pack, overrides, MetricsFixture.topicProgress)
    private val insightsSyllabus = syllabusMetrics(pack, overrides, MetricsFixture.topicProgress)

    private val insights = computeInsights(
        pack, insightsSyllabus, MetricsFixture.sessions, MetricsFixture.planBlocks,
        MetricsFixture.revisionStates, MetricsFixture.plan, InsightPeriod.ALL, today, nowMinute,
    )

    @Test
    fun coveragePercentIsTheSameOnEveryScreen() {
        val expected = 50
        assertEquals(expected, syllabusScreen.overall.percent)
        assertEquals(expected, insights.syllabusPercent)
        assertEquals(expected, settingsRing.overall.percent.takeIf { settingsRing.overall.total > 0 })
        assertEquals(expected, home.overall.percent)
        assertEquals(expected, organise.overall.percent)
    }

    @Test
    fun subjectRingsMatchBetweenSyllabusAndInsights() {
        insights.subjects.forEach { subject ->
            val ring = syllabusScreen.bySubject.getValue(subject.subjectId)
            assertEquals(ring.percent, subject.percent)
            assertEquals(ring.covered, subject.completedSections)
            assertEquals(ring.total, subject.totalSections)
            assertEquals(ring.remaining, subject.remainingSections)
        }
        assertEquals(syllabusScreen.overall.covered, insights.subjects.sumOf { it.completedSections })
        assertEquals(syllabusScreen.overall.total, insights.subjects.sumOf { it.totalSections })
    }

    @Test
    fun remainingWorkIsTheSameInOrganiseInsightsAndTheForecast() {
        assertEquals(210, organise.remainingMinutes)
        assertEquals(organise.remainingMinutes, insights.remainingMinutes)
        assertEquals(syllabusScreen.remainingMinutes, syllabusScreen.remainingMinutesBySubject.values.sum())
        // Organise "hours left" is remaining, not the total included.
        assertEquals(480, organise.targetMinutes)
        assertEquals(minutesToHours(organise.remainingMinutes), minutesToHours(insights.remainingMinutes))
    }

    @Test
    fun studiedTimeIsFocusedTimeEverywhere() {
        val sessions = MetricsFixture.sessions
        // Home's "X of Y" for today, Insights' day bar and its DAY total all read the same focused seconds.
        val homeToday = studiedMinutesByDate(sessions).getValue(today)
        val insightsToday = insights.periodDays.single { it.date == today }.minutes
        val insightsDayPeriod = computeInsights(
            pack, insightsSyllabus, sessions, MetricsFixture.planBlocks, MetricsFixture.revisionStates,
            MetricsFixture.plan, InsightPeriod.DAY, today, nowMinute,
        )
        assertEquals(10, homeToday) // the partial save: 630 s, not the planned 50 minutes
        assertEquals(homeToday, insightsToday)
        assertEquals(homeToday, insightsDayPeriod.totalStudiedMinutes)
        assertEquals(studiedMinutes(sessions), insights.totalStudiedMinutes)
        assertEquals(insights.totalStudiedMinutes, insights.periodDays.sumOf { it.minutes })
        assertEquals(insights.totalStudiedMinutes, insights.heatmapDays.sumOf { it.minutes })
    }

    @Test
    fun revisionDueSetIsTheSameOnHomeAndInsights() {
        val homeDue = revisionsDueNow(MetricsFixture.revisionStates, today).map { it.nodeId }

        assertEquals(homeDue.size, insights.revision.dueNow)
        assertEquals(homeDue, insights.revision.dueTitles) // fixture titles are the node ids
        assertEquals(revisionsOverdue(MetricsFixture.revisionStates, today).size, insights.revision.overdue)
    }

    @Test
    fun streakAndDaysToTargetHaveOneDefinition() {
        assertEquals(currentStreak(studyDates(MetricsFixture.sessions), today), insights.currentStreak)
        assertEquals(longestStreak(studyDates(MetricsFixture.sessions)), insights.longestStreak)
        assertEquals(60, daysToTarget(MetricsFixture.plan.targetDate, today))
        assertEquals(today.plusDays(60), insights.forecast.targetDate)
    }

    @Test
    fun adherenceNeverCountsPlannedLengthAsStudied() {
        // The planned blocks total 100 ended minutes, the student focused 60 of them.
        assertEquals(100, insights.plannedMinutes)
        assertEquals(60, insights.completedPlannedMinutes)
        assertNull(computeInsights(
            pack, insightsSyllabus, emptyList(), emptyList(), emptyList(),
            MetricsFixture.plan, InsightPeriod.ALL, today, nowMinute,
        ).planPercent)
    }

    @Test
    fun calibrationScalesRemainingWorkTheSameEverywhereAndInsightsCarriesTheFit() {
        val calibration = Calibration(factor = 1.5, chapters = 4)
        val progress = MetricsFixture.topicProgress
        val organiseCalibrated = syllabusMetrics(pack, overrides, progress, calibration.factor)
        val insightsCalibrated = syllabusMetrics(pack, overrides, progress, calibration.factor)
        val fit = PlanFit(
            finishBy = today.plusDays(30), learnMinutes = 600, practiceMinutes = 150,
            shortByMinutes = 120, revisionWindowDays = 90, calibrated = true,
        )
        val data = computeInsights(
            pack, insightsCalibrated, MetricsFixture.sessions, MetricsFixture.planBlocks,
            MetricsFixture.revisionStates, MetricsFixture.plan, InsightPeriod.ALL, today, nowMinute,
            calibration = calibration, fit = fit,
        )

        assertEquals(315, organiseCalibrated.remainingMinutes)
        assertEquals(organiseCalibrated.remainingMinutes, data.remainingMinutes)
        assertEquals(calibration, data.calibration)
        assertEquals(fit, data.fit)
        assertEquals(fit.requiredMinutes - fit.shortByMinutes, fit.scheduledMinutes)
        // Percentages are counts of chapters, so calibration never moves them.
        assertEquals(syllabusScreen.overall.percent, data.syllabusPercent)
    }
}
