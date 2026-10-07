package com.exam.assistant.domain

import com.exam.assistant.domain.PlannerFixture.attemptId
import com.exam.assistant.domain.PlannerFixture.everyDay
import com.exam.assistant.domain.PlannerFixture.longAgoMs
import com.exam.assistant.domain.PlannerFixture.pack
import com.exam.assistant.domain.PlannerFixture.start
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {

    private fun inputs(
        pack: ExamPack = pack(),
        target: LocalDate = start.plusDays(30),
        mode: PlanMode = PlanMode.DEADLINE,
        exam: LocalDate? = null,
        availability: List<WeeklyAvailability> = everyDay(),
        weekday: Int = 240,
        weekend: Int = 240,
        order: PlanningOrder = PlanningOrder.DEFAULT,
        priorities: Map<String, ChapterPriority> = emptyMap(),
        revisions: List<RevisionState> = emptyList(),
        weak: Map<String, Int> = emptyMap(),
        allowLearn: Boolean = false,
        calibration: Double = 1.0,
        preserved: List<StudyPlanBlock> = emptyList(),
        covered: Set<String> = emptySet(),
        recentMocks: List<LocalDate> = emptyList(),
        from: LocalDate = start,
    ) = PlanInputs(
        attemptId = attemptId,
        pack = pack,
        startDate = from,
        targetDate = target,
        weeklyAvailability = availability,
        coveredLeafIds = covered,
        preservedBlocks = preserved,
        weekdayTargetMinutes = weekday,
        weekendTargetMinutes = weekend,
        planningOrder = order,
        mode = mode,
        examDate = exam,
        allowLearnInRevisionPhase = allowLearn,
        calibrationFactor = calibration,
        priorities = priorities,
        revisionStates = revisions,
        weakNodeCounts = weak,
        recentMockDates = recentMocks,
        zone = ZoneOffset.UTC,
        includedLeafIds = pack.leafIds().toSet(),
    )

    private fun PlanInputs.run(horizon: Int = 21, nowMs: Long = longAgoMs) = plan(horizon, nowMs)

    private fun priority(id: String, band: PriorityBand, mean: Double) = ChapterPriority(id, band, mean, 5, listOf("PW"))

    // --- modes ---------------------------------------------------------------

    @Test
    fun `deadline mode stops at finish-by and reports a fit but steady mode keeps going and never does`() {
        val deadline = inputs(target = start.plusDays(1)).run()
        val steady = inputs(target = start.plusDays(1), mode = PlanMode.STEADY).run()

        assertEquals(start.plusDays(1), deadline.blocks.maxOf { it.scheduledDate })
        assertTrue(steady.blocks.maxOf { it.scheduledDate } > start.plusDays(1))
        assertNotNull(deadline.fit)
        assertFalse(deadline.fit!!.fits)
        assertNull("steady mode has no short state", steady.fit)
    }

    @Test
    fun `steady mode interleaves learn practice and revise with no phase switch`() {
        val result = inputs(
            mode = PlanMode.STEADY,
            revisions = listOf(PlannerFixture.revision("P1a", start.minusDays(1))),
        ).run()
        val types = result.blocks.map { it.activityType }.toSet()
        assertTrue(StudyActivityType.LEARN in types)
        assertTrue(StudyActivityType.PRACTICE in types)
        assertTrue(StudyActivityType.REVISION in types)
        assertFalse(StudyActivityType.MOCK_TEST in types)
    }

    @Test
    fun `existing students default to deadline mode`() {
        val attempt = ExamAttempt(
            "a", NEET_EXAM_ID, "v", start.plusDays(90), start.plusDays(30), 0, 0, ExamAttemptStatus.ACTIVE,
        )
        assertEquals(PlanMode.DEADLINE, attempt.planMode)
        assertFalse(StudyPreferences(attemptId = "a").allowLearnInRevisionPhase)
    }

    // --- block types ---------------------------------------------------------

    @Test
    fun `a 21 day plan contains learn practice and revision blocks`() {
        val result = inputs(revisions = listOf(PlannerFixture.revision("P1a", start.minusDays(2)))).run()
        val byType = result.blocks.groupBy { it.activityType }

        assertTrue(byType.containsKey(StudyActivityType.LEARN))
        assertTrue(byType.containsKey(StudyActivityType.PRACTICE))
        val revision = byType.getValue(StudyActivityType.REVISION).single()
        assertEquals("P1a", revision.nodeId)
        assertEquals(PlanBlockSource.REVISION_ENGINE, revision.source)
        assertTrue(revision.id.startsWith(ENGINE_REVISION_ID_PREFIX))
        assertEquals(REVISION_BLOCK_MINUTES, revision.plannedMinutes)
        assertTrue(revision.isRegenerable())
    }

    @Test
    fun `practice follows a chapter's learn work and is 25 percent of its estimate`() {
        val result = inputs().run()
        for (chapter in listOf("P1", "P2", "P3", "C1", "C2")) {
            val learnEnd = result.blocks.filter { it.activityType == StudyActivityType.LEARN && it.nodeId!!.startsWith(chapter) }
                .maxOf { it.scheduledDate.toEpochDay() * 1440 + it.startMinuteOfDay + it.plannedMinutes }
            val practice = result.blocks.single { it.activityType == StudyActivityType.PRACTICE && it.nodeId == chapter }
            assertTrue(chapter, practice.scheduledDate.toEpochDay() * 1440 + practice.startMinuteOfDay >= learnEnd)
            assertEquals(chapter, 30, practice.plannedMinutes)
        }
    }

    @Test
    fun `practice length is clamped between 20 and 90 minutes and follows calibration`() {
        fun practiceFor(sectionMinutes: Int, calibration: Double = 1.0) = inputs(
            pack = pack(physicsChapters = 1, chemistryChapters = 0, sectionMinutes = sectionMinutes),
            calibration = calibration,
        ).run().blocks.filter { it.activityType == StudyActivityType.PRACTICE }.sumOf { it.plannedMinutes }

        assertEquals("min 20", 20, practiceFor(sectionMinutes = 20))
        assertEquals("25% of 120", 30, practiceFor(sectionMinutes = 60))
        assertEquals("max 90", 90, practiceFor(sectionMinutes = 400))
        assertEquals("25% of 120 x 2.0", 60, practiceFor(sectionMinutes = 60, calibration = 2.0))
    }

    @Test
    fun `calibration scales the learn work`() {
        val base = inputs(pack = pack(1, 0)).run().minutes(StudyActivityType.LEARN)
        val slow = inputs(pack = pack(1, 0), calibration = 1.5).run().minutes(StudyActivityType.LEARN)
        assertEquals(120, base)
        assertEquals(180, slow)
    }

    // --- revision engine -----------------------------------------------------

    @Test
    fun `revision blocks take at most 40 percent of a day and overflow to the next day most overdue first`() {
        val due = listOf("P1a", "P1b", "P2a", "P2b", "P3a").mapIndexed { i, id ->
            PlannerFixture.revision(id, start.minusDays((10 - i).toLong()))
        }
        val result = inputs(weekday = 100, weekend = 100, revisions = due.shuffled()).run()

        val revisions = result.blocks.filter { it.activityType == StudyActivityType.REVISION }
            .sortedWith(compareBy({ it.scheduledDate }, { it.startMinuteOfDay }))
        revisions.groupBy { it.scheduledDate }.forEach { (day, blocks) ->
            assertTrue("$day cap", blocks.sumOf { it.plannedMinutes } <= 40)
        }
        assertEquals("every due revision is placed", 5, revisions.size)
        assertEquals("most overdue first", listOf("P1a", "P1b", "P2a", "P2b", "P3a"), revisions.map { it.nodeId })
        assertEquals("one a day: 40 minutes holds one 25 minute block", 5, revisions.map { it.scheduledDate }.distinct().size)
    }

    @Test
    fun `a due date in the future is not placed before it is due`() {
        val result = inputs(revisions = listOf(PlannerFixture.revision("P1a", start.plusDays(4)))).run()
        val revision = result.blocks.single { it.activityType == StudyActivityType.REVISION }
        assertTrue(revision.scheduledDate >= start.plusDays(4))
    }

    @Test
    fun `parked chapters get no revision block`() {
        val result = inputs(
            revisions = listOf(PlannerFixture.revision("P1a", start.minusDays(1))),
        ).copy(includedLeafIds = pack().leafIds().toSet() - setOf("P1a", "P1b")).run()
        assertTrue(result.blocks.none { it.activityType == StudyActivityType.REVISION })
    }

    // --- revision phase ------------------------------------------------------

    @Test
    fun `the revision phase has no learn blocks and orders passes by yield then weakness`() {
        val exam = start.plusDays(40)
        val target = start.plusDays(4)
        val result = inputs(
            target = target,
            exam = exam,
            priorities = mapOf("P3" to priority("P3", PriorityBand.HIGH, 5.0)),
            weak = mapOf("C2a" to 3),
        ).run(horizon = 60)

        val after = result.blocks.filter { it.scheduledDate > target }
        assertTrue(after.isNotEmpty())
        assertTrue("no new learn after finish-by", after.none { it.activityType == StudyActivityType.LEARN })
        assertTrue(after.any { it.activityType == StudyActivityType.MOCK_TEST })
        assertTrue("nothing on the exam day or later", result.blocks.all { it.scheduledDate < exam })

        val passes = after.filter { it.activityType == StudyActivityType.REVISION && it.source == PlanBlockSource.AUTO }
            .sortedWith(compareBy({ it.scheduledDate }, { it.startMinuteOfDay }))
        assertEquals(listOf("P3", "C2", "P1", "P2", "C1"), passes.map { it.nodeId }.distinct().take(5))
    }

    @Test
    fun `learn in the revision phase only when the student opts in`() {
        val big = pack(physicsChapters = 6, chemistryChapters = 6)
        val exam = start.plusDays(30)
        val target = start.plusDays(2)
        val off = inputs(pack = big, target = target, exam = exam, weekday = 120, weekend = 120).run(horizon = 40)
        val on = inputs(pack = big, target = target, exam = exam, weekday = 120, weekend = 120, allowLearn = true).run(horizon = 40)

        assertTrue(off.blocks.none { it.scheduledDate > target && it.activityType == StudyActivityType.LEARN })
        assertTrue(on.blocks.any { it.scheduledDate > target && it.activityType == StudyActivityType.LEARN })
    }

    @Test
    fun `deadline mode without an exam date has no revision phase`() {
        val result = inputs(target = start.plusDays(3)).run(horizon = 30)
        assertTrue(result.blocks.all { it.scheduledDate <= start.plusDays(3) })
    }

    // --- mock tests ----------------------------------------------------------

    @Test
    fun `mock density follows the weeks to the exam`() {
        val exam = start.plusDays(100)
        val result = inputs(target = start.plusDays(1), exam = exam).run(horizon = 100)
        val mocks = result.blocks.filter { it.activityType == StudyActivityType.MOCK_TEST }.map { it.scheduledDate }.sorted()

        assertTrue(mocks.isNotEmpty())
        assertEquals("every mock the simulation placed was emitted", mocks.size, result.mockTestsPlanned)
        assertTrue("revision phase only", mocks.all { it > start.plusDays(1) })
        assertTrue(result.blocks.filter { it.activityType == StudyActivityType.MOCK_TEST }.all { it.plannedMinutes == MOCK_TEST_MINUTES })

        fun daysToExam(date: LocalDate) = ChronoUnit.DAYS.between(date, exam)
        val far = mocks.filter { daysToExam(it) > 56 }
        assertTrue("more than 8 weeks: one per 14 days", far.zipWithNext().all { (a, b) -> ChronoUnit.DAYS.between(a, b) == 14L })
        val mid = mocks.filter { daysToExam(it) in 28..56 }
        assertTrue("4 to 8 weeks: one per 7 days", mid.zipWithNext().all { (a, b) -> ChronoUnit.DAYS.between(a, b) >= 7 })
        assertTrue(mid.size >= 3)
        val last = mocks.filter { daysToExam(it) < 28 }
        assertTrue("last 4 weeks: never back to back", last.zipWithNext().all { (a, b) -> ChronoUnit.DAYS.between(a, b) >= 2 })
        assertTrue(
            "last 4 weeks: at most 3 in any 7 days",
            last.all { day -> last.count { it >= day && it < day.plusDays(7) } <= 3 },
        )
        assertTrue("last 4 weeks: about three a week", last.size in 10..12)
    }

    @Test
    fun `a day without a 200 minute window is skipped and the next one is tried`() {
        val availability = DayOfWeek.entries.map { day ->
            val weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            WeeklyAvailability("w_$day", attemptId, day, 9 * 60, if (weekend) 14 * 60 else 11 * 60)
        }
        val result = inputs(
            target = start.plusDays(1),
            exam = start.plusDays(60),
            availability = availability,
        ).run(horizon = 60)
        val mockDays = result.blocks.filter { it.activityType == StudyActivityType.MOCK_TEST }.map { it.scheduledDate }

        assertTrue(mockDays.isNotEmpty())
        assertTrue("only weekends have a long enough window", mockDays.all { it.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })
        assertEquals("first mock is the first long day of the revision phase", LocalDate.of(2026, 10, 17), mockDays.min())
    }

    @Test
    fun `steady mode plans no mocks until the exam is within 8 weeks`() {
        val far = inputs(mode = PlanMode.STEADY, exam = start.plusDays(100)).run()
        val near = inputs(mode = PlanMode.STEADY, exam = start.plusDays(40)).run()
        assertTrue(far.blocks.none { it.activityType == StudyActivityType.MOCK_TEST })
        assertTrue(near.blocks.any { it.activityType == StudyActivityType.MOCK_TEST })
    }

    @Test
    fun `mocks done recently count towards the density`() {
        val exam = start.plusDays(100)
        val without = inputs(target = start.plusDays(1), exam = exam).run(horizon = 20)
        val with = inputs(target = start.plusDays(1), exam = exam, recentMocks = listOf(start.minusDays(1))).run(horizon = 20)
        val firstWithout = without.blocks.first { it.activityType == StudyActivityType.MOCK_TEST }.scheduledDate
        val firstWith = with.blocks.first { it.activityType == StudyActivityType.MOCK_TEST }.scheduledDate
        assertTrue(firstWith > firstWithout)
    }

    // --- now -----------------------------------------------------------------

    @Test
    fun `nothing is placed before now today and now rounds up to 5 minutes`() {
        val afternoon = everyDay(startMinute = 13 * 60, endMinute = 17 * 60)
        val result = inputs(availability = afternoon).run(nowMs = PlannerFixture.nowAt(start, 14, 3))

        val today = result.blocks.filter { it.scheduledDate == start }
        assertTrue(today.isNotEmpty())
        assertEquals(14 * 60 + 5, today.minOf { it.startMinuteOfDay })
        val tomorrow = result.blocks.filter { it.scheduledDate == start.plusDays(1) }
        assertEquals("later days start at their normal window start", 13 * 60, tomorrow.minOf { it.startMinuteOfDay })
    }

    @Test
    fun `a day that is already over gets no blocks`() {
        val result = inputs().run(nowMs = PlannerFixture.nowAt(start, 23, 30))
        assertTrue(result.blocks.none { it.scheduledDate == start })
        assertTrue(result.blocks.isNotEmpty())
    }

    @Test
    fun `an exact multiple of five stays put`() {
        val result = inputs(availability = everyDay(13 * 60, 17 * 60)).run(nowMs = PlannerFixture.nowAt(start, 14, 5))
        assertEquals(14 * 60 + 5, result.blocks.filter { it.scheduledDate == start }.minOf { it.startMinuteOfDay })
    }

    // --- protected blocks ----------------------------------------------------

    @Test
    fun `only future planner-made blocks the student has not moved are regenerable`() {
        val auto = PlannerFixture.block("a", start, 540, 50)
        assertTrue(auto.isRegenerable())
        assertFalse(auto.copy(source = PlanBlockSource.MANUAL).isRegenerable())
        assertFalse(auto.copy(status = PlanBlockStatus.COMPLETED).isRegenerable())
        assertFalse(auto.copy(status = PlanBlockStatus.SKIPPED).isRegenerable())
        assertFalse("moved", auto.copy(rescheduledFromId = "old").isRegenerable())
        val engine = auto.copy(id = "rev_x", source = PlanBlockSource.REVISION_ENGINE, activityType = StudyActivityType.REVISION)
        assertTrue(engine.isRegenerable())
        assertFalse("student-added revision", engine.copy(id = "uuid-1").isRegenerable())
    }

    @Test
    fun `a replan from now keeps earlier blocks today`() {
        val before = PlannerFixture.block("early", start, 9 * 60, 50)
        val after = PlannerFixture.block("late", start, 15 * 60, 50)
        val tomorrow = PlannerFixture.block("next", start.plusDays(1), 9 * 60, 50)
        assertFalse(before.isReplacedByReplanFrom(start, 12 * 60))
        assertTrue(after.isReplacedByReplanFrom(start, 12 * 60))
        assertTrue(tomorrow.isReplacedByReplanFrom(start, 12 * 60))
    }

    @Test
    fun `completed running manual and moved blocks are kept and planned around`() {
        val completed = PlannerFixture.block("done", start, 9 * 60, 50, status = PlanBlockStatus.COMPLETED, nodeId = "P1a")
        val manual = PlannerFixture.block("manual", start, 10 * 60, 50, source = PlanBlockSource.MANUAL, nodeId = "C1a")
        val moved = PlannerFixture.block("moved", start, 11 * 60, 50, nodeId = "P2a", rescheduledFromId = "orig")
        val result = inputs(preserved = listOf(completed, manual, moved)).run()

        val keep = listOf(completed, manual, moved).map { it.startMinuteOfDay until it.startMinuteOfDay + it.plannedMinutes }
        val first = result.blocks.filter { it.scheduledDate == start }
        assertTrue(first.isNotEmpty())
        assertTrue("never recreated", result.blocks.none { it.id in setOf("done", "manual", "moved") })
        assertTrue(
            "no overlap with kept blocks",
            first.none { b -> keep.any { k -> b.startMinuteOfDay < k.last + 1 && b.startMinuteOfDay + b.plannedMinutes > k.first } },
        )
        // The moved block is 50 of P2a's 60 minutes: only 10 are left to plan. The manual one is 50 of C1a's.
        val learn = result.blocks.filter { it.activityType == StudyActivityType.LEARN }
        assertEquals(10, learn.filter { it.nodeId == "P2a" }.sumOf { it.plannedMinutes })
        assertEquals(10, learn.filter { it.nodeId == "C1a" }.sumOf { it.plannedMinutes })
    }

    @Test
    fun `completed blocks count towards the day's target`() {
        val done = PlannerFixture.block("done", start, 9 * 60, 60, status = PlanBlockStatus.COMPLETED)
        val result = inputs(weekday = 120, weekend = 120, preserved = listOf(done)).run()
        assertEquals(60, result.blocks.filter { it.scheduledDate == start }.sumOf { it.plannedMinutes })
    }

    // --- order ---------------------------------------------------------------

    @Test
    fun `high yield first plans the chapters in band order then by mean`() {
        val physicsOnly = pack(physicsChapters = 3, chemistryChapters = 0)
        val priorities = mapOf(
            "P1" to priority("P1", PriorityBand.LOW, 0.5),
            "P2" to priority("P2", PriorityBand.MEDIUM, 3.0),
            "P3" to priority("P3", PriorityBand.HIGH, 5.0),
        )
        fun firstLearn(order: PlanningOrder) = inputs(pack = physicsOnly, order = order, priorities = priorities)
            .run().blocks.filter { it.activityType == StudyActivityType.LEARN }
            .sortedWith(compareBy({ it.scheduledDate }, { it.startMinuteOfDay }))
            .map { it.nodeId!!.take(2) }.distinct()

        assertEquals(listOf("P3", "P2", "P1"), firstLearn(PlanningOrder.HIGH_YIELD_FIRST))
        assertEquals(listOf("P1", "P2", "P3"), firstLearn(PlanningOrder.DEFAULT))
    }

    @Test
    fun `chapters with no data come after high medium and low`() {
        val physicsOnly = pack(physicsChapters = 3, chemistryChapters = 0)
        val priorities = mapOf(
            "P2" to priority("P2", PriorityBand.LOW, 0.5),
            "P3" to ChapterPriority("P3", PriorityBand.NO_DATA, null, 1, emptyList()),
        )
        val order = inputs(pack = physicsOnly, order = PlanningOrder.HIGH_YIELD_FIRST, priorities = priorities)
            .run().blocks.filter { it.activityType == StudyActivityType.LEARN }
            .sortedWith(compareBy({ it.scheduledDate }, { it.startMinuteOfDay }))
            .map { it.nodeId!!.take(2) }.distinct()
        assertEquals(listOf("P2", "P1", "P3"), order)
    }

    @Test
    fun `shortest first plans the smallest chapter first`() {
        val mixed = ExamPack(
            1, NEET_EXAM_ID, "NEET", "t",
            listOf(
                ExamSubject(
                    "physics", "Physics", order = 0,
                    nodes = listOf(
                        PlannerFixture.chapter("BIG", 0, PlannerFixture.leaf("big1", 200, 0)),
                        PlannerFixture.chapter("SMALL", 1, PlannerFixture.leaf("small1", 40, 0)),
                        PlannerFixture.chapter("MID", 2, PlannerFixture.leaf("mid1", 100, 0)),
                    ),
                ),
            ),
        )
        fun order(o: PlanningOrder) = inputs(pack = mixed, order = o).run().blocks
            .filter { it.activityType == StudyActivityType.LEARN }
            .sortedWith(compareBy({ it.scheduledDate }, { it.startMinuteOfDay })).map { it.nodeId }.distinct()
        assertEquals(listOf("small1", "mid1", "big1"), order(PlanningOrder.SHORTEST_FIRST))
        assertEquals(listOf("big1", "small1", "mid1"), order(PlanningOrder.DEFAULT))
    }

    @Test
    fun `subjects take turns block by block`() {
        val result = inputs().run()
        val firstDay = result.blocks.filter { it.scheduledDate == start }
            .sortedBy { it.startMinuteOfDay }.map { it.subjectId }
        assertEquals(listOf("physics", "chemistry", "physics", "chemistry"), firstDay.take(4))
    }

    // --- fit == planner ------------------------------------------------------

    @Test
    fun `the fit equals what the planner actually schedules`() {
        val target = start.plusDays(9)
        val base = inputs(target = target, weekday = 45, weekend = 45)
        val full = base.run(horizon = 10)
        val fit = full.fit!!

        val placed = full.blocks.filter { it.activityType in setOf(StudyActivityType.LEARN, StudyActivityType.PRACTICE) }
            .sumOf { it.plannedMinutes }
        assertEquals("required work", 750, fit.requiredMinutes)
        assertEquals("scheduled by finish-by", placed, fit.scheduledMinutes)
        assertEquals(750 - placed, fit.shortByMinutes)
        assertFalse(fit.fits)

        // The 21 day plan and the stand-alone fit give the very same numbers.
        assertEquals(fit, base.run(horizon = 21).fit)
        assertEquals(fit, base.planFit(longAgoMs, withOptions = false))
        // Learn leftovers seen by the old unscheduledMinutes agree with the fit's learn part.
        assertTrue(fit.shortByMinutes >= full.unscheduledMinutes)
    }

    @Test
    fun `a plan that fits says so and has no options`() {
        val fit = inputs(target = start.plusDays(20)).planFit(longAgoMs)!!
        assertTrue(fit.fits)
        assertEquals(0, fit.shortByMinutes)
        assertEquals(100, fit.coveragePercent)
        assertTrue(fit.options.isEmpty())
    }

    @Test
    fun `steady mode has no fit at all`() {
        assertNull(inputs(mode = PlanMode.STEADY).planFit(longAgoMs))
    }

    @Test
    fun `covered work is subtracted from the need`() {
        val covered = setOf("P1a", "P1b", "P2a", "P2b", "P3a", "P3b")
        val fit = inputs(covered = covered, target = start.plusDays(5)).planFit(longAgoMs, withOptions = false)!!
        // Only chemistry is left: 2 chapters x (120 learn + 30 practice).
        assertEquals(240, fit.learnMinutes)
        assertEquals(60, fit.practiceMinutes)
    }

    @Test
    fun `each option for a short plan really closes the gap`() {
        val priorities = mapOf(
            "P1" to priority("P1", PriorityBand.HIGH, 5.0),
            "P2" to priority("P2", PriorityBand.MEDIUM, 3.0),
            "P3" to priority("P3", PriorityBand.LOW, 0.5),
            "C1" to priority("C1", PriorityBand.HIGH, 4.0),
            "C2" to priority("C2", PriorityBand.LOW, 0.8),
        )
        val base = inputs(target = start.plusDays(9), weekday = 45, weekend = 45, priorities = priorities)
        val fit = base.planFit(longAgoMs)!!
        assertFalse(fit.fits)

        val add = fit.options.filterIsInstance<PlanFitOption.AddMinutesPerDay>().single()
        assertTrue(add.minutes > 0 && add.minutes % PLAN_FIT_MINUTES_STEP == 0)
        assertTrue(base.copy(weekdayTargetMinutes = 45 + add.minutes, weekendTargetMinutes = 45 + add.minutes).planFit(longAgoMs, false)!!.fits)
        assertFalse(
            "one step less is not enough",
            base.copy(weekdayTargetMinutes = 45 + add.minutes - PLAN_FIT_MINUTES_STEP, weekendTargetMinutes = 45 + add.minutes - PLAN_FIT_MINUTES_STEP)
                .planFit(longAgoMs, false)!!.fits,
        )

        val park = fit.options.filterIsInstance<PlanFitOption.ParkChapters>().single()
        assertEquals("lowest yield first", listOf("P3", "C2"), park.chapterIds.take(2))
        assertTrue(park.count >= 2)
        val parkedLeaves = park.chapterIds.flatMap { id -> base.pack.subjects.flatMap { it.nodes }.first { it.id == id }.leafIds() }.toSet()
        assertTrue(base.copy(includedLeafIds = base.includedLeafIds - parkedLeaves).planFit(longAgoMs, false)!!.fits)
        assertTrue(park.minutesFreed >= fit.shortByMinutes)

        val move = fit.options.filterIsInstance<PlanFitOption.MoveFinishBy>().single()
        assertTrue(move.date > base.targetDate)
        assertTrue(base.copy(targetDate = move.date).planFit(longAgoMs, false)!!.fits)
        assertFalse(base.copy(targetDate = move.date.minusDays(1)).planFit(longAgoMs, false)!!.fits)

        val accept = fit.options.filterIsInstance<PlanFitOption.AcceptCoverage>().single()
        assertEquals(fit.scheduledMinutes * 100 / fit.requiredMinutes, accept.percent)
        assertTrue(accept.percent in 1..99)
    }

    @Test
    fun `moving finish-by never goes past the exam`() {
        val exam = start.plusDays(12)
        val fit = inputs(target = start.plusDays(9), exam = exam, weekday = 30, weekend = 30).planFit(longAgoMs)!!
        val move = fit.options.filterIsInstance<PlanFitOption.MoveFinishBy>().firstOrNull()
        assertTrue(move == null || move.date < exam)
        assertEquals(3, fit.revisionWindowDays)
    }

    // --- rebalance -----------------------------------------------------------

    @Test
    fun `rebalancing a missed day skips its blocks and puts their work back in the queue`() {
        val first = inputs(target = start.plusDays(40)).run()
        val today = start.plusDays(1)
        val stored = first.blocks
        val dayOne = stored.filter { it.scheduledDate == start }
        assertTrue(dayOne.isNotEmpty())

        // One of day one's blocks was actually done.
        val done = dayOne.first()
        val session = StudySession(
            id = "s", attemptId = attemptId, nodeId = done.nodeId, subjectId = done.subjectId, planBlockId = done.id,
            activityType = done.activityType, startedAtEpochMs = 1, endedAtEpochMs = 2, studyDate = start,
            timeZoneId = "UTC", focusedSeconds = done.plannedMinutes * 60, pausedSeconds = 0,
            status = StudySessionStatus.COMPLETED, focusLockUsed = false, interruptionCount = 0,
            customTitle = null, createdAtEpochMs = 1, updatedAtEpochMs = 1,
        )
        val missed = missedPlannedBlocks(stored, listOf(session), today, since = start)
        assertEquals(dayOne.size - 1, missed.size)
        assertTrue(missed.none { it.id == done.id })
        assertTrue("only past days", missed.all { it.scheduledDate < today })

        val skipped = skipMissedBlocks(missed, nowMs = 5)
        assertTrue(skipped.all { it.status == PlanBlockStatus.SKIPPED })

        // Regenerate from today. The done block's minutes are focused time now.
        val focused = mapOf(done.nodeId!! to done.plannedMinutes)
        val again = inputs(target = start.plusDays(40), from = today)
            .copy(focusedLearnMinutesByNode = focused, preservedBlocks = skipped).run()
        val skippedLearn = skipped.filter { it.activityType == StudyActivityType.LEARN }
        assertTrue(skippedLearn.isNotEmpty())
        val replanned = again.blocks.filter { it.activityType == StudyActivityType.LEARN }.groupBy { it.nodeId }
        skippedLearn.forEach { assertTrue("${it.nodeId} is back in the queue", replanned.containsKey(it.nodeId)) }
        assertEquals(StudyActivityType.LEARN, done.activityType)
        assertEquals(
            "all the learn work is still planned in full, less the block that was done",
            600 - done.plannedMinutes,
            again.minutes(StudyActivityType.LEARN),
        )
    }

    @Test
    fun `nothing is missed when the day is done or moved`() {
        val plan = inputs().run().blocks.filter { it.scheduledDate == start }
        val today = start.plusDays(1)
        val moved = plan.map { it.copy(rescheduledFromId = "x") }
        assertTrue(missedPlannedBlocks(moved, emptyList(), today, start).isEmpty())
        val completed = plan.map { it.copy(status = PlanBlockStatus.COMPLETED) }
        assertTrue(missedPlannedBlocks(completed, emptyList(), today, start).isEmpty())
        assertTrue("today itself is not a past day", missedPlannedBlocks(plan, emptyList(), start, start).isEmpty())
        assertTrue("before the cut-off", missedPlannedBlocks(plan, emptyList(), today, today).isEmpty())
    }

    // --- determinism ---------------------------------------------------------

    @Test
    fun `generation is deterministic with every block type`() {
        fun go() = inputs(
            target = start.plusDays(5), exam = start.plusDays(60),
            revisions = listOf(PlannerFixture.revision("P1a", start.minusDays(1))),
        ).run(horizon = 60)
        assertEquals(go().blocks, go().blocks)
        assertEquals("ids are unique", go().blocks.size, go().blocks.map { it.id }.distinct().size)
    }
}
