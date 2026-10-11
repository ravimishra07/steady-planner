// Port of domain/StudyHistory.kt
import { dataClass, enumClass } from './kt.js';

/** What actually happened. studyDate: date; dueDateAtCompletion: date|null. */
export const StudySession = dataClass({
  id: undefined, attemptId: undefined, nodeId: undefined, subjectId: undefined, planBlockId: undefined,
  activityType: undefined, startedAtEpochMs: undefined, endedAtEpochMs: undefined,
  studyDate: undefined, timeZoneId: undefined, focusedSeconds: undefined, pausedSeconds: undefined,
  status: undefined, focusLockUsed: undefined, interruptionCount: undefined,
  outcome: null,
  dueDateAtCompletion: null,
  priorIntervalDays: null,
  customTitle: undefined,
  createdAtEpochMs: undefined, updatedAtEpochMs: undefined,
  isManual: false,
});

export const StudySessionStatus = enumClass(['RUNNING', 'PAUSED', 'COMPLETED', 'ABANDONED']);
export const StudyOutcome = enumClass(['STRUGGLED', 'OKAY', 'STRONG']);

export const StudySessionSegment = dataClass({
  id: undefined, sessionId: undefined, nodeId: undefined, subjectId: undefined,
  startedAtEpochMs: undefined, endedAtEpochMs: undefined, focusedSeconds: undefined, order: undefined,
});

/** Runtime-only state for the active Focus session. */
export const FocusRuntimeState = dataClass({
  status: undefined, studySessionId: undefined, planBlockId: undefined, activeSegmentId: undefined,
  plannedDurationSeconds: undefined, remainingSeconds: undefined, endsAtEpochMs: undefined,
});
