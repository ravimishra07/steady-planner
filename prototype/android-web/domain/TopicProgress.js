// Port of domain/TopicProgress.kt
import { dataClass, enumClass } from './kt.js';

/** Explicit syllabus coverage — separate from time spent. */
export const TopicProgress = dataClass({ attemptId: undefined, nodeId: undefined, status: undefined, coveredAtEpochMs: undefined, updatedAtEpochMs: undefined });

export const TopicProgressStatus = enumClass(['NOT_STARTED', 'IN_PROGRESS', 'COVERED']);

/** The single rule for what a syllabus checkmark means. */
export function toggledTopicProgress(current, attemptId, nodeId, nowMs) {
  const isCovered = current?.status === TopicProgressStatus.COVERED;
  return TopicProgress({
    attemptId,
    nodeId,
    status: isCovered ? TopicProgressStatus.NOT_STARTED : TopicProgressStatus.COVERED,
    coveredAtEpochMs: isCovered ? null : nowMs,
    updatedAtEpochMs: nowMs,
  });
}

/** A completed study session moves a topic to IN_PROGRESS unless covered — time spent is not coverage. */
export function topicProgressAfterStudy(current, attemptId, nodeId, nowMs) {
  if (current?.status === TopicProgressStatus.COVERED) return current;
  return TopicProgress({
    attemptId,
    nodeId,
    status: TopicProgressStatus.IN_PROGRESS,
    coveredAtEpochMs: current?.coveredAtEpochMs ?? null,
    updatedAtEpochMs: nowMs,
  });
}

/** Revision scheduling for one topic. nextDueDate: date|null. */
export const RevisionState = dataClass({
  attemptId: undefined, nodeId: undefined, enabled: undefined, lastReviewedAtEpochMs: undefined,
  nextDueDate: undefined, revisionCount: undefined, intervalDays: undefined, updatedAtEpochMs: undefined,
});
