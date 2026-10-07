package com.exam.assistant.domain

import com.exam.assistant.domain.PlannerFixture.attemptId
import com.exam.assistant.domain.PlannerFixture.everyDay
import com.exam.assistant.domain.PlannerFixture.longAgoMs
import com.exam.assistant.domain.PlannerFixture.pack
import com.exam.assistant.domain.PlannerFixture.start
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRODUCTION_V1_PLAN §3.1: steady mode projects a finish date and never has a behind or short state. */
class SteadyModeTest {
    private fun steady(exam: LocalDate? = start.plusDays(200), weekday: Int = 240) = PlanInputs(
        attemptId = attemptId,
        pack = pack(),
        startDate = start,
        targetDate = start.plusDays(30),
        weeklyAvailability = everyDay(),
        weekdayTargetMinutes = weekday,
        weekendTargetMinutes = weekday,
        mode = PlanMode.STEADY,
        examDate = exam,
        zone = ZoneOffset.UTC,
        includedLeafIds = pack().leafIds().toSet(),
    )

    @Test
    fun `the projected finish is the day the last learn or practice block lands`() {
        val inputs = steady()
        val projected = inputs.projectedFinish(longAgoMs)
        val whole = inputs.plan(horizonDays = 60, nowMs = longAgoMs)
        val lastWork = whole.blocks
            .filter { it.activityType == StudyActivityType.LEARN || it.activityType == StudyActivityType.PRACTICE }
            .maxOf { it.scheduledDate }

        assertNotNull(projected)
        assertEquals(lastWork, projected)
    }

    @Test
    fun `more hours a day finishes earlier`() {
        val slow = steady(weekday = 120).projectedFinish(longAgoMs)!!
        val fast = steady(weekday = 480).projectedFinish(longAgoMs)!!
        assertTrue(fast < slow)
    }

    @Test
    fun `a plan that cannot end before the exam has no projected finish rather than a failure state`() {
        val inputs = steady(exam = start.plusDays(2))
        assertNull(inputs.projectedFinish(longAgoMs))
        assertNull("steady mode never reports a fit", inputs.plan(horizonDays = 21, nowMs = longAgoMs).fit)
        assertNull(inputs.planFit(longAgoMs))
    }

    @Test
    fun `projecting emits nothing and does not change the emitted plan`() {
        val inputs = steady()
        val normal = inputs.plan(horizonDays = 21, nowMs = longAgoMs)
        val projecting = inputs.plan(horizonDays = 21, nowMs = longAgoMs, simulateToEnd = true)
        assertEquals(normal.blocks, projecting.blocks)
    }

    @Test
    fun `insights in steady mode have no subject status, no target and no days behind`() {
        val today = MetricsFixture.today
        val syllabus = syllabusMetrics(MetricsFixture.pack, MetricsFixture.overrides, MetricsFixture.topicProgress)
        val sessions = (0 until 8).map { MetricsFixture.session("d$it", daysAgo = it, seconds = 600) }
        val data = computeInsights(
            MetricsFixture.pack, syllabus, sessions, MetricsFixture.planBlocks, MetricsFixture.revisionStates,
            MetricsFixture.plan.copy(planMode = PlanMode.STEADY), InsightPeriod.WEEK, today, MetricsFixture.nowMinute,
        )

        assertTrue(data.subjects.all { it.status == null })
        assertFalse(data.forecast.hasTarget)
        assertNull(data.forecast.daysDelta)
        assertNull(data.forecast.requiredMinutesPerDay)
        assertEquals(0, data.forecast.extraMinutesPerDay)
        assertNotNull("a projected finish still shows", data.forecast.forecastDate)
    }

    @Test
    fun `deadline insights still carry a target and a status`() {
        val syllabus = syllabusMetrics(MetricsFixture.pack, MetricsFixture.overrides, MetricsFixture.topicProgress)
        val data = computeInsights(
            MetricsFixture.pack, syllabus, MetricsFixture.sessions, MetricsFixture.planBlocks, MetricsFixture.revisionStates,
            MetricsFixture.plan, InsightPeriod.WEEK, MetricsFixture.today, MetricsFixture.nowMinute,
        )
        assertTrue(data.forecast.hasTarget)
        assertTrue(data.subjects.all { it.status != null })
    }
}
