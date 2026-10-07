package com.exam.assistant.domain

import java.time.LocalDate

/**
 * Explicit syllabus coverage — separate from time spent. Studying a topic
 * for an hour does not automatically mean it is covered; only marking it
 * done does. Aggregates like total minutes or session count are NOT stored
 * here — they are derived from [StudySession]/[StudySessionSegment] history.
 */
data class TopicProgress(
    val attemptId: String,
    val nodeId: String,

    val status: TopicProgressStatus,

    val coveredAtEpochMs: Long?,
    val updatedAtEpochMs: Long,
)

enum class TopicProgressStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COVERED,
}

/**
 * The single rule for what a syllabus checkmark means — Syllabus screen,
 * Focus completion, and any future flow all call this instead of each
 * inventing their own toggle semantics.
 */
fun toggledTopicProgress(current: TopicProgress?, attemptId: String, nodeId: String, nowMs: Long): TopicProgress {
    val isCovered = current?.status == TopicProgressStatus.COVERED
    return TopicProgress(
        attemptId = attemptId,
        nodeId = nodeId,
        status = if (isCovered) TopicProgressStatus.NOT_STARTED else TopicProgressStatus.COVERED,
        coveredAtEpochMs = if (isCovered) null else nowMs,
        updatedAtEpochMs = nowMs,
    )
}

/** A completed study session on a topic moves it to IN_PROGRESS if it wasn't already covered — time spent is not coverage. */
fun topicProgressAfterStudy(current: TopicProgress?, attemptId: String, nodeId: String, nowMs: Long): TopicProgress {
    if (current?.status == TopicProgressStatus.COVERED) return current
    return TopicProgress(
        attemptId = attemptId,
        nodeId = nodeId,
        status = TopicProgressStatus.IN_PROGRESS,
        coveredAtEpochMs = current?.coveredAtEpochMs,
        updatedAtEpochMs = nowMs,
    )
}

/**
 * Revision scheduling for one topic. Answers "when is this due again?" —
 * [StudySession] with [StudyActivityType.REVISION] answers "when did the
 * student actually revise it?" Never a second parallel history system.
 */
data class RevisionState(
    val attemptId: String,
    val nodeId: String,

    val enabled: Boolean,

    val lastReviewedAtEpochMs: Long?,
    val nextDueDate: java.time.LocalDate?,

    val revisionCount: Int,
    val intervalDays: Int?,

    val updatedAtEpochMs: Long,
)
