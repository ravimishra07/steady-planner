import { Chapter, Subject, chapterIsDone } from '../onboarding/exam-pack';
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
  subjects: readonly Subject[];
  capacityOn: (date: Date, revisionMinutes: number) => number;
  revisionMinutesOn: (date: Date) => number;
}

/**
 * Walks the subjects the way the day planner does and accumulates hours, so a
 * chapter's date is the date the plan would actually reach it.
 */
export function project(input: ProjectionInput): Landing[] {
  const queues = new Map<string, Chapter[]>();

  for (const subject of input.subjects) {
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

  const today = startOfToday();
  const out: Landing[] = [];
  const work = ROTATION.flatMap(() => [] as Chapter[]);
  let remaining = [...ROTATION];
  while (remaining.length) {
    const next: string[] = [];
    for (const id of remaining) {
      const chapter = queues.get(id)?.shift();
      if (chapter) work.push(chapter);
      if ((queues.get(id)?.length ?? 0) > 0) next.push(id);
    }
    remaining = next;
  }

  let day = 0;
  let minutes = 0;
  for (const chapter of work) {
    let needed = chapter.hours * 60;
    while (needed > 0 && day < 1460) {
      const date = addDays(today, day);
      if (minutes <= 0) minutes = input.capacityOn(date, input.revisionMinutesOn(date));
      if (minutes <= 0) { day++; continue; }
      const used = Math.min(needed, minutes);
      needed -= used;
      minutes -= used;
      if (needed > 0) day++;
    }
    const landingDay = day;
    const date = addDays(today, landingDay);
    out.push({ chapter, day: landingDay, date, fits: date <= input.examDate });
    if (minutes <= 0) day++;
  }

  return out;
}

/** Everything that lands after the exam — the honest "this will not happen". */
export function overflow(landings: Landing[]): Landing[] {
  return landings.filter((l) => !l.fits);
}
