// Port of domain/StudyPlan.kt
import { dataClass, enumClass } from './kt.js';

export const StudyActivityType = enumClass(['LEARN', 'REVISION', 'PRACTICE', 'MOCK_TEST', 'CLASS', 'CUSTOM', 'BREAK']);

/** Scheduled intent, not history. scheduledDate: date. */
export const StudyPlanBlock = dataClass({
  id: undefined, attemptId: undefined, nodeId: undefined, subjectId: undefined, customTitle: undefined,
  activityType: undefined, scheduledDate: undefined, startMinuteOfDay: undefined, plannedMinutes: undefined,
  status: undefined, source: undefined, rescheduledFromId: undefined, replacedById: undefined,
  createdAtEpochMs: undefined, updatedAtEpochMs: undefined,
});

export const PlanBlockStatus = enumClass(['PLANNED', 'COMPLETED', 'MISSED', 'SKIPPED', 'RESCHEDULED']);
export const PlanBlockSource = enumClass(['AUTO', 'MANUAL', 'REVISION_ENGINE']);

/** History-preserving fork. Returns [closedOriginal, replacement]. */
export function rescheduleBlock(original, newDate, newStartMinuteOfDay, newId, nowMs) {
  const replacement = original.copy({
    id: newId,
    scheduledDate: newDate,
    startMinuteOfDay: newStartMinuteOfDay,
    status: PlanBlockStatus.PLANNED,
    rescheduledFromId: original.id,
    replacedById: null,
    createdAtEpochMs: nowMs,
    updatedAtEpochMs: nowMs,
  });
  const closedOriginal = original.copy({
    status: PlanBlockStatus.RESCHEDULED,
    replacedById: replacement.id,
    updatedAtEpochMs: nowMs,
  });
  return [closedOriginal, replacement];
}
