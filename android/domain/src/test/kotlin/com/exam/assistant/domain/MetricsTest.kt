package com.exam.assistant.domain

import com.exam.assistant.domain.MetricsFixture.nowMinute
import com.exam.assistant.domain.MetricsFixture.today
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {
    private val pack = MetricsFixture.pack
    private val syllabus = syllabusMetrics(pack, MetricsFixture.overrides, MetricsFixture.topicProgress)

    // --- coverage -----------------------------------------------------------

    @Test
    fun coverageRoundsDownAndIgnoresCoveredIdsOutsideTheTarget() {
        // 6 target leaves, 3 covered inside it. p3a is covered but its chapter is excluded.
        assertEquals(Coverage(3, 6), syllabus.overall)
        assertEquals(50, syllabus.overall.percent)
        assertEquals(66, syllabus.bySubject.getValue("physics").percent) // 2 of 3, not 67
        assertEquals(33, syllabus.bySubject.getValue("botany").percent)
        assertFalse("p3a" in syllabus.targetLeafIds)
    }

    @Test
    fun coverageOfAnEmptyTargetIsZeroNotAnError() {
        assertEquals(0, coverage(emptySet(), setOf("a")).percent)
    }

    @Test
    fun onlyCoveredStatusCountsAsCovered() {
        assertEquals(setOf("p1a", "p2a", "b1a", "p3a"), coveredNodeIds(MetricsFixture.topicProgress))
    }

    // --- remaining work -----------------------------------------------------

    @Test
    fun remainingMinutesSumsUncoveredTargetLeavesOnly() {
        // p1b 60 + b1b 90 + b2a 60. Not p3b: excluded.
        assertEquals(210, syllabus.remainingMinutes)
        assertEquals(480, syllabus.targetMinutes)
        assertEquals(60, syllabus.remainingMinutesBySubject.getValue("physics"))
        assertEquals(150, syllabus.remainingMinutesBySubject.getValue("botany"))
    }

    @Test
    fun calibrationFactorScalesRemainingWork() {
        assertEquals(315, remainingMinutes(pack, syllabus.targetLeafIds, syllabus.coveredIds, calibrationFactor = 1.5))
    }

    @Test
    fun hoursAreFormattedAfterSummingMinutes() {
        // Three chapters of 100 minutes: truncating each to 1h would say 3h; summed it is 300 min = 5h.
        assertEquals(5, minutesToHours(300))
        assertEquals(2, minutesToHours(100))
        assertEquals(2, minutesToHours(90))
        assertEquals(1, minutesToHours(89))
    }

    // --- studied time -------------------------------------------------------

    @Test
    fun studiedTimeIsFocusedSecondsOfCompletedAndPartialSessionsOnly() {
        val sessions = MetricsFixture.sessions
        // 3000 + 630 + 1200 + 900 s. Abandoned and running sessions do not count.
        assertEquals(5_730, studiedSeconds(sessions))
        assertEquals(95, studiedMinutes(sessions))
        assertEquals(10, studiedMinutesByDate(sessions).getValue(today)) // 10.5 min, whole minutes
        assertEquals(10, studiedMinutes(sessions, from = today, to = today))
    }

    // --- plan adherence -----------------------------------------------------

    @Test
    fun adherenceOnlyCountsBlocksWhoseEndTimePassed() {
        val result = planAdherence(MetricsFixture.planBlocks, MetricsFixture.sessions, today.minusDays(6), today, today, nowMinute)

        // b1 (50, done in full) + b2 (50 planned, 10 focused). b3 ends 20:50 > 15:00, b4 was rescheduled.
        assertEquals(PlanAdherence(plannedMinutes = 100, completedMinutes = 60), result)
        assertEquals(60, result.percent)
    }

    @Test
    fun adherenceIsNotBehindMidDay() {
        val result = planAdherence(MetricsFixture.planBlocks, emptyList(), today, today, today, nowMinuteOfDay = 8 * 60)

        assertEquals(PlanAdherence(0, 0), result)
        assertNull(result.percent)
    }

    @Test
    fun adherenceCreditsACompletedBlockWithNoSessionRecordInFull() {
        val result = planAdherence(MetricsFixture.planBlocks, emptyList(), today.minusDays(1), today.minusDays(1), today, nowMinute)

        assertEquals(PlanAdherence(50, 50), result)
    }

    // --- pace, forecast, required pace -------------------------------------

    private fun studyDays(count: Int, type: StudyActivityType = StudyActivityType.LEARN) =
        (0 until count).map { MetricsFixture.session("d$it", daysAgo = it, seconds = 3_600, type = type) }

    @Test
    fun paceNeedsSevenActiveDaysInTheLastFourteen() {
        assertNull(pace(studyDays(6), today))
        val result = pace(studyDays(7), today)!!
        assertEquals(7, result.activeDays)
        assertEquals(30.0, result.minutesPerDay, 0.0001) // 7 x 60 / 14
    }

    @Test
    fun learnPaceCountsLearnMinutesOnlyButTheGateCountsAnyStudy() {
        val sessions = studyDays(4) + studyDays(7, StudyActivityType.REVISION).drop(4).map { it.copy(id = "r${it.id}") }
        // 4 learn days + 3 revision-only days = 7 active days, 4 hours of learning.
        val learn = pace(sessions, today, learnOnly = true)!!
        assertEquals(4 * 60 / 14.0, learn.minutesPerDay, 0.0001)
        assertNull(pace(sessions.take(4), today, learnOnly = true))
    }

    @Test
    fun forecastRangeIsPlusMinusFifteenPercentOfPace() {
        val result = finishForecast(remainingMinutes = 3_000, learnPace = Pace(100.0, 10), today = today)!!

        assertEquals(today.plusDays(30), result.expected)
        assertEquals(today.plusDays(27), result.earliest) // ceil(3000 / 115)
        assertEquals(today.plusDays(36), result.latest) // ceil(3000 / 85)
    }

    @Test
    fun forecastIsAbsentWithoutPace() {
        assertNull(finishForecast(3_000, null, today))
        assertNull(finishForecast(3_000, Pace(0.0, 7), today))
        assertEquals(today, finishForecast(0, Pace(10.0, 7), today)!!.expected)
    }

    @Test
    fun availableTimeFollowsPerDateWindowsOverridesAndDailyTargets() {
        val weekly = listOf(
            WeeklyAvailability("m", "a", DayOfWeek.MONDAY, 9 * 60, 12 * 60), // 3h
            WeeklyAvailability("t", "a", DayOfWeek.TUESDAY, 9 * 60, 15 * 60), // 6h, capped by a 4h target
        )
        val monday = LocalDate.of(2026, 9, 7)
        val overrides = listOf(AvailabilityOverride("o", "a", monday.plusDays(7), 9 * 60, 10 * 60)) // next Monday: 1h

        val result = availableStudyTime(monday, monday.plusDays(13), weekly, overrides, 4 * 60, 7 * 60)

        // Mon 3h + Tue 4h, then Mon override 1h + Tue 4h. The other ten calendar days have no window.
        assertEquals(AvailableStudyTime(minutes = 12 * 60, days = 4), result)
    }

    @Test
    fun requiredPaceDividesByDaysWithStudyTimeNotCalendarDays() {
        val required = requiredPace(600, AvailableStudyTime(minutes = 1_200, days = 4))

        assertEquals(150, required.minutesPerStudyDay)
        assertEquals(0.5, required.load!!, 0.0001)
        assertTrue(required.fits)
        assertFalse(requiredPace(1_300, AvailableStudyTime(1_200, 4)).fits)
        assertNull(requiredPace(10, AvailableStudyTime(0, 0)).minutesPerStudyDay)
        assertEquals(0, requiredPace(0, AvailableStudyTime(0, 0)).minutesPerStudyDay)
    }

    // --- subject status -----------------------------------------------------

    @Test
    fun subjectStatusIsHiddenForTheFirstSevenDaysThenUsesPlusMinusFive() {
        val start = today.minusDays(50)
        val target = today.plusDays(50) // half way: 50% expected
        assertNull(subjectStatus(90, today.minusDays(6), today.plusDays(100), today))
        assertEquals(SubjectStatus.AHEAD, subjectStatus(55, start, target, today))
        assertEquals(SubjectStatus.ON_TRACK, subjectStatus(54, start, target, today))
        assertEquals(SubjectStatus.ON_TRACK, subjectStatus(46, start, target, today))
        assertEquals(SubjectStatus.BEHIND, subjectStatus(45, start, target, today))
    }

    // --- streak and dates ---------------------------------------------------

    @Test
    fun streakCountsCompletedDaysAndSurvivesAnUnstartedToday() {
        val dates = studyDates(MetricsFixture.sessions)
        assertEquals(3, currentStreak(dates, today)) // today, -1, -2 (-3 is empty)
        assertEquals(3, longestStreak(dates))
        assertEquals(2, currentStreak(setOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, currentStreak(setOf(today.minusDays(2)), today))
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun daysToTargetNeverGoesNegative() {
        assertEquals(30, daysToTarget(today.plusDays(30), today))
        assertEquals(0, daysToTarget(today.minusDays(3), today))
    }

    // --- revision -----------------------------------------------------------

    @Test
    fun overdueRevisionsStayInTheDueSet() {
        val states = MetricsFixture.revisionStates

        assertEquals(listOf("p1a", "p2a"), revisionsDueNow(states, today).map { it.nodeId }) // most overdue first
        assertEquals(listOf("p1a"), revisionsOverdue(states, today).map { it.nodeId })
        assertEquals(listOf("p1a", "p2a", "b1a"), revisionsDueThisWeek(states, today).map { it.nodeId })
        assertTrue(revisionsDueNow(states.map { it.copy(enabled = false) }, today).isEmpty())
    }

    @Test
    fun revisionOnTimeIsOnTimeCompletionsOverDueInThePeriod() {
        val result = revisionOnTime(
            MetricsFixture.sessions, MetricsFixture.revisionStates, today.minusDays(6), today, today,
        )

        // s4 on time; s3 late; p1a still pending and overdue.
        assertEquals(RevisionOnTime(onTime = 1, due = 3), result)
        assertEquals(33, result.percent)
    }

    @Test
    fun revisionOnTimeIgnoresCompletionsWithoutARecordedDueDateAndEmptyPeriods() {
        val legacy = MetricsFixture.session("old", 1, 600, type = StudyActivityType.REVISION)
        assertNull(revisionOnTime(listOf(legacy), emptyList(), today.minusDays(6), today, today).percent)
        assertEquals(2, revisionsCompleted(MetricsFixture.sessions, today.minusDays(6), today))
    }
}
