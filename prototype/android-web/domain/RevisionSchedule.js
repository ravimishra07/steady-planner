// Port of domain/RevisionSchedule.kt — the one revision-interval table (§7.1).
import { dataClass, coerceAtMost } from './kt.js';
import { plusDays } from './time.js';
import { StudyActivityType } from './StudyPlan.js';
import { StudyOutcome, StudySessionStatus } from './StudyHistory.js';
import { RevisionState } from './TopicProgress.js';

const RevisionRule = dataClass({ multiplier: undefined, capDays: undefined, resetDays: null });

export const RevisionSchedule = {
  /** What a missing rating means. */
  get DEFAULT_OUTCOME() { return StudyOutcome.OKAY; },
  RevisionRule,
  /** First revision interval, in days after a Learn session. Map<StudyOutcome, Int>. */
  get AFTER_LEARN_DAYS() {
    return new Map([[StudyOutcome.STRUGGLED, 1], [StudyOutcome.OKAY, 3], [StudyOutcome.STRONG, 7]]);
  },
  get AFTER_REVISION() {
    return new Map([
      [StudyOutcome.STRUGGLED, RevisionRule({ multiplier: 1, capDays: 1, resetDays: 1 })],
      [StudyOutcome.OKAY, RevisionRule({ multiplier: 2, capDays: 30 })],
      [StudyOutcome.STRONG, RevisionRule({ multiplier: 3, capDays: 60 })],
    ]);
  },
  intervalAfterLearn(outcome) {
    return this.AFTER_LEARN_DAYS.get(outcome ?? this.DEFAULT_OUTCOME);
  },
  /** previousDays is the interval in force when this revision was done. */
  intervalAfterRevision(previousDays, outcome) {
    const rule = this.AFTER_REVISION.get(outcome ?? this.DEFAULT_OUTCOME);
    if (rule.resetDays != null) return rule.resetDays;
    const previous = previousDays ?? this.intervalAfterLearn(null);
    return coerceAtMost(previous * rule.multiplier, rule.capDays);
  },
};

/** When the topic's next revision will be due if a session is completed today unrated. */
export function nextRevisionDueIfCompleted(current, activityType, today) {
  if (activityType === StudyActivityType.REVISION) {
    return plusDays(today, RevisionSchedule.intervalAfterRevision(current?.intervalDays ?? null, null));
  }
  return current?.nextDueDate ?? plusDays(today, RevisionSchedule.intervalAfterLearn(null));
}

/** After a completed LEARN session: schedule its first revision. An existing schedule is kept. */
export function revisionStateAfterLearn(current, attemptId, nodeId, today, nowMs, outcome) {
  if (current != null) return current;
  const interval = RevisionSchedule.intervalAfterLearn(outcome);
  return RevisionState({
    attemptId, nodeId, enabled: true, lastReviewedAtEpochMs: null,
    nextDueDate: plusDays(today, interval), revisionCount: 0, intervalDays: interval, updatedAtEpochMs: nowMs,
  });
}

/** After a completed REVISION session: bump the count and push the next due date out. */
export function revisionStateAfterReview(current, attemptId, nodeId, today, nowMs, outcome) {
  const interval = RevisionSchedule.intervalAfterRevision(current?.intervalDays ?? null, outcome);
  return RevisionState({
    attemptId, nodeId, enabled: true, lastReviewedAtEpochMs: nowMs,
    nextDueDate: plusDays(today, interval), revisionCount: (current?.revisionCount ?? 0) + 1,
    intervalDays: interval, updatedAtEpochMs: nowMs,
  });
}

/** Result of closing a study session. */
export const CompletedStudy = dataClass({ session: undefined, revision: undefined });

/** The pure core of "complete a Focus session". */
export function completeStudySession(session, currentRevision, today, nowMs) {
  const isRevision = session.activityType === StudyActivityType.REVISION;
  const completed = session.copy({
    status: StudySessionStatus.COMPLETED,
    endedAtEpochMs: nowMs,
    updatedAtEpochMs: nowMs,
    priorIntervalDays: currentRevision?.intervalDays ?? null,
    dueDateAtCompletion: isRevision ? currentRevision?.nextDueDate ?? null : null,
  });
  const nodeId = session.nodeId;
  if (nodeId == null) return CompletedStudy({ session: completed, revision: null });
  const revision = isRevision
    ? revisionStateAfterReview(currentRevision, session.attemptId, nodeId, today, nowMs, session.outcome)
    : revisionStateAfterLearn(currentRevision, session.attemptId, nodeId, today, nowMs, session.outcome);
  return CompletedStudy({ session: completed, revision });
}

/** The student rated a completed session; recomputed from the interval recorded at completion. */
export function recordStudyOutcome(session, currentRevision, outcome, nowMs) {
  const rated = session.copy({ outcome, updatedAtEpochMs: nowMs });
  if (currentRevision == null) return CompletedStudy({ session: rated, revision: null });
  let interval;
  if (session.activityType === StudyActivityType.REVISION) {
    interval = RevisionSchedule.intervalAfterRevision(session.priorIntervalDays, outcome);
  } else {
    if (session.priorIntervalDays != null) return CompletedStudy({ session: rated, revision: currentRevision });
    interval = RevisionSchedule.intervalAfterLearn(outcome);
  }
  return CompletedStudy({
    session: rated,
    revision: currentRevision.copy({ nextDueDate: plusDays(session.studyDate, interval), intervalDays: interval, updatedAtEpochMs: nowMs }),
  });
}
