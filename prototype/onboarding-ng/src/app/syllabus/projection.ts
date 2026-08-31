import { Chapter, PACK, chapterIsDone } from '../onboarding/exam-pack';
import { OrderMode, addDays, startOfToday } from '../onboarding/state';
import { availableChapters } from '../onboarding/sequence';

/**
 * Where each chapter lands on the calendar at the pace actually being kept.
 * Totals do not help anyone decide what to drop — a date does, and so does the
 * line under which nothing happens before the exam.
 */

export interface Landing {
  chapter: Chapter;
  /** Days from today the chapter is projected to finish on. */
  day: number;
  date: Date;
  fits: boolean;
}

/** The planner works the subjects round-robin, weighted to the paper. */
const ROTATION = ['botany', 'physics', 'zoology', 'chemistry'];

export interface ProjectionInput {
  orderModes: ReadonlyMap<string, OrderMode>;
  customOrder: ReadonlyMap<string, string[]>;
  taughtUpTo: ReadonlyMap<string, string | null>;
  parked: ReadonlySet<string>;
  doneUnits: ReadonlySet<string>;
  hoursPerDay: number;
  examDate: Date;
}

/**
 * Walks the subjects the way the day planner does and accumulates hours, so a
 * chapter's date is the date the plan would actually reach it.
 */
export function project(input: ProjectionInput): Landing[] {
  const queues = new Map<string, Chapter[]>();

  for (const subject of PACK.subjects) {
    const available = availableChapters(
      subject,
      input.orderModes.get(subject.id) ?? 'book',
      input.customOrder.get(subject.id),
      input.taughtUpTo.get(subject.id) ?? null,
    );
    queues.set(
      subject.id,
      available.filter((c) => !input.parked.has(c.id) && !chapterIsDone(c, input.doneUnits)),
    );
  }

  const perDay = Math.max(0.25, input.hoursPerDay);
  const today = startOfToday();
  const out: Landing[] = [];
  let hours = 0;
  let guard = 0;

  while (guard++ < 500) {
    let placed = false;
    for (const subjectId of ROTATION) {
      const queue = queues.get(subjectId);
      if (!queue || queue.length === 0) continue;
      const chapter = queue.shift()!;
      hours += chapter.hours;
      const day = Math.ceil(hours / perDay);
      const date = addDays(today, day);
      out.push({ chapter, day, date, fits: date <= input.examDate });
      placed = true;
    }
    if (!placed) break;
  }

  return out;
}

/** Everything that lands after the exam — the honest "this will not happen". */
export function overflow(landings: Landing[]): Landing[] {
  return landings.filter((l) => !l.fits);
}
