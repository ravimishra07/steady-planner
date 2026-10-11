// Port of core/data/db/StudySessionEntity.kt.
// Tables `study_session` (PK id) and `study_session_segment` (PK id). Aggregate queries
// (SUM/COUNT/GROUP BY) are computed in JS with the same filters and grouping.
import { dataClass } from '../../domain/kt.js';
import { toEpochDay } from '../../domain/time.js';
import { StudySession, StudySessionSegment, StudySessionStatus, StudyOutcome } from '../../domain/StudyHistory.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { epochDayToDate } from './Converters.js';

export const StudySessionEntity = dataClass({
  id: undefined, attemptId: undefined, nodeId: null, subjectId: null, planBlockId: null, activityType: undefined,
  startedAtEpochMs: undefined, endedAtEpochMs: null, studyDateEpochDay: undefined, timeZoneId: undefined,
  focusedSeconds: undefined, pausedSeconds: undefined, status: undefined, focusLockUsed: undefined,
  interruptionCount: undefined, outcome: null, dueDateAtCompletionEpochDay: null, priorIntervalDays: null,
  customTitle: null, createdAtEpochMs: undefined, updatedAtEpochMs: undefined,
  /** Logged by hand rather than timed (§6.3). */
  isManual: false,
}, {
  toDomain() {
    return StudySession({
      id: this.id,
      attemptId: this.attemptId,
      nodeId: this.nodeId,
      subjectId: this.subjectId,
      planBlockId: this.planBlockId,
      activityType: StudyActivityType.valueOf(this.activityType),
      startedAtEpochMs: this.startedAtEpochMs,
      endedAtEpochMs: this.endedAtEpochMs,
      studyDate: epochDayToDate(this.studyDateEpochDay),
      timeZoneId: this.timeZoneId,
      focusedSeconds: this.focusedSeconds,
      pausedSeconds: this.pausedSeconds,
      status: StudySessionStatus.valueOf(this.status),
      focusLockUsed: this.focusLockUsed,
      interruptionCount: this.interruptionCount,
      outcome: this.outcome == null ? null : StudyOutcome.valueOf(this.outcome),
      dueDateAtCompletion: epochDayToDate(this.dueDateAtCompletionEpochDay),
      priorIntervalDays: this.priorIntervalDays,
      customTitle: this.customTitle,
      createdAtEpochMs: this.createdAtEpochMs,
      updatedAtEpochMs: this.updatedAtEpochMs,
      isManual: this.isManual,
    });
  },
});
StudySessionEntity.tableName = 'study_session';
StudySessionEntity.primaryKeys = ['id'];
StudySessionEntity.booleanColumns = ['focusLockUsed', 'isManual'];

export const sessionToEntity = (s) => StudySessionEntity({
  id: s.id,
  attemptId: s.attemptId,
  nodeId: s.nodeId,
  subjectId: s.subjectId,
  planBlockId: s.planBlockId,
  activityType: s.activityType.name,
  startedAtEpochMs: s.startedAtEpochMs,
  endedAtEpochMs: s.endedAtEpochMs,
  studyDateEpochDay: toEpochDay(s.studyDate),
  timeZoneId: s.timeZoneId,
  focusedSeconds: s.focusedSeconds,
  pausedSeconds: s.pausedSeconds,
  status: s.status.name,
  focusLockUsed: s.focusLockUsed,
  interruptionCount: s.interruptionCount,
  outcome: s.outcome?.name ?? null,
  dueDateAtCompletionEpochDay: s.dueDateAtCompletion == null ? null : toEpochDay(s.dueDateAtCompletion),
  priorIntervalDays: s.priorIntervalDays ?? null,
  customTitle: s.customTitle ?? null,
  createdAtEpochMs: s.createdAtEpochMs,
  updatedAtEpochMs: s.updatedAtEpochMs,
  isManual: s.isManual ?? false,
});

export const StudySessionSegmentEntity = dataClass({
  id: undefined, sessionId: undefined, nodeId: null, subjectId: null, startedAtEpochMs: undefined,
  endedAtEpochMs: null, focusedSeconds: undefined, order: undefined,
}, {
  toDomain() {
    return StudySessionSegment({
      id: this.id, sessionId: this.sessionId, nodeId: this.nodeId, subjectId: this.subjectId,
      startedAtEpochMs: this.startedAtEpochMs, endedAtEpochMs: this.endedAtEpochMs,
      focusedSeconds: this.focusedSeconds, order: this.order,
    });
  },
});
StudySessionSegmentEntity.tableName = 'study_session_segment';
StudySessionSegmentEntity.primaryKeys = ['id'];

export const segmentToEntity = (s) => StudySessionSegmentEntity({
  id: s.id, sessionId: s.sessionId, nodeId: s.nodeId, subjectId: s.subjectId,
  startedAtEpochMs: s.startedAtEpochMs, endedAtEpochMs: s.endedAtEpochMs,
  focusedSeconds: s.focusedSeconds, order: s.order,
});

/** Kotlin overloads toEntity on StudySession / StudySessionSegment; dispatch on shape. */
export const toEntity = (x) => ('sessionId' in x ? segmentToEntity(x) : sessionToEntity(x));

export const DaySum = dataClass({ studyDateEpochDay: undefined, totalSeconds: undefined, sessionCount: undefined });
export const SubjectSum = dataClass({ subjectId: null, totalSeconds: undefined });
export const NodeFocusedSum = dataClass({ nodeId: undefined, totalSeconds: undefined });
export const NodeCount = dataClass({ nodeId: undefined, total: undefined });

/** GROUP BY helper keeping first-seen order. */
function groupRows(rows, keyOf) {
  const m = new Map();
  for (const r of rows) { const k = keyOf(r); if (!m.has(k)) m.set(k, []); m.get(k).push(r); }
  return m;
}
const sumFocused = (rows) => rows.reduce((s, r) => s + r.focusedSeconds, 0);

export class StudySessionDao {
  constructor(sessions, segments) { this.sessions = sessions; this.segments = segments; }
  upsert(entity) { this.sessions.upsert(entity); }
  upsertAll(entities) { this.sessions.upsertAll(entities); }
  byId(id) { return this.sessions.first((r) => r.id === id); }
  forDate(attemptId, epochDay) {
    return this.sessions.rows((r) => r.attemptId === attemptId && r.studyDateEpochDay === epochDay);
  }
  between(attemptId, startEpochDay, endEpochDay) {
    return this.sessions.rows((r) => r.attemptId === attemptId &&
      r.studyDateEpochDay >= startEpochDay && r.studyDateEpochDay <= endEpochDay);
  }
  forNode(attemptId, nodeId) { return this.sessions.rows((r) => r.attemptId === attemptId && r.nodeId === nodeId); }
  forPlanBlock(planBlockId) { return this.sessions.first((r) => r.planBlockId === planBlockId); }
  activeSession(attemptId) {
    return this.sessions.first((r) => r.attemptId === attemptId && (r.status === 'RUNNING' || r.status === 'PAUSED'));
  }
  dailySums(attemptId, startEpochDay, endEpochDay) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED' &&
      r.studyDateEpochDay >= startEpochDay && r.studyDateEpochDay <= endEpochDay);
    return [...groupRows(rows, (r) => r.studyDateEpochDay)].map(([day, rs]) =>
      DaySum({ studyDateEpochDay: day, totalSeconds: sumFocused(rs), sessionCount: rs.length }));
  }
  subjectSums(attemptId, startEpochDay, endEpochDay) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED' &&
      r.studyDateEpochDay >= startEpochDay && r.studyDateEpochDay <= endEpochDay);
    return [...groupRows(rows, (r) => r.subjectId ?? null)].map(([subjectId, rs]) =>
      SubjectSum({ subjectId, totalSeconds: sumFocused(rs) }));
  }
  /** Learn minutes per node. Titled sessions on a node are coaching backlog lectures (§5.4) and stay out. */
  completedLearnSumsByNode(attemptId) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED' &&
      r.activityType === 'LEARN' && r.nodeId != null && r.customTitle == null);
    return [...groupRows(rows, (r) => r.nodeId)].map(([nodeId, rs]) => NodeFocusedSum({ nodeId, totalSeconds: sumFocused(rs) }));
  }
  /** How many completed sessions on each node the student rated STRUGGLED. */
  struggledCountsByNode(attemptId) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED' &&
      r.outcome === 'STRUGGLED' && r.nodeId != null);
    return [...groupRows(rows, (r) => r.nodeId)].map(([nodeId, rs]) => NodeCount({ nodeId, total: rs.length }));
  }
  completedNodeIdsOfType(attemptId, activityType) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED' &&
      r.activityType === activityType && r.focusedSeconds > 0 && r.nodeId != null);
    return [...new Set(rows.map((r) => r.nodeId))];
  }
  earliestStudyDate(attemptId) {
    const rows = this.sessions.rawRows((r) => r.attemptId === attemptId && r.status === 'COMPLETED');
    return rows.length ? Math.min(...rows.map((r) => r.studyDateEpochDay)) : null;
  }
  deleteById(id) { this.sessions.deleteWhere((r) => r.id === id); }
  deleteSegmentsFor(sessionId) { this.segments.deleteWhere((r) => r.sessionId === sessionId); }
  /** @Transaction */
  deleteSession(id) { this.deleteSegmentsFor(id); this.deleteById(id); }
  deleteForAttempt(attemptId) { this.sessions.deleteWhere((r) => r.attemptId === attemptId); }
  upsertSegment(entity) { this.segments.upsert(entity); }
  /** ORDER BY `order` */
  segmentsFor(sessionId) {
    return this.segments.rows((r) => r.sessionId === sessionId).sort((a, b) => a.order - b.order);
  }
  openSegment(sessionId) { return this.segments.first((r) => r.sessionId === sessionId && r.endedAtEpochMs == null); }
  deleteSegmentsForAttempt(attemptId) {
    const ids = new Set(this.sessions.rawRows((r) => r.attemptId === attemptId).map((r) => r.id));
    this.segments.deleteWhere((r) => ids.has(r.sessionId));
  }
  /** @Transaction */
  deleteAllForAttempt(attemptId) { this.deleteSegmentsForAttempt(attemptId); this.deleteForAttempt(attemptId); }
}
