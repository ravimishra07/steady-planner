import { Chapter, PACK, Subtopic, subjectNameOf } from '../onboarding/exam-pack';
import { Commitment, commitmentsOn } from '../onboarding/commitments';
import { Task } from '../study/study-store';

export interface FixedBlock {
  kind: 'fixed';
  startMinute: number;
  minutes: number;
  title: string;
  subject: string;
}

export interface StudyBlock {
  kind: 'study';
  startMinute: number;
  minutes: number;
  task: Task;
  /** The subtopic, when there is one — the unit a sitting actually covers. */
  title: string;
  /** The chapter it belongs to, shown as the line under the title. */
  context: string;
  chapterId: string;
  subtopicId?: string;
  questions?: number;
  done: boolean;
  /** Days past due, when this block exists because retention has slipped. */
  overdue?: number;
}

export interface GapBlock {
  kind: 'gap';
  startMinute: number;
  minutes: number;
}

export interface BreakBlock {
  kind: 'break';
  startMinute: number;
  minutes: number;
}

export type Block = FixedBlock | StudyBlock | GapBlock | BreakBlock;

export interface Window {
  startMinute: number;
  minutes: number;
}

export interface Candidate {
  task: Task;
  chapter: Chapter;
  subtopic?: Subtopic;
  /** Preferred length; the packer may shorten it to fit a window. */
  minutes: number;
  /** Days past due, for a revision that is late. */
  overdue?: number;
}

/** A sitting shorter than this teaches nothing; longer than this loses focus. */
const MIN_SESSION = 25;
const MAX_SESSION = 90;
/** Default break between two sittings; the caller can override it. */
const BREAK = 15;
/** Below this a leftover window is not worth offering as free time. */
const MIN_GAP = 30;
/** Nobody opens a book the minute they wake up. */
const MORNING_BUFFER = 45;

/** The parts of the waking day nothing else has claimed. */
export function freeWindows(
  commitments: readonly Commitment[],
  weekday: number,
  wake: number,
  sleep: number,
): Window[] {
  const out: Window[] = [];
  let cursor = wake + MORNING_BUFFER;

  for (const c of commitmentsOn(commitments, weekday)) {
    const start = Math.max(c.startMinute, wake);
    const end = Math.min(c.startMinute + c.minutes, sleep);
    if (end <= cursor) continue;
    if (start - cursor >= MIN_SESSION) out.push({ startMinute: cursor, minutes: start - cursor });
    cursor = Math.max(cursor, end);
  }

  if (sleep - cursor >= MIN_SESSION) out.push({ startMinute: cursor, minutes: sleep - cursor });
  return out;
}

/**
 * Lay the day out. Candidates are consumed in order into the free windows;
 * what is left over becomes free time, and sittings inside one window are
 * separated by a short break rather than by an hour of nothing.
 *
 * The old version emitted a fixed number of equal blocks with equal 45-minute
 * holes between them, which is why every day looked the same.
 */
export function layOutDay(
  windows: Window[],
  commitments: readonly Commitment[],
  weekday: number,
  candidates: Candidate[],
  targetMinutes: number,
  coachingName: string,
  breakMinutes: number = BREAK,
): Block[] {
  const blocks: Block[] = commitmentsOn(commitments, weekday).map((c) => ({
    kind: 'fixed' as const,
    startMinute: c.startMinute,
    minutes: c.minutes,
    title: c.label,
    subject: c.kind === 'coaching' ? coachingName : kindLabel(c.kind),
  }));

  // Study is spread across the day's windows in proportion to their size,
  // rather than dumped into the first one — an evening window after coaching
  // is where half the work actually happens.
  const free = windows.reduce((n, w) => n + w.minutes, 0) || 1;
  let carried = 0;
  let next = 0;

  for (const window of windows) {
    let cursor = window.startMinute;
    const end = window.startMinute + window.minutes;
    let placedHere = 0;
    let remaining = Math.round((targetMinutes * window.minutes) / free) + carried;

    while (remaining >= MIN_SESSION && next < candidates.length && end - cursor >= MIN_SESSION) {
      if (placedHere > 0) {
        if (end - cursor < breakMinutes + MIN_SESSION) break;
        blocks.push({ kind: 'break', startMinute: cursor, minutes: breakMinutes });
        cursor += breakMinutes;
      }

      const candidate = candidates[next++];
      const minutes = round5(
        Math.min(candidate.minutes, MAX_SESSION, remaining, end - cursor),
      );
      if (minutes < MIN_SESSION) break;

      blocks.push({
        kind: 'study',
        startMinute: cursor,
        minutes,
        task: candidate.task,
        title: candidate.subtopic?.name ?? candidate.chapter.name,
        context: candidate.subtopic
          ? `${subjectLabel(candidate.chapter)} · ${candidate.chapter.name}`
          : subjectLabel(candidate.chapter),
        chapterId: candidate.chapter.id,
        subtopicId: candidate.subtopic?.id,
        questions: candidate.task === 'Practice' ? Math.round(minutes / 1.2) : undefined,
        done: false,
        overdue: candidate.overdue,
      });

      cursor += minutes;
      remaining -= minutes;
      placedHere++;
    }

    carried = Math.max(0, remaining);

    if (end - cursor >= MIN_GAP) {
      blocks.push({ kind: 'gap', startMinute: cursor, minutes: end - cursor });
    }
  }

  return blocks.sort((a, b) => a.startMinute - b.startMinute);
}

function round5(minutes: number): number {
  return Math.floor(minutes / 5) * 5;
}

export function subjectLabel(chapter: Chapter): string {
  return subjectNameOf(chapter.id);
}

function kindLabel(kind: Commitment['kind']): string {
  return {
    school: 'School',
    coaching: 'Coaching',
    lecture: 'Lectures',
    tuition: 'Tuition',
    work: 'Work',
    meal: 'Break',
    other: 'Fixed',
  }[kind];
}
