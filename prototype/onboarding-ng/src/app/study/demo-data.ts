import { OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { PACK, Chapter } from '../onboarding/exam-pack';
import { ChapterStat, LoggedSession, StudyStore, Task, dateKey } from './study-store';
import { Recall, dueDate } from './retention';

/**
 * A fabricated three weeks of use. Nothing here is real study data — it exists
 * so the screens can be judged with history in them: logged sittings, revision
 * rounds and accuracy. Loaded and cleared from the More tab.
 */

/** Deterministic, so the demo looks the same every time it is loaded. */
function rng(seed: number): () => number {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

const DAYS = 21;
/** Chapters per subject the demo has worked through. */
const FINISHED = 6;

interface Built {
  sessions: LoggedSession[];
  stats: Map<string, ChapterStat>;
  done: Set<string>;
}

export function buildDemo(): Built {
  const random = rng(20260830);
  const sessions: LoggedSession[] = [];
  const stats = new Map<string, ChapterStat>();
  const done = new Set<string>();

  const bySubject = PACK.subjects.map((s) => s.sections.flatMap((sec) => sec.chapters));

  // The chapters the demo account has been through, in coaching order.
  const covered: Chapter[] = [];
  for (let i = 0; i < FINISHED; i++) {
    for (const chapters of bySubject) if (chapters[i]) covered.push(chapters[i]);
  }
  // One chapter per subject left half-finished — a real syllabus is ragged.
  const partial = bySubject.map((chapters) => chapters[FINISHED]).filter(Boolean);

  const blank: ChapterStat = {
    revisions: 0,
    attempted: 0,
    correct: 0,
    lastTouched: null,
    recall: null,
    dueKey: null,
  };

  /** Mirrors StudyStore.log: every sitting re-schedules the next pass. */
  const touch = (id: string, date: Date, change: Partial<ChapterStat>, recall?: Recall) => {
    const prev = stats.get(id) ?? blank;
    const next: ChapterStat = { ...prev, ...change };
    next.recall = recall ?? next.recall;
    next.dueKey = dateKey(dueDate(date, next.revisions, next.recall));
    stats.set(id, next);
  };

  /** Weighted so most sittings feel okay, a few either way. */
  const recallFor = (): Recall => {
    const r = random();
    return r < 0.22 ? 'shaky' : r < 0.78 ? 'okay' : 'solid';
  };

  const today = startOfToday();
  let cursor = 0;

  for (let back = DAYS; back >= 1; back--) {
    const date = addDays(today, -back);
    const key = dateKey(date);
    const sunday = date.getDay() === 0;
    // Not every day gets studied. That is the honest part of the picture.
    if (!sunday && random() < 0.15) continue;

    const count = sunday ? 2 : 3 + Math.floor(random() * 2);

    for (let i = 0; i < count; i++) {
      const chapter = covered[cursor % covered.length];
      const task: Task = i === 0 ? 'Learn' : i === count - 1 ? 'Revise' : 'Practice';
      const minutes = task === 'Learn' ? 45 : task === 'Practice' ? 60 : 30;

      if (task === 'Learn') {
        const next = chapter.subtopics.find((t) => !done.has(t.id));
        if (next) done.add(next.id);
        sessions.push({
          id: `demo-${key}-${i}`,
          dateKey: key,
          chapterId: chapter.id,
          subtopicId: next?.id,
          title: next?.name ?? chapter.name,
          task,
          minutes,
        });
        touch(chapter.id, date, { lastTouched: key }, recallFor());
      } else if (task === 'Practice') {
        const attempted = 30 + Math.floor(random() * 20);
        // Accuracy drifts up over the three weeks, 58% to about 78%.
        const rate = 0.58 + (1 - back / DAYS) * 0.2 + random() * 0.06;
        const correct = Math.round(attempted * Math.min(0.92, rate));
        const prev = stats.get(chapter.id);
        sessions.push({
          id: `demo-${key}-${i}`,
          dateKey: key,
          chapterId: chapter.id,
          title: chapter.name,
          task,
          minutes,
          attempted,
          correct,
        });
        touch(chapter.id, date, {
          lastTouched: key,
          attempted: (prev?.attempted ?? 0) + attempted,
          correct: (prev?.correct ?? 0) + correct,
        });
      } else {
        const prev = stats.get(chapter.id);
        sessions.push({
          id: `demo-${key}-${i}`,
          dateKey: key,
          chapterId: chapter.id,
          title: chapter.name,
          task,
          minutes,
        });
        touch(chapter.id, date, {
          lastTouched: key,
          revisions: Math.min(4, (prev?.revisions ?? 0) + 1),
        }, recallFor());
      }

      cursor++;
    }
  }

  // The chapters the account counts as finished, ticked all the way through.
  for (const chapter of covered) {
    if (chapter.subtopics.length === 0) done.add(chapter.id);
    else for (const t of chapter.subtopics) done.add(t.id);
  }
  for (const chapter of partial) {
    const half = Math.ceil(chapter.subtopics.length / 2);
    chapter.subtopics.slice(0, half).forEach((t) => done.add(t.id));
    if (chapter.subtopics.length === 0) continue;
    touch(chapter.id, addDays(today, -1), { lastTouched: dateKey(addDays(today, -1)) });
  }

  return { sessions, stats, done };
}

export function loadDemo(onboarding: OnboardingStore, study: StudyStore): void {
  const demo = buildDemo();
  study.sessions.set(demo.sessions);
  study.stats.set(demo.stats);
  study.extras.set([]);
  onboarding.doneUnits.set(demo.done);
}

export function clearDemo(onboarding: OnboardingStore, study: StudyStore): void {
  study.sessions.set([]);
  study.stats.set(new Map());
  study.extras.set([]);
  onboarding.doneUnits.set(new Set());
}
