package com.exam.assistant.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared pieces for the planner tests: a small two-subject pack, availability and clocks. */
internal object PlannerFixture {
    /** A Monday. */
    val start: LocalDate = LocalDate.of(2026, 10, 12)
    const val attemptId = "attempt"

    /** Long before [start], so no clipping to "now" happens unless a test asks for it. */
    const val longAgoMs = 1L

    fun leaf(id: String, minutes: Int, order: Int) =
        SyllabusNode(id, id, SyllabusNodeKind.SECTION, order, estimatedMinutes = minutes)

    fun chapter(id: String, order: Int, vararg leaves: SyllabusNode) =
        SyllabusNode(id, id, SyllabusNodeKind.CHAPTER, order, children = leaves.toList())

    /** Physics P1..P3 and Chemistry C1..C2, two 60-minute sections each: 120 minutes, 30 minutes of practice. */
    fun pack(physicsChapters: Int = 3, chemistryChapters: Int = 2, sectionMinutes: Int = 60): ExamPack {
        fun chapters(prefix: String, count: Int) = (1..count).map { n ->
            chapter("$prefix$n", n - 1, leaf("$prefix${n}a", sectionMinutes, 0), leaf("$prefix${n}b", sectionMinutes, 1))
        }
        return ExamPack(
            schemaVersion = 1,
            examId = NEET_EXAM_ID,
            displayName = "NEET",
            syllabusVersion = "test",
            subjects = listOf(
                ExamSubject("physics", "Physics", order = 0, nodes = chapters("P", physicsChapters)),
                ExamSubject("chemistry", "Chemistry", order = 1, nodes = chapters("C", chemistryChapters)),
            ),
        )
    }

    /** One window every day of the week. */
    fun everyDay(startMinute: Int = 9 * 60, endMinute: Int = 13 * 60): List<WeeklyAvailability> =
        DayOfWeek.entries.map { WeeklyAvailability("w_$it", attemptId, it, startMinute, endMinute) }

    fun nowAt(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun revision(nodeId: String, due: LocalDate) = RevisionState(
        attemptId, nodeId, enabled = true, lastReviewedAtEpochMs = null,
        nextDueDate = due, revisionCount = 1, intervalDays = 3, updatedAtEpochMs = 1,
    )

    fun block(
        id: String,
        date: LocalDate,
        startMinute: Int,
        minutes: Int,
        type: StudyActivityType = StudyActivityType.LEARN,
        status: PlanBlockStatus = PlanBlockStatus.PLANNED,
        source: PlanBlockSource = PlanBlockSource.AUTO,
        nodeId: String? = "P1a",
        rescheduledFromId: String? = null,
    ) = StudyPlanBlock(
        id = id, attemptId = attemptId, nodeId = nodeId, subjectId = "physics", customTitle = null,
        activityType = type, scheduledDate = date, startMinuteOfDay = startMinute, plannedMinutes = minutes,
        status = status, source = source, rescheduledFromId = rescheduledFromId, replacedById = null,
        createdAtEpochMs = 1, updatedAtEpochMs = 1,
    )
}

internal fun RollingPlanResult.minutes(type: StudyActivityType): Int =
    blocks.filter { it.activityType == type }.sumOf { it.plannedMinutes }
