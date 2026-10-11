// Port of feature/syllabus/SyllabusUiState.kt
import { dataClass, lazyDefault } from '../../domain/kt.js';
import { StageLabels } from '../../domain/ChapterStage.js';

export const SyllabusTreeRow = dataClass({
  key: undefined,
  name: undefined,
  estimatedMinutes: undefined,
  depth: undefined,
  hasChildren: undefined,
  expanded: undefined,
  tickState: undefined,
  percent: undefined,
  doneLeafCount: undefined,
  totalLeafCount: undefined,
  /** For each ancestor column (0 until depth): does that ancestor have a later sibling? */
  ancestorContinues: undefined,
  /** Is this row the last child among its own siblings? */
  isLastChild: undefined,
  subjectId: undefined,
  sectionName: undefined,
  topicPath: undefined,
  classNumber: null,
  isClassGroup: false,
  excluded: false,
  /** Set on chapter rows once the PYQ table has loaded. */
  priority: null,
  /** A top-level chapter row: carries the chapter menu. */
  isChapter: false,
  coaching: null,
  renamed: false,
  originalName: '',
  /** Chapter rows only: the student's visible stages, in order. */
  stageChips: [],
});

/** One stage chip on a chapter row. count is 0/1 for ticks, N for Revised. Null label = default name. */
export const StageChipUi = dataClass({ stage: undefined, customLabel: undefined, count: undefined });

export const SyllabusChapterStat = dataClass({ classNumber: undefined, complete: undefined });

export const SyllabusSubjectCard = dataClass({
  key: undefined,
  name: undefined,
  subjectId: undefined,
  percent: undefined,
  expanded: undefined,
  rows: undefined,
  /** The subject's first topic — what "play" on the card itself jumps into. */
  firstTopicKey: undefined,
  firstTopicTitle: undefined,
  doneChapterCount: undefined,
  totalChapterCount: undefined,
  totalEstimatedMinutes: undefined,
  questions: undefined,
  chapterStats: undefined,
  excludedCount: 0,
});

export const SyllabusUiState = dataClass({
  loading: true,
  subjects: [],
  allCount: 0,
  dueCount: 0,
  completedPercent: 0,
  isEmpty: false,
  /** Years the PYQ counts cover, for the "(2021–25)" label. Zero until loaded. */
  pyqFirstYear: 0,
  pyqLastYear: 0,
  stageLabels: lazyDefault(() => StageLabels()),
  /** Pro, or the paywall switched off remotely. Adding the custom stage needs it. */
  proUnlocked: false,
});
