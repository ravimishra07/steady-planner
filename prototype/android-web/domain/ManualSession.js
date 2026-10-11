// Port of domain/ManualSession.kt. `zone` params accept a zone id string; times resolve in the local zone.
import { dataClass, coerceAtLeast } from './kt.js';
import { atStartOfDayMs, minuteOfDayAt, systemZoneId } from './time.js';
import { StudyActivityType } from './StudyPlan.js';
import { StudySession, StudySessionStatus } from './StudyHistory.js';
import { focusedMinutes } from './Metrics.js';

/** The types a student can pick when logging or editing a session by hand. */
export const LOGGABLE_ACTIVITY_TYPES = Object.freeze([
  StudyActivityType.LEARN, StudyActivityType.REVISION, StudyActivityType.PRACTICE, StudyActivityType.MOCK_TEST, StudyActivityType.CLASS,
]);

export const MANUAL_SESSION_MIN_MINUTES = 1;
export const MANUAL_SESSION_MAX_MINUTES = 12 * 60;
export const MANUAL_SESSION_STEP_MINUTES = 5;
export const MANUAL_SESSION_DEFAULT_MINUTES = 60;

/** What the student typed. date: date. */
export const SessionEntry = dataClass(
  { nodeId: undefined, subjectId: undefined, activityType: undefined, durationMinutes: undefined, date: undefined, startMinuteOfDay: undefined },
  {
    get isValid() {
      return this.subjectId != null &&
        this.durationMinutes >= MANUAL_SESSION_MIN_MINUTES && this.durationMinutes <= MANUAL_SESSION_MAX_MINUTES &&
        this.startMinuteOfDay >= 0 && this.startMinuteOfDay < 24 * 60;
    },
  },
);

const startEpochMs = (entry) => atStartOfDayMs(entry.date) + entry.startMinuteOfDay * 60_000;

/** A new completed session from [entry]. */
export function manualStudySession(id, attemptId, entry, zone, nowMs) {
  const start = startEpochMs(entry);
  return StudySession({
    id,
    attemptId,
    nodeId: entry.nodeId,
    subjectId: entry.subjectId,
    planBlockId: null,
    activityType: entry.activityType,
    startedAtEpochMs: start,
    endedAtEpochMs: start + entry.durationMinutes * 60_000,
    studyDate: entry.date,
    timeZoneId: typeof zone === 'string' ? zone : systemZoneId(),
    focusedSeconds: entry.durationMinutes * 60,
    pausedSeconds: 0,
    status: StudySessionStatus.COMPLETED,
    focusLockUsed: false,
    interruptionCount: 0,
    customTitle: null,
    isManual: true,
    createdAtEpochMs: nowMs,
    updatedAtEpochMs: nowMs,
  });
}

/** StudySession.editedWith(entry, nowMs) */
export function editedWith(session, entry, nowMs) {
  const start = startEpochMs(entry);
  return session.copy({
    nodeId: entry.nodeId,
    subjectId: entry.subjectId,
    activityType: entry.activityType,
    startedAtEpochMs: start,
    endedAtEpochMs: start + (entry.durationMinutes * 60 + session.pausedSeconds) * 1000,
    studyDate: entry.date,
    focusedSeconds: entry.durationMinutes * 60,
    updatedAtEpochMs: nowMs,
  });
}

/** StudySession.toEntry() */
export function toEntry(session) {
  return SessionEntry({
    nodeId: session.nodeId,
    subjectId: session.subjectId,
    activityType: session.activityType,
    durationMinutes: coerceAtLeast(focusedMinutes(session), MANUAL_SESSION_MIN_MINUTES),
    date: session.studyDate,
    startMinuteOfDay: minuteOfDayAt(session.startedAtEpochMs),
  });
}
