import { Chapter, PACK, Subject, subjectById } from './exam-pack';
import { OrderMode } from './state';

/**
 * The order a subject is worked through, and where the class has got to.
 * Every queue in the app reads through here, so "up next" can never be a
 * chapter the student has not been taught.
 */

/** Marks the paper gives a chapter, spread evenly inside its subject. */
export function marksOf(chapter: Chapter): number {
  const subject = subjectById(chapter.id.split('.')[0]);
  if (!subject) return 0;
  // Marks spread evenly across whatever that subject currently holds.
  const count = Math.max(1, chaptersIn(chapter.id.split('.')[0]));
  return Math.round((subject.questions * 4) / count);
}

/** What an hour spent on a chapter is worth. */
export function marksPerHour(chapter: Chapter): number {
  return marksOf(chapter) / Math.max(0.5, chapter.hours);
}

export function chaptersOf(subject: Subject): Chapter[] {
  return subject.sections.flatMap((s) => s.chapters);
}

/** The subject's chapters in the order the student wants them. */
export function orderedChapters(
  subject: Subject,
  mode: OrderMode,
  custom: readonly string[] | undefined,
): Chapter[] {
  const chapters = chaptersOf(subject);

  switch (mode) {
    case 'yield':
      return [...chapters].sort((a, b) => marksPerHour(b) - marksPerHour(a));
    case 'short':
      return [...chapters].sort((a, b) => a.hours - b.hours);
    case 'custom': {
      if (!custom || custom.length === 0) return chapters;
      const index = new Map(custom.map((id, i) => [id, i]));
      // Anything the saved order does not mention keeps its book position,
      // after everything it does — a pack update must not drop chapters.
      return [...chapters].sort(
        (a, b) => (index.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (index.get(b.id) ?? Number.MAX_SAFE_INTEGER),
      );
    }
    default:
      return chapters;
  }
}

/**
 * The part of the subject that is actually available: everything up to and
 * including the taught marker, in the chosen order. No marker means all of it.
 */
export function availableChapters(
  subject: Subject,
  mode: OrderMode,
  custom: readonly string[] | undefined,
  taughtUpTo: string | null,
): Chapter[] {
  const ordered = orderedChapters(subject, mode, custom);
  if (!taughtUpTo) return ordered;
  const cut = ordered.findIndex((c) => c.id === taughtUpTo);
  return cut === -1 ? ordered : ordered.slice(0, cut + 1);
}

export function subjectOfChapter(chapterId: string): Subject | undefined {
  const id = chapterId.split('.')[0];
  return PACK.subjects.find((s) => s.id === id);
}

/** How many chapters a subject holds in the bundled pack, for the marks split. */
function chaptersIn(subjectId: string): number {
  const subject = PACK.subjects.find((s) => s.id === subjectId);
  return subject ? subject.sections.reduce((n, sec) => n + sec.chapters.length, 0) : 1;
}
