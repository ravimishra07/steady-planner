// Port of domain/ChapterStage.kt
import { dataClass, enumClass, isBlank, maxOfOrNull } from './kt.js';
import { flatten } from './ExamPack.js';

/** Per-chapter stages. Only LEARNED changes coverage (stored as TopicProgress, not a stage row). */
export const ChapterStage = enumClass(['LEARNED', 'NCERT', 'PRACTICE', 'PYQ', 'REVISED', 'CUSTOM']);

/** One stored stage mark. count matters only for REVISED. */
export const ChapterStageMark = dataClass({ attemptId: undefined, chapterId: undefined, stage: undefined, count: undefined, updatedAtEpochMs: undefined });

/** renamed: Map<ChapterStage,String>, hidden: Set<ChapterStage>, customLabel: String? */
export const StageLabels = dataClass(
  { renamed: new Map(), hidden: new Set(), customLabel: null },
  {
    /** The stages a chapter row shows, in order. The custom stage needs a name before it appears. */
    visibleStages() {
      return ChapterStage.entries.filter((stage) =>
        !this.hidden.has(stage) && (stage !== ChapterStage.CUSTOM || !isBlank(this.customLabel)));
    },
    /** The student's label for [stage], or null to use the default. */
    labelFor(stage) {
      if (stage === ChapterStage.CUSTOM) return !isBlank(this.customLabel) ? this.customLabel : null;
      const r = this.renamed.get(stage);
      return r != null && !isBlank(r) ? r : null;
    },
  },
);

/** revisionStates: Map<String, RevisionState> */
export function chapterRevisionCount(chapter, revisionStates, manualCount) {
  const focusCount = maxOfOrNull(flatten(chapter), (n) => revisionStates.get(n.id)?.revisionCount ?? 0) ?? 0;
  return focusCount + manualCount;
}
