package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialPlanTest {

    private val pack = ExamPack(
        schemaVersion = 1,
        examId = "cgl",
        displayName = "CGL",
        syllabusVersion = "1",
        subjects = listOf(
            ExamSubject(
                id = "quant",
                name = "Quant",
                order = 0,
                nodes = listOf(
                    SyllabusNode("chapter", "Chapter", SyllabusNodeKind.CHAPTER, 0, children = listOf(
                        SyllabusNode("leaf-a", "A", SyllabusNodeKind.TOPIC, 0),
                        SyllabusNode("leaf-b", "B", SyllabusNodeKind.TOPIC, 1),
                    )),
                ),
            ),
        ),
    )

    @Test
    fun `initial plan fills only supplied free windows up to the daily budget`() {
        val monday = LocalDate.of(2026, 9, 7)
        val windows = listOf(
            WeeklyAvailability("am", "attempt", DayOfWeek.MONDAY, 6 * 60, 8 * 60),
            WeeklyAvailability("pm", "attempt", DayOfWeek.MONDAY, 16 * 60, 19 * 60),
        )

        val blocks = generateInitialPlan("attempt", pack, monday, 1, 4f, 4f, windows, nowMs = 1L)

        assertEquals(4 * 60, blocks.sumOf { it.plannedMinutes })
        assertTrue(blocks.all { block ->
            windows.any { block.startMinuteOfDay >= it.startMinuteOfDay && block.startMinuteOfDay + block.plannedMinutes <= it.endMinuteOfDay }
        })
        assertTrue(blocks.zipWithNext().all { (a, b) -> a.startMinuteOfDay + a.plannedMinutes <= b.startMinuteOfDay })
    }

    @Test
    fun `covered leaves are not scheduled`() {
        val monday = LocalDate.of(2026, 9, 7)
        val blocks = generateInitialPlan(
            "attempt",
            pack,
            monday,
            1,
            1f,
            1f,
            listOf(WeeklyAvailability("am", "attempt", DayOfWeek.MONDAY, 6 * 60, 9 * 60)),
            excludedLeafIds = setOf("leaf-a"),
            nowMs = 1L,
        )

        assertFalse(blocks.isEmpty())
        assertTrue(blocks.all { it.nodeId == "leaf-b" })
    }

    @Test
    fun `initial plan rotates across subjects instead of exhausting one subject first`() {
        val multiSubjectPack = pack.copy(
            subjects = listOf(
                pack.subjects.single().copy(
                    id = "physics",
                    nodes = listOf(
                        SyllabusNode("physics-1", "Physics 1", SyllabusNodeKind.CHAPTER, 0),
                        SyllabusNode("physics-2", "Physics 2", SyllabusNodeKind.CHAPTER, 1),
                    ),
                ),
                pack.subjects.single().copy(
                    id = "chemistry",
                    nodes = listOf(
                        SyllabusNode("chemistry-1", "Chemistry 1", SyllabusNodeKind.CHAPTER, 0),
                        SyllabusNode("chemistry-2", "Chemistry 2", SyllabusNodeKind.CHAPTER, 1),
                    ),
                ),
            ),
        )
        val monday = LocalDate.of(2026, 9, 7)

        val blocks = generateInitialPlan(
            attemptId = "attempt",
            pack = multiSubjectPack,
            startDate = monday,
            days = 1,
            weekdayHours = 2f,
            weekendHours = 2f,
            weeklyAvailability = listOf(WeeklyAvailability("am", "attempt", DayOfWeek.MONDAY, 6 * 60, 9 * 60)),
            nowMs = 1L,
        )

        assertEquals(listOf("physics", "chemistry"), blocks.take(2).map { it.subjectId })
    }
}
