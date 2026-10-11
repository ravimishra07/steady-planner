package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingPlanTest {
    private val start = LocalDate.of(2026, 9, 7)

    @Test
    fun `fills through day twenty one without repeating exhausted work`() {
        val result = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(3_000),
            startDate = start,
            targetDate = start.plusDays(100),
            weeklyAvailability = availability(120),
            nowMs = 1,
        )

        assertEquals(start.plusDays(20), result.blocks.maxOf { it.scheduledDate })
        assertEquals(2_100, result.blocks.sumOf { it.plannedMinutes })
        assertEquals(900, result.unscheduledMinutes)
    }

    @Test
    fun `stops at target date`() {
        val result = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(2_000),
            startDate = start,
            targetDate = start.plusDays(2),
            weeklyAvailability = availability(120),
            nowMs = 1,
        )

        assertEquals(start.plusDays(2), result.blocks.maxOf { it.scheduledDate })
    }

    @Test
    fun `subtracts focused minutes and preserved plan intent`() {
        val preserved = block(nodeId = "leaf-a", minutes = 50)
        val result = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(200),
            startDate = start,
            targetDate = start.plusDays(20),
            weeklyAvailability = availability(120),
            focusedLearnMinutesByNode = mapOf("leaf-a" to 75),
            preservedBlocks = listOf(preserved),
            nowMs = 1,
        )

        assertEquals(0, result.blocks.filter { it.nodeId == "leaf-a" }.sumOf { it.plannedMinutes })
        assertEquals(100, result.blocks.filter { it.nodeId == "leaf-b" }.sumOf { it.plannedMinutes })
    }

    @Test
    fun `never schedules covered or excluded leaves`() {
        val result = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(200),
            startDate = start,
            targetDate = start.plusDays(20),
            weeklyAvailability = availability(120),
            includedLeafIds = setOf("leaf-a"),
            coveredLeafIds = setOf("leaf-a"),
            nowMs = 1,
        )

        assertTrue(result.blocks.isEmpty())
    }

    @Test
    fun `preserved blocks reserve their occupied interval`() {
        val preserved = block(nodeId = "leaf-a", minutes = 50, startMinute = 360)
        val result = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(300),
            startDate = start,
            targetDate = start,
            weeklyAvailability = availability(180),
            preservedBlocks = listOf(preserved),
            nowMs = 1,
        )

        assertTrue(result.blocks.none { it.startMinuteOfDay < 410 && it.startMinuteOfDay + it.plannedMinutes > 360 })
    }

    @Test
    fun `generation is deterministic`() {
        val first = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(400),
            startDate = start,
            targetDate = start.plusDays(20),
            weeklyAvailability = availability(120),
            nowMs = 1,
        )
        val second = generateRollingPlan(
            attemptId = "attempt",
            pack = pack(400),
            startDate = start,
            targetDate = start.plusDays(20),
            weeklyAvailability = availability(120),
            nowMs = 1,
        )

        assertEquals(first.blocks, second.blocks)
    }

    private fun availability(minutes: Int): List<WeeklyAvailability> = DayOfWeek.entries.map { day ->
        WeeklyAvailability("window-${day.name}", "attempt", day, 360, 360 + minutes)
    }

    private fun pack(minutes: Int) = ExamPack(
        schemaVersion = 1,
        examId = NEET_EXAM_ID,
        displayName = "NEET",
        syllabusVersion = "test",
        subjects = listOf(
            ExamSubject(
                id = "physics",
                name = "Physics",
                order = 0,
                nodes = listOf(
                    SyllabusNode("leaf-a", "A", SyllabusNodeKind.TOPIC, 0, minutes / 2),
                    SyllabusNode("leaf-b", "B", SyllabusNodeKind.TOPIC, 1, minutes - minutes / 2),
                ),
            ),
        ),
    )

    private fun block(nodeId: String, minutes: Int, startMinute: Int = 600) = StudyPlanBlock(
        id = "manual",
        attemptId = "attempt",
        nodeId = nodeId,
        subjectId = "physics",
        customTitle = null,
        activityType = StudyActivityType.LEARN,
        scheduledDate = start,
        startMinuteOfDay = startMinute,
        plannedMinutes = minutes,
        status = PlanBlockStatus.PLANNED,
        source = PlanBlockSource.MANUAL,
        rescheduledFromId = null,
        replacedById = null,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}
