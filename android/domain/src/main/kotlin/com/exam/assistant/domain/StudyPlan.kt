package com.exam.assistant.domain

import java.time.LocalDate

enum class StudyActivityType {
    LEARN,
    REVISION,
    PRACTICE,
    MOCK_TEST,
    CLASS,
    CUSTOM,
    BREAK,
}

/**
 * What the student intended to do — scheduled intent, not actual history.
 * [nodeId] is nullable because a custom event (e.g. a coaching class) may
 * not map to a syllabus node. [subjectId] is persisted alongside [nodeId]
 * (even though it's derivable from the pack) purely because it's needed for
 * cheap historical aggregation and stays version-pinned by the attempt.
 */
data class StudyPlanBlock(
    val id: String,
    val attemptId: String,

    val nodeId: String?,
    val subjectId: String?,
    val customTitle: String?,

    val activityType: StudyActivityType,

    val scheduledDate: LocalDate,
    val startMinuteOfDay: Int,
    val plannedMinutes: Int,

    val status: PlanBlockStatus,
    val source: PlanBlockSource,

    val rescheduledFromId: String?,
    val replacedById: String?,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

enum class PlanBlockStatus {
    PLANNED,
    COMPLETED,
    MISSED,
    SKIPPED,
    RESCHEDULED,
}

enum class PlanBlockSource {
    AUTO,
    MANUAL,
    REVISION_ENGINE,
}

/**
 * Reschedule as history-preserving fork, not mutation: the original block
 * becomes RESCHEDULED and points at its replacement; the replacement points
 * back at the original. Neither record's identity is reused.
 */
fun rescheduleBlock(
    original: StudyPlanBlock,
    newDate: LocalDate,
    newStartMinuteOfDay: Int,
    newId: String,
    nowMs: Long,
): Pair<StudyPlanBlock, StudyPlanBlock> {
    val replacement = original.copy(
        id = newId,
        scheduledDate = newDate,
        startMinuteOfDay = newStartMinuteOfDay,
        status = PlanBlockStatus.PLANNED,
        rescheduledFromId = original.id,
        replacedById = null,
        createdAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
    )
    val closedOriginal = original.copy(
        status = PlanBlockStatus.RESCHEDULED,
        replacedById = replacement.id,
        updatedAtEpochMs = nowMs,
    )
    return closedOriginal to replacement
}
