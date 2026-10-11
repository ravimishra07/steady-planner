// Port of domain/MockTest.kt — mock/test log (§8.1) and mistake log (§8.2).
import { dataClass, enumClass, sumOf } from './kt.js';
import { plusDays, minDate } from './time.js';
import { RevisionSchedule, revisionStateAfterLearn } from './RevisionSchedule.js';
import { StudyOutcome } from './StudyHistory.js';

export const MockTestType = enumClass(['UNIT_TEST', 'PART_TEST', 'FULL_MOCK', 'PYQ_PAPER']);
export const MistakeType = enumClass(['CONCEPT', 'SILLY', 'TIME', 'GUESS']);
/** The four NEET sections a score can be split into. */
export const MockSubject = enumClass(['PHYSICS', 'CHEMISTRY', 'BOTANY', 'ZOOLOGY']);

export const NEET_MAX_TOTAL = 720;

export const MockTest = dataClass(
  {
    id: undefined, attemptId: undefined, date: undefined, name: undefined, type: undefined,
    physics: null, chemistry: null, botany: null, zoology: null,
    total: undefined, maxTotal: NEET_MAX_TOTAL, attempted: null, correct: null, wrong: null,
  },
  {
    score(subject) {
      switch (subject) {
        case MockSubject.PHYSICS: return this.physics;
        case MockSubject.CHEMISTRY: return this.chemistry;
        case MockSubject.BOTANY: return this.botany;
        case MockSubject.ZOOLOGY: return this.zoology;
        default: return null;
      }
    },
  },
);

/** A chapter where marks were lost in one mock, and why. */
export const MockMistake = dataClass({ mockId: undefined, chapterId: undefined, type: undefined });

/** Sum of subject scores when any is given, otherwise the typed total; null when neither. */
export function mockTotal(subjectScores, typedTotal) {
  const given = subjectScores.filter((x) => x != null);
  return given.length > 0 ? sumOf(given) : typedTotal;
}

/** Chapters tagged in [after] that were not tagged in [before]: Set<String>. */
export function newlyTaggedChapters(before, after) {
  const b = new Set([...before].map((m) => m.chapterId));
  return new Set([...after].map((m) => m.chapterId).filter((c) => !b.has(c)));
}

/** A chapter lost marks in a mock: scheduled as a Struggled outcome; earlier due date kept. */
export function revisionStateAfterMistake(current, attemptId, chapterId, mockDate, nowMs) {
  if (current == null) {
    return revisionStateAfterLearn(null, attemptId, chapterId, mockDate, nowMs, StudyOutcome.STRUGGLED);
  }
  const interval = RevisionSchedule.intervalAfterRevision(current.intervalDays, StudyOutcome.STRUGGLED);
  const due = plusDays(mockDate, interval);
  return current.copy({
    enabled: true,
    nextDueDate: current.nextDueDate != null ? minDate(current.nextDueDate, due) : due,
    intervalDays: interval,
    updatedAtEpochMs: nowMs,
  });
}
