// Port of domain/CoachingBacklog.kt — coaching backlog tracker (§5.4).
import { dataClass, enumClass, coerceIn, coerceAtLeast, sortedBy } from './kt.js';

export const BACKLOG_LECTURE_MINUTES = 90;
export const BACKLOG_DPP_MINUTES = 30;
export const DEFAULT_LIVE_BACKLOG_SPLIT = 60;
export const MAX_COACHING_COUNT = 99;

/** One chapter's coaching counts. lectures 1..lecturesDone are done. */
export const ChapterCoaching = dataClass(
  { chapterId: undefined, lectures: undefined, dpps: undefined, lecturesDone: undefined, dppsDone: undefined },
  {
    normalized() {
      const l = coerceIn(this.lectures, 0, MAX_COACHING_COUNT);
      const d = coerceIn(this.dpps, 0, MAX_COACHING_COUNT);
      return this.copy({ lectures: l, dpps: d, lecturesDone: coerceIn(this.lecturesDone, 0, l), dppsDone: coerceIn(this.dppsDone, 0, d) });
    },
    get isEmpty() { return this.lectures === 0 && this.dpps === 0; },
  },
);

/** "My batch is currently at: <chapter>, lecture k". */
export const BatchPosition = dataClass({ chapterId: undefined, lecture: undefined, updatedAtEpochMs: undefined });

export const CoachingBacklog = dataClass(
  { lectures: undefined, dpps: undefined },
  {
    get isEmpty() { return this.lectures <= 0 && this.dpps <= 0; },
    plus(other) { return CoachingBacklog({ lectures: this.lectures + other.lectures, dpps: this.dpps + other.dpps }); },
  },
);
CoachingBacklog.NONE = CoachingBacklog({ lectures: 0, dpps: 0 });

export const BacklogKind = enumClass(['LECTURE', 'DPP']);

/** One lecture or DPP the student is behind on. number is 1-based within its chapter. */
export const BacklogItem = dataClass({ chapterId: undefined, subjectId: undefined, kind: undefined, number: undefined });

const MAX_INT = 2147483647;

/** subjectOf: Map<chapterId, subjectId>. Returns Map<chapterId, [lectures, dpps]>. */
function taughtCounts(coaching, position, chapterOrder, subjectOf) {
  const index = new Map(chapterOrder.map((c, i) => [c, i]));
  const batchSubject = position != null ? subjectOf.get(position.chapterId) ?? null : null;
  const batchIndex = position != null ? index.get(position.chapterId) ?? null : null;
  const out = new Map();
  for (const chapter of [...coaching].map((c) => c.normalized())) {
    let taught;
    if (position == null || batchSubject == null || batchIndex == null) taught = [chapter.lectures, chapter.dpps];
    else if (chapter.chapterId === position.chapterId) {
      const upTo = coerceIn(position.lecture, 0, chapter.lectures);
      taught = [upTo, Math.min(upTo, chapter.dpps)];
    } else if (subjectOf.get(chapter.chapterId) !== batchSubject) taught = [chapter.lectures, chapter.dpps];
    else if ((index.get(chapter.chapterId) ?? MAX_INT) < batchIndex) taught = [chapter.lectures, chapter.dpps];
    else taught = [0, 0];
    out.set(chapter.chapterId, taught);
  }
  return out;
}

/** Lectures and DPPs behind the batch, by subject id: Map<subjectId, CoachingBacklog>. */
export function coachingBacklogBySubject(coaching, position, chapterOrder, subjectOf) {
  const taught = taughtCounts(coaching, position, chapterOrder, subjectOf);
  const grouped = new Map();
  for (const chapter of [...coaching].map((c) => c.normalized())) {
    const subject = subjectOf.get(chapter.chapterId);
    if (subject == null) continue;
    const t = taught.get(chapter.chapterId);
    if (t == null) continue;
    const part = CoachingBacklog({ lectures: coerceAtLeast(t[0] - chapter.lecturesDone, 0), dpps: coerceAtLeast(t[1] - chapter.dppsDone, 0) });
    grouped.set(subject, (grouped.get(subject) ?? CoachingBacklog.NONE).plus(part));
  }
  for (const [k, v] of [...grouped]) if (v.isEmpty) grouped.delete(k);
  return grouped;
}

/** Every backlog lecture and DPP as a plan item; subjects taken in turn. */
export function coachingBacklogItems(coaching, position, chapterOrder, subjectOf) {
  const taught = taughtCounts(coaching, position, chapterOrder, subjectOf);
  const index = new Map(chapterOrder.map((c, i) => [c, i]));
  const bySubject = new Map();
  const chs = sortedBy([...coaching].map((c) => c.normalized()).filter((c) => subjectOf.get(c.chapterId) != null),
    (c) => index.get(c.chapterId) ?? MAX_INT);
  for (const chapter of chs) {
    const subject = subjectOf.get(chapter.chapterId);
    const t = taught.get(chapter.chapterId);
    if (t == null) continue;
    const [lectures, dpps] = t;
    let queue = bySubject.get(subject);
    if (!queue) bySubject.set(subject, (queue = []));
    const last = Math.max(lectures, dpps);
    for (let k = 1; k <= last; k++) {
      if (k >= chapter.lecturesDone + 1 && k <= lectures) queue.push(BacklogItem({ chapterId: chapter.chapterId, subjectId: subject, kind: BacklogKind.LECTURE, number: k }));
      if (k >= chapter.dppsDone + 1 && k <= dpps) queue.push(BacklogItem({ chapterId: chapter.chapterId, subjectId: subject, kind: BacklogKind.DPP, number: k }));
    }
  }
  const queues = [...bySubject.values()].filter((q) => q.length > 0);
  const result = [];
  while (queues.some((q) => q.length > 0)) {
    for (const q of queues) { const x = q.shift(); if (x !== undefined) result.push(x); }
  }
  return result;
}

/** Minutes the backlog needs at the given block lengths. */
export const backlogMinutes = (backlog, lectureMin = BACKLOG_LECTURE_MINUTES, dppMin = BACKLOG_DPP_MINUTES) =>
  coerceAtLeast(backlog.lectures, 0) * lectureMin + coerceAtLeast(backlog.dpps, 0) * dppMin;

/** Minutes of a day that go to backlog when liveSplitPct percent goes to live work. */
export const backlogDailyMinutes = (dailyMinutes, liveSplitPct) =>
  Math.trunc((coerceAtLeast(dailyMinutes, 0) * (100 - coerceIn(liveSplitPct, 0, 100))) / 100);
