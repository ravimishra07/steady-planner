package com.exam.assistant.domain

import java.time.LocalDate

/**
 * What actually happened — truthful historical fact, distinct from
 * [StudyPlanBlock] (intent). An unplanned session ([planBlockId] null) is
 * legitimate and counts toward study history same as a planned one.
 *
 * Both [startedAtEpochMs]/[endedAtEpochMs] (exact instant) and [studyDate]/
 * [timeZoneId] (the student's experienced calendar day) are preserved
 * together — a session finished at 11:50pm stays on that calendar day even
 * if the device's timezone changes later. Never re-derive [studyDate] from
 * the current device timezone.
 */
data class StudySession(
    val id: String,
    val attemptId: String,

    val nodeId: String?,
    val subjectId: String?,
    val planBlockId: String?,

    val activityType: StudyActivityType,

    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,

    val studyDate: LocalDate,
    val timeZoneId: String,

    val focusedSeconds: Int,
    val pausedSeconds: Int,

    val status: StudySessionStatus,

    val focusLockUsed: Boolean,
    val interruptionCount: Int,

    val outcome: StudyOutcome? = null,

    /**
     * For a completed REVISION: the date the revision was due when it was done.
     * Null for Learn sessions, partial saves and sessions completed before this
     * was recorded — "revision on time" ignores those rather than guessing.
     */
    val dueDateAtCompletion: LocalDate? = null,

    /** The topic's revision interval before this session completed; null if it had no schedule yet. */
    val priorIntervalDays: Int? = null,

    val customTitle: String?,

    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,

    /** Logged by hand ("Log study I did offline", §6.3) rather than timed. Counted like any session. */
    val isManual: Boolean = false,
)

enum class StudySessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    ABANDONED,
}

enum class StudyOutcome {
    STRUGGLED,
    OKAY,
    STRONG,
}

/**
 * One syllabus node's slice of a [StudySession]. A normal session has
 * exactly one segment; a future multi-topic Focus session (switching topics
 * without stopping the timer) closes the current segment and opens the
 * next, keeping the parent session running throughout.
 */
data class StudySessionSegment(
    val id: String,
    val sessionId: String,

    val nodeId: String?,
    val subjectId: String?,

    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,

    val focusedSeconds: Int,
    val order: Int,
)

/**
 * Runtime-only state for whatever Focus session is active right now.
 * Display title/subtitle are resolved from [studySessionId] -> nodeId ->
 * ExamPack at read time, never stored here as persisted truth.
 */
data class FocusRuntimeState(
    val status: FocusStatus,
    val studySessionId: String?,
    val planBlockId: String?,
    val activeSegmentId: String?,

    val plannedDurationSeconds: Int,
    val remainingSeconds: Int,

    val endsAtEpochMs: Long?,
)
