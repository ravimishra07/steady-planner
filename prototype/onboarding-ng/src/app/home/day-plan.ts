import { Chapter, PACK, Subtopic, chapterIsDone } from '../onboarding/exam-pack';
import { availableChapters } from '../onboarding/sequence';
import { ChapterStat, Task } from '../study/study-store';
import { overdueDays } from '../study/retention';
import { Candidate } from './scheduler';

/** How long each kind of sitting wants to be. */
const LENGTH: Record<Task, number> = { Learn: 45, Practice: 60, Revise: 30 };

/**
 * Subject rotation follows the paper: Biology is 90 of the 180 questions, so
 * Botany and Zoology take half the slots.
 */
const ROTATION = ['botany', 'physics', 'zoology', 'chemistry'];

export interface PlanInput {
  doneUnits: ReadonlySet<string>;
  /** Chapters the user has set aside; the plan never picks them. */
  parked: ReadonlySet<string>;
  /**
   * Per subject, the chapters that are in play — ordered by the rule the
   * student chose and cut at how far their class has reached.
   */
  available: (subjectId: string) => Chapter[];
  learnedSubtopics: ReadonlySet<string>;
  stat: (chapterId: string) => ChapterStat;
  /** The day being planned, which is what "due" is measured against. */
  date: Date;
  /** Slots to fill; more than needed is fine, the packer stops when full. */
  slots: number;
  allChapters: readonly Chapter[];
}

/**
 * What today should contain. Revision that has fallen due comes first — the
 * whole point of tracking retention is that the plan acts on it — then new
 * material, then practice on what was just covered.
 */
export function dayCandidates(input: PlanInput): Candidate[] {
  const out: Candidate[] = [];

  const learn = learnQueue(input);
  const practice = practiceQueue(input, learn);
  const revise = reviseQueue(input);

  // Anything badly overdue is not negotiable: it goes at the top of the day.
  while (revise.length > 0 && (revise[0].overdue ?? 0) >= 3 && out.length < Math.ceil(input.slots / 2)) {
    out.push(revise.shift()!);
  }

  const order: Task[] = ['Learn', 'Revise', 'Practice', 'Learn'];
  const queues: Record<Task, Candidate[]> = { Learn: learn, Practice: practice, Revise: revise };

  let i = 0;
  while (out.length < input.slots && (learn.length || practice.length || revise.length)) {
    const task = order[i++ % order.length];
    const pick = queues[task].shift();
    if (pick) out.push(pick);
    if (i > input.slots * 4) break;
  }

  return out.slice(0, input.slots);
}

/** The next unread section heading in each subject, rotated by paper weight. */
function learnQueue(input: PlanInput): Candidate[] {
  const out: Candidate[] = [];
  for (let round = 0; round < 4; round++) {
    for (const subjectId of ROTATION) {
      const chapters = input.available(subjectId);
      const found = nextSubtopic(chapters, input, round);
      if (found) out.push({ task: 'Learn', ...found, minutes: LENGTH.Learn });
    }
  }
  return out;
}

function nextSubtopic(
  chapters: Chapter[],
  input: PlanInput,
  skip: number,
): { chapter: Chapter; subtopic?: Subtopic } | null {
  let seen = 0;
  for (const chapter of chapters) {
    if (input.parked.has(chapter.id)) continue;
    if (chapter.subtopics.length === 0) {
      if (input.doneUnits.has(chapter.id)) continue;
      if (seen++ < skip) continue;
      return { chapter };
    }
    for (const subtopic of chapter.subtopics) {
      if (input.doneUnits.has(subtopic.id)) continue;
      if (seen++ < skip) continue;
      return { chapter, subtopic };
    }
  }
  return null;
}

/**
 * Practice belongs to chapters that have been started but are unproven. On a
 * first day nothing has been started, so today's own Learn blocks stand in —
 * reading a section and then working questions on it is one motion.
 */
function practiceQueue(input: PlanInput, learn: Candidate[]): Candidate[] {
  const started = input.allChapters.filter(
    (c) =>
      !input.parked.has(c.id) &&
      (input.stat(c.id).lastTouched !== null || chapterIsDone(c, input.doneUnits)),
  ).sort((a, b) => input.stat(a.id).attempted - input.stat(b.id).attempted);

  const pool = started.length > 0 ? started : dedupe(learn.map((c) => c.chapter));

  return pool
    .slice(0, 6)
    .map((chapter) => ({ task: 'Practice' as const, chapter, minutes: LENGTH.Practice }));
}

function dedupe(chapters: Chapter[]): Chapter[] {
  const seen = new Set<string>();
  return chapters.filter((c) => (seen.has(c.id) ? false : (seen.add(c.id), true)));
}

/**
 * Revision is whatever has come due, most decayed first. Nothing else decides
 * it — not the order chapters were learnt in, not how many passes they have had.
 */
function reviseQueue(input: PlanInput): Candidate[] {
  return input.allChapters.filter((c) => !input.parked.has(c.id))
    .map((chapter) => ({ chapter, stat: input.stat(chapter.id) }))
    .filter((row) => row.stat.lastTouched !== null && row.stat.dueKey !== null)
    .map((row) => ({ ...row, overdue: overdueDays(row.stat.dueKey, input.date) }))
    .filter((row) => row.overdue >= 0)
    .sort((a, b) => b.overdue - a.overdue)
    .slice(0, 8)
    .map((row) => ({
      task: 'Revise' as const,
      chapter: row.chapter,
      // A chapter that has slipped badly needs more than a ten-minute look.
      minutes: row.overdue >= 7 ? LENGTH.Revise + 15 : LENGTH.Revise,
      overdue: row.overdue,
    }));
}
