// Port of domain/ChapterTitles.kt
import { dataClass } from './kt.js';

/** The student's own name for a chapter; progress keys on the stable chapter id. */
export const ChapterTitleOverride = dataClass({ attemptId: undefined, chapterId: undefined, customTitle: undefined });

/** Longest custom chapter name accepted. */
export const MAX_CHAPTER_TITLE_LENGTH = 80;

/** ExamPack.withChapterTitles(titles: Map<String,String>) — the one display-title resolver. */
export function withChapterTitles(pack, titles) {
  const t = titles instanceof Map ? titles : new Map(Object.entries(titles ?? {}));
  if (t.size === 0) return pack;
  return pack.copy({
    subjects: pack.subjects.map((subject) =>
      subject.copy({
        nodes: subject.nodes.map((node) => {
          const v = t.get(node.id)?.trim();
          return v ? node.copy({ title: v }) : node;
        }),
      }),
    ),
  });
}

/** A rename input cleaned for storage; null means "back to the original name". */
export function cleanChapterTitle(input, originalTitle) {
  if (input == null) return null;
  const v = input.trim().slice(0, MAX_CHAPTER_TITLE_LENGTH);
  return v.length > 0 && v !== originalTitle ? v : null;
}
