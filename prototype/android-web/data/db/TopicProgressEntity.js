// Port of core/data/db/TopicProgressEntity.kt.
// Tables `topic_progress` (PK attemptId+nodeId) and `revision_state` (PK attemptId+nodeId).
import { dataClass } from '../../domain/kt.js';
import { toEpochDay } from '../../domain/time.js';
import { TopicProgress, TopicProgressStatus, RevisionState } from '../../domain/TopicProgress.js';
import { epochDayToDate } from './Converters.js';

export const TopicProgressEntity = dataClass({
  attemptId: undefined, nodeId: undefined, status: undefined, coveredAtEpochMs: null, updatedAtEpochMs: undefined,
}, {
  toDomain() {
    return TopicProgress({
      attemptId: this.attemptId,
      nodeId: this.nodeId,
      status: TopicProgressStatus.valueOf(this.status),
      coveredAtEpochMs: this.coveredAtEpochMs,
      updatedAtEpochMs: this.updatedAtEpochMs,
    });
  },
});
TopicProgressEntity.tableName = 'topic_progress';
TopicProgressEntity.primaryKeys = ['attemptId', 'nodeId'];

export const progressToEntity = (p) => TopicProgressEntity({
  attemptId: p.attemptId, nodeId: p.nodeId, status: p.status.name,
  coveredAtEpochMs: p.coveredAtEpochMs, updatedAtEpochMs: p.updatedAtEpochMs,
});

export const RevisionStateEntity = dataClass({
  attemptId: undefined, nodeId: undefined, enabled: undefined, lastReviewedAtEpochMs: null,
  nextDueDateEpochDay: null, revisionCount: undefined, intervalDays: null, updatedAtEpochMs: undefined,
}, {
  toDomain() {
    return RevisionState({
      attemptId: this.attemptId,
      nodeId: this.nodeId,
      enabled: this.enabled,
      lastReviewedAtEpochMs: this.lastReviewedAtEpochMs,
      nextDueDate: epochDayToDate(this.nextDueDateEpochDay),
      revisionCount: this.revisionCount,
      intervalDays: this.intervalDays,
      updatedAtEpochMs: this.updatedAtEpochMs,
    });
  },
});
RevisionStateEntity.tableName = 'revision_state';
RevisionStateEntity.primaryKeys = ['attemptId', 'nodeId'];
RevisionStateEntity.booleanColumns = ['enabled'];

export const revisionToEntity = (r) => RevisionStateEntity({
  attemptId: r.attemptId,
  nodeId: r.nodeId,
  enabled: r.enabled,
  lastReviewedAtEpochMs: r.lastReviewedAtEpochMs,
  nextDueDateEpochDay: r.nextDueDate == null ? null : toEpochDay(r.nextDueDate),
  revisionCount: r.revisionCount,
  intervalDays: r.intervalDays,
  updatedAtEpochMs: r.updatedAtEpochMs,
});

/** Kotlin overloads toEntity on TopicProgress / RevisionState; dispatch on shape. */
export const toEntity = (x) => ('enabled' in x ? revisionToEntity(x) : progressToEntity(x));

export class TopicProgressDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  upsertAll(entities) { this.t.upsertAll(entities); }
  observeAll(attemptId) { return this.t.flow((rows) => rows.filter((r) => r.attemptId === attemptId)); }
  allOnce(attemptId) { return this.t.rows((r) => r.attemptId === attemptId); }
  byNode(attemptId, nodeId) { return this.t.first((r) => r.attemptId === attemptId && r.nodeId === nodeId); }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }
}

export class RevisionStateDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  byNode(attemptId, nodeId) { return this.t.first((r) => r.attemptId === attemptId && r.nodeId === nodeId); }
  /** nextDueDateEpochDay <= :epochDay AND enabled = 1 (NULL due dates never match, as in SQL). */
  dueBy(attemptId, epochDay) {
    return this.t.rows((r) => r.attemptId === attemptId && r.nextDueDateEpochDay != null &&
      r.nextDueDateEpochDay <= epochDay && r.enabled);
  }
  allEnabled(attemptId) { return this.t.rows((r) => r.attemptId === attemptId && r.enabled); }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }
}
