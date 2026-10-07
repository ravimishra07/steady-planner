package com.exam.assistant.domain

import java.time.LocalDate
import java.time.ZoneId

/*
 * Manual entry and edits (PRODUCTION_V1_PLAN §6.3). A logged or edited session is an ordinary
 * COMPLETED [StudySession] whose focused seconds equal the duration typed, so every metric in
 * Metrics.kt counts it with no special case.
 */

/** The types a student can pick when logging or editing a session by hand. */
val LOGGABLE_ACTIVITY_TYPES: List<StudyActivityType> = listOf(
    StudyActivityType.LEARN,
    StudyActivityType.REVISION,
    StudyActivityType.PRACTICE,
    StudyActivityType.MOCK_TEST,
    StudyActivityType.CLASS,
)

const val MANUAL_SESSION_MIN_MINUTES = 1
const val MANUAL_SESSION_MAX_MINUTES = 12 * 60
const val MANUAL_SESSION_STEP_MINUTES = 5
const val MANUAL_SESSION_DEFAULT_MINUTES = 60

/** What the student typed: one session's chapter, type, length and clock time. */
data class SessionEntry(
    val nodeId: String?,
    val subjectId: String?,
    val activityType: StudyActivityType,
    val durationMinutes: Int,
    val date: LocalDate,
    val startMinuteOfDay: Int,
) {
    val isValid: Boolean
        get() = subjectId != null &&
            durationMinutes in MANUAL_SESSION_MIN_MINUTES..MANUAL_SESSION_MAX_MINUTES &&
            startMinuteOfDay in 0 until 24 * 60
}

/** A new completed session from [entry]. Revision and topic progress are applied by the repository. */
fun manualStudySession(
    id: String,
    attemptId: String,
    entry: SessionEntry,
    zone: ZoneId,
    nowMs: Long,
): StudySession {
    val start = entry.startEpochMs(zone)
    return StudySession(
        id = id,
        attemptId = attemptId,
        nodeId = entry.nodeId,
        subjectId = entry.subjectId,
        planBlockId = null,
        activityType = entry.activityType,
        startedAtEpochMs = start,
        endedAtEpochMs = start + entry.durationMinutes * 60_000L,
        studyDate = entry.date,
        timeZoneId = zone.id,
        focusedSeconds = entry.durationMinutes * 60,
        pausedSeconds = 0,
        status = StudySessionStatus.COMPLETED,
        focusLockUsed = false,
        interruptionCount = 0,
        customTitle = null,
        isManual = true,
        createdAtEpochMs = nowMs,
        updatedAtEpochMs = nowMs,
    )
}

/** The editable fields of [this] replaced by [entry]. Status, rating and revision bookkeeping are kept. */
fun StudySession.editedWith(entry: SessionEntry, nowMs: Long): StudySession {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    val start = entry.startEpochMs(zone)
    return copy(
        nodeId = entry.nodeId,
        subjectId = entry.subjectId,
        activityType = entry.activityType,
        startedAtEpochMs = start,
        endedAtEpochMs = start + (entry.durationMinutes * 60 + pausedSeconds) * 1000L,
        studyDate = entry.date,
        focusedSeconds = entry.durationMinutes * 60,
        updatedAtEpochMs = nowMs,
    )
}

/** [this] read back as an editable entry, in the zone it was recorded in. */
fun StudySession.toEntry(): SessionEntry {
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
    val time = java.time.Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalTime()
    return SessionEntry(
        nodeId = nodeId,
        subjectId = subjectId,
        activityType = activityType,
        durationMinutes = focusedMinutes(this).coerceAtLeast(MANUAL_SESSION_MIN_MINUTES),
        date = studyDate,
        startMinuteOfDay = time.hour * 60 + time.minute,
    )
}

private fun SessionEntry.startEpochMs(zone: ZoneId): Long =
    date.atStartOfDay(zone).plusMinutes(startMinuteOfDay.toLong()).toInstant().toEpochMilli()
