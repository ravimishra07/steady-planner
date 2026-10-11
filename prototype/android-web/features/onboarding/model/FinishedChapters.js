// Port of feature/onboarding/model/FinishedChapters.kt
// Rules for "Mark chapters you've already finished" (PRODUCTION_V1_PLAN §3.3). Sets are JS Sets.
import { StartingPoint } from '../../../domain/ExamAttempt.js';
import { idiv } from '../../../domain/kt.js';

/** Whole-subject toggle: everything finished clears it, otherwise it all becomes finished. */
export const toggleSubjectFinished = (covered, subject) => toggleAll(covered, subject.chapters.map((it) => it.id));

/** Whole-class toggle across every subject (all of Class 11, say). */
export const toggleClassFinished = (covered, subjects, classNumber) =>
  toggleAll(covered, subjects.flatMap((it) => it.chapters).filter((it) => it.classNumber === classNumber).map((it) => it.id));

export function toggleChapterFinished(covered, chapterId) {
  const out = new Set(covered);
  if (covered.has(chapterId)) out.delete(chapterId); else out.add(chapterId);
  return out;
}

function toggleAll(covered, ids) {
  if (ids.length === 0) return covered;
  const out = new Set(covered);
  if (ids.every((it) => covered.has(it))) ids.forEach((it) => out.delete(it));
  else ids.forEach((it) => out.add(it));
  return out;
}

/**
 * What "finished" becomes when the starting point changes. A dropper starts with every chapter
 * finished once. Moving off dropper clears that default, but never real progress.
 * Returns [Set, Boolean].
 */
export function finishedAfterStartingPointChange({ point, current, autoFilled, keepExisting, allChapterIds }) {
  if (keepExisting && current.size > 0) return [current, false];
  if (point === StartingPoint.DROPPER && (current.size === 0 || autoFilled)) return [allChapterIds, true];
  if (point !== StartingPoint.DROPPER && autoFilled) return [new Set(), false];
  return [current, autoFilled];
}

/** Share of chapters marked finished, rounded down. */
export const finishedPercent = (covered, allChapterIds) =>
  (allChapterIds.size === 0 ? 0 : idiv([...allChapterIds].filter((it) => covered.has(it)).length * 100, allChapterIds.size));
