// Port of domain/ExamDates.kt
import { enumClass } from './kt.js';
import { parseDate, plusDays, minusDays, maxDate, coerceDateIn } from './time.js';
import { daysToTarget } from './Metrics.js';

/** Compile-time default for the NEET 2027 date. */
export const DEFAULT_NEET_EXAM_DATE = '2027-05-02';
/** Revision window left between finish-by and the exam by default. */
export const DEFAULT_REVISION_WINDOW_DAYS = 90;
/** The finish-by date may not be closer than this many days from today. */
export const MIN_FINISH_BY_DAYS_AHEAD = 7;

/** Parses the configured exam date; anything unreadable falls back to DEFAULT_NEET_EXAM_DATE. */
export function resolveExamDate(configured) {
  try {
    return parseDate((configured ?? '').trim());
  } catch {
    return parseDate(DEFAULT_NEET_EXAM_DATE);
  }
}

/** Exam date − 90 days, kept inside the valid range. */
export const defaultFinishByDate = (examDate, today) =>
  coerceFinishBy(minusDays(examDate, DEFAULT_REVISION_WINDOW_DAYS), examDate, today);

export const FinishByIssue = enumClass(['TOO_SOON', 'AFTER_EXAM']);

/** Null when finishBy is on or before examDate and at least a week out. */
export function validateFinishBy(finishBy, examDate, today) {
  if (finishBy > examDate) return FinishByIssue.AFTER_EXAM;
  if (finishBy < plusDays(today, MIN_FINISH_BY_DAYS_AHEAD)) return FinishByIssue.TOO_SOON;
  return null;
}

/** Clamps into [today + 7, examDate]; when empty the lower bound wins. */
export function coerceFinishBy(finishBy, examDate, today) {
  const lower = plusDays(today, MIN_FINISH_BY_DAYS_AHEAD);
  const upper = maxDate(examDate, lower);
  return coerceDateIn(finishBy, lower, upper);
}

/** Whole days from today to the exam, never negative. */
export const daysToExam = (attempt, today) => (attempt.examDate != null ? daysToTarget(attempt.examDate, today) : null);
