// Port of core/data/db/StudyPlanBlockEntity.kt. Table `study_plan_block`, PK id.
import { dataClass } from '../../domain/kt.js';
import { toEpochDay } from '../../domain/time.js';
import { StudyPlanBlock, StudyActivityType, PlanBlockStatus, PlanBlockSource } from '../../domain/StudyPlan.js';
import { epochDayToDate } from './Converters.js';

export const StudyPlanBlockEntity = dataClass({
  id: undefined, attemptId: undefined, nodeId: null, subjectId: null, customTitle: null, activityType: undefined,
  scheduledDateEpochDay: undefined, startMinuteOfDay: undefined, plannedMinutes: undefined, status: undefined,
  source: undefined, rescheduledFromId: null, replacedById: null, createdAtEpochMs: undefined, updatedAtEpochMs: undefined,
}, {
  toDomain() {
    return StudyPlanBlock({
      id: this.id,
      attemptId: this.attemptId,
      nodeId: this.nodeId,
      subjectId: this.subjectId,
      customTitle: this.customTitle,
      activityType: StudyActivityType.valueOf(this.activityType),
      scheduledDate: epochDayToDate(this.scheduledDateEpochDay),
      startMinuteOfDay: this.startMinuteOfDay,
      plannedMinutes: this.plannedMinutes,
      status: PlanBlockStatus.valueOf(this.status),
      source: PlanBlockSource.valueOf(this.source),
      rescheduledFromId: this.rescheduledFromId,
      replacedById: this.replacedById,
      createdAtEpochMs: this.createdAtEpochMs,
      updatedAtEpochMs: this.updatedAtEpochMs,
    });
  },
});
StudyPlanBlockEntity.tableName = 'study_plan_block';
StudyPlanBlockEntity.primaryKeys = ['id'];

export const toEntity = (b) => StudyPlanBlockEntity({
  id: b.id,
  attemptId: b.attemptId,
  nodeId: b.nodeId,
  subjectId: b.subjectId,
  customTitle: b.customTitle,
  activityType: b.activityType.name,
  scheduledDateEpochDay: toEpochDay(b.scheduledDate),
  startMinuteOfDay: b.startMinuteOfDay,
  plannedMinutes: b.plannedMinutes,
  status: b.status.name,
  source: b.source.name,
  rescheduledFromId: b.rescheduledFromId,
  replacedById: b.replacedById,
  createdAtEpochMs: b.createdAtEpochMs,
  updatedAtEpochMs: b.updatedAtEpochMs,
});

export class StudyPlanBlockDao {
  constructor(t) { this.t = t; }
  upsert(entity) { this.t.upsert(entity); }
  upsertAll(entities) { this.t.upsertAll(entities); }
  observeForDate(attemptId, epochDay) {
    return this.t.flow((rows) => rows.filter((r) => r.attemptId === attemptId && r.scheduledDateEpochDay === epochDay));
  }
  forDateOnce(attemptId, epochDay) {
    return this.t.rows((r) => r.attemptId === attemptId && r.scheduledDateEpochDay === epochDay);
  }
  between(attemptId, startEpochDay, endEpochDay) {
    return this.t.rows((r) => r.attemptId === attemptId &&
      r.scheduledDateEpochDay >= startEpochDay && r.scheduledDateEpochDay <= endEpochDay);
  }
  byId(id) { return this.t.first((r) => r.id === id); }
  deleteForAttempt(attemptId) { this.t.deleteWhere((r) => r.attemptId === attemptId); }

  /**
   * Deletes what a replan from (fromEpochDay, fromMinute) recreates: still-planned blocks the
   * planner made itself and the student has not moved. Mirrors `StudyPlanBlock.isReplacedByReplanFrom`.
   * Engine revision blocks are told apart from ones the student added by the `rev_` id prefix.
   */
  deleteReplaceable(attemptId, fromEpochDay, fromMinute) {
    this.t.deleteWhere((r) => r.attemptId === attemptId && r.status === 'PLANNED' &&
      r.rescheduledFromId == null &&
      (r.source === 'AUTO' || (r.source === 'REVISION_ENGINE' && r.id.startsWith('rev_'))) &&
      (r.scheduledDateEpochDay > fromEpochDay ||
        (r.scheduledDateEpochDay === fromEpochDay && r.startMinuteOfDay >= fromMinute)));
  }

  /** UPDATE ... SET status = 'SKIPPED', updatedAtEpochMs = :nowMs WHERE id IN (:ids) */
  markSkipped(ids, nowMs) {
    const set = new Set(ids);
    const hits = this.t.rows((r) => set.has(r.id));
    this.t.upsertAll(hits.map((e) => e.copy({ status: 'SKIPPED', updatedAtEpochMs: nowMs })));
  }
}
