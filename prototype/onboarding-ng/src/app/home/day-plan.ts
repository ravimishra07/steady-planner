import { ALL_CHAPTERS, Chapter, PACK, Subtopic, chapterIsDone } from '../onboarding/exam-pack';
import { ChapterStat, Task } from '../study/study-store';
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
  learnedSubtopics: ReadonlySet<string>;
  stat: (chapterId: string) => ChapterStat;
  /** Slots to fill; more than needed is fine, the packer stops when full. */
  slots: number;
}

/**
 * What today should contain: learn forward, practise what was just learnt,
 * revise what is going stale — in that order, rotated across subjects.
 */
export function dayCandidates(input: PlanInput): Candidate[] {
  const out: Candidate[] = [];

  const learn = learnQueue(input);
  const practice = practiceQueue(input, learn);
  const revise = reviseQueue(input);

  const order: Task[] = ['Learn', 'Practice', 'Learn', 'Revise'];
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
      const subject = PACK.subjects.find((s) => s.id === subjectId)!;
      const chapters = subject.sections.flatMap((sec) => sec.chapters);
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
  const started = ALL_CHAPTERS.filter(
    (c) => input.stat(c.id).lastTouched !== null || chapterIsDone(c, input.doneUnits),
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

/** Revision goes to finished chapters with the fewest passes, oldest first. */
function reviseQueue(input: PlanInput): Candidate[] {
  return ALL_CHAPTERS.filter((c) => chapterIsDone(c, input.doneUnits))
    .filter((c) => input.stat(c.id).revisions < 3)
    .sort((a, b) => {
      const sa = input.stat(a.id);
      const sb = input.stat(b.id);
      if (sa.revisions !== sb.revisions) return sa.revisions - sb.revisions;
      return (sa.lastTouched ?? '').localeCompare(sb.lastTouched ?? '');
    })
    .slice(0, 6)
    .map((chapter) => ({ task: 'Revise' as const, chapter, minutes: LENGTH.Revise }));
}
