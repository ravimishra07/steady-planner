package com.exam.assistant.domain

import java.time.LocalDate

/**
 * One shared state for the metric tests: a small two-subject pack, a chapter-level
 * exclusion, completed + partial + abandoned + running sessions, and revision
 * states including an overdue one.
 */
internal object MetricsFixture {
    val today: LocalDate = LocalDate.of(2026, 9, 10)
    const val nowMinute = 15 * 60
    const val attemptId = "attempt"

    private fun leaf(id: String, minutes: Int, order: Int) =
        SyllabusNode(id, id, SyllabusNodeKind.SECTION, order, estimatedMinutes = minutes)

    private fun chapter(id: String, order: Int, vararg leaves: SyllabusNode) =
        SyllabusNode(id, id, SyllabusNodeKind.CHAPTER, order, children = leaves.toList())

    val pack = ExamPack(
        schemaVersion = 1,
        examId = NEET_EXAM_ID,
        displayName = "NEET",
        syllabusVersion = "test",
        subjects = listOf(
            ExamSubject(
                id = "physics", name = "Physics", order = 0, questions = 45,
                nodes = listOf(
                    chapter("P1", 0, leaf("p1a", 60, 0), leaf("p1b", 60, 1)),
                    chapter("P2", 1, leaf("p2a", 120, 0)),
                    chapter("P3", 2, leaf("p3a", 30, 0), leaf("p3b", 30, 1)),
                ),
            ),
            ExamSubject(
                id = "botany", name = "Botany", order = 1, questions = 45,
                nodes = listOf(
                    chapter("B1", 0, leaf("b1a", 90, 0), leaf("b1b", 90, 1)),
                    chapter("B2", 1, leaf("b2a", 60, 0)),
                ),
            ),
        ),
    )

    /** Chapter P3 is "not preparing". Its leaf p3a is covered anyway. */
    val overrides: Map<String, TargetNodeState> = mapOf("P3" to TargetNodeState.EXCLUDED)

    private fun progress(nodeId: String, status: TopicProgressStatus) =
        TopicProgress(attemptId, nodeId, status, null, 1L)

    val topicProgress = listOf(
        progress("p1a", TopicProgressStatus.COVERED),
        progress("p2a", TopicProgressStatus.COVERED),
        progress("b1a", TopicProgressStatus.COVERED),
        progress("p3a", TopicProgressStatus.COVERED),
        progress("b1b", TopicProgressStatus.IN_PROGRESS),
    )

    fun session(
        id: String,
        daysAgo: Int,
        seconds: Int,
        type: StudyActivityType = StudyActivityType.LEARN,
        nodeId: String = "p1a",
        status: StudySessionStatus = StudySessionStatus.COMPLETED,
        planBlockId: String? = null,
        dueDateAtCompletion: LocalDate? = null,
    ) = StudySession(
        id = id,
        attemptId = attemptId,
        nodeId = nodeId,
        subjectId = if (nodeId.startsWith("b")) "botany" else "physics",
        planBlockId = planBlockId,
        activityType = type,
        startedAtEpochMs = today.minusDays(daysAgo.toLong()).toEpochDay() * 86_400_000L + 9 * 3_600_000L,
        endedAtEpochMs = null,
        studyDate = today.minusDays(daysAgo.toLong()),
        timeZoneId = "UTC",
        focusedSeconds = seconds,
        pausedSeconds = 0,
        status = status,
        focusLockUsed = false,
        interruptionCount = 0,
        dueDateAtCompletion = dueDateAtCompletion,
        customTitle = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    val sessions = listOf(
        // Full session on a planned block yesterday.
        session("s1", daysAgo = 1, seconds = 3_000, planBlockId = "b1"),
        // Partial "Save & end": 10.5 focused minutes of a 50 minute block.
        session("s2", daysAgo = 0, seconds = 630, nodeId = "p2a", planBlockId = "b2"),
        // Revision done late (due 3 days ago, done 2 days ago).
        session("s3", daysAgo = 2, seconds = 1_200, type = StudyActivityType.REVISION, dueDateAtCompletion = today.minusDays(3)),
        // Revision done on its due date.
        session("s4", daysAgo = 4, seconds = 900, type = StudyActivityType.REVISION, nodeId = "b1a", dueDateAtCompletion = today.minusDays(4)),
        // Never credited.
        session("s5", daysAgo = 0, seconds = 0, status = StudySessionStatus.ABANDONED),
        session("s6", daysAgo = 0, seconds = 400, status = StudySessionStatus.RUNNING),
    )

    private fun block(id: String, daysAgo: Int, start: Int, minutes: Int, status: PlanBlockStatus) = StudyPlanBlock(
        id = id,
        attemptId = attemptId,
        nodeId = "p1a",
        subjectId = "physics",
        customTitle = null,
        activityType = StudyActivityType.LEARN,
        scheduledDate = today.minusDays(daysAgo.toLong()),
        startMinuteOfDay = start,
        plannedMinutes = minutes,
        status = status,
        source = PlanBlockSource.AUTO,
        rescheduledFromId = null,
        replacedById = null,
        createdAtEpochMs = 0,
        updatedAtEpochMs = 0,
    )

    val planBlocks = listOf(
        block("b1", 1, 9 * 60, 50, PlanBlockStatus.COMPLETED),
        // Ended at 09:50, partially studied; the block itself was not marked completed.
        block("b2", 0, 9 * 60, 50, PlanBlockStatus.PLANNED),
        // 20:00 is still ahead of the 15:00 clock: not "behind".
        block("b3", 0, 20 * 60, 50, PlanBlockStatus.PLANNED),
        block("b4", 1, 11 * 60, 50, PlanBlockStatus.RESCHEDULED),
    )

    private fun revision(nodeId: String, dueInDays: Int, interval: Int) = RevisionState(
        attemptId, nodeId, enabled = true, lastReviewedAtEpochMs = null,
        nextDueDate = today.plusDays(dueInDays.toLong()), revisionCount = 1, intervalDays = interval, updatedAtEpochMs = 1,
    )

    val revisionStates = listOf(
        revision("p1a", dueInDays = -5, interval = 3), // overdue
        revision("p2a", dueInDays = 0, interval = 3), // due today
        revision("b1a", dueInDays = 3, interval = 7), // this week
        revision("b2a", dueInDays = 10, interval = 7), // later
    )

    val plan = InsightPlan(
        startedOn = today.minusDays(30),
        targetDate = today.plusDays(60),
        weeklyAvailability = DayOfWeekAll.windows(attemptId, 9 * 60, 13 * 60),
        weekdayTargetMinutes = 4 * 60,
        weekendTargetMinutes = 4 * 60,
    )
}

internal object DayOfWeekAll {
    fun windows(attemptId: String, start: Int, end: Int): List<WeeklyAvailability> =
        java.time.DayOfWeek.entries.map { WeeklyAvailability("w_$it", attemptId, it, start, end) }
}
