// Port of domain/ExamAttempt.kt
import { dataClass, enumClass, lazyDefault } from './kt.js';
import { daysBetween } from './time.js';
import { PlanMode } from './PlanMode.js';
import { chapters } from './ExamPack.js';

/** The student's attempt at one exam. examDate: date|null, targetCompletionDate: date. */
export const ExamAttempt = dataClass({
  id: undefined,
  examId: undefined,
  syllabusVersion: undefined,
  examDate: undefined,
  targetCompletionDate: undefined,
  createdAtEpochMs: undefined,
  updatedAtEpochMs: undefined,
  status: undefined,
  /** Deadline mode is the default; steady mode has no finish-by pressure. */
  planMode: lazyDefault(() => PlanMode.DEADLINE),
  /** Where the student starts (§3.3). Null for attempts made before it was asked. */
  startingPoint: null,
});

/** "Which class / attempt?" A dropper has already been through most of the syllabus once. */
export const StartingPoint = enumClass(['CLASS_11', 'CLASS_12', 'DROPPER'], (name) => ({
  /** Droppers default to most chapters done once, and the plan leans on revision. */
  revisionShaped: name === 'DROPPER',
}));

export const ExamAttemptStatus = enumClass(['ACTIVE', 'COMPLETED', 'ARCHIVED']);

export const daysUntil = (date, today) => daysBetween(today, date);

/** weekStartsOn: DayOfWeek */
export const UserProfile = dataClass({ id: undefined, timeZoneId: undefined, weekStartsOn: undefined, localeTag: undefined, createdAtEpochMs: undefined });

/** StartingPoint.defaultFinishedChapterIds(pack): Set<String> */
export const defaultFinishedChapterIds = (startingPoint, pack) =>
  startingPoint === StartingPoint.DROPPER ? new Set(chapters(pack).map((c) => c.id)) : new Set();
