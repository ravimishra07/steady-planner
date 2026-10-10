// Port of feature/syllabus/OrganiseUiState.kt
import { dataClass, lazyDefault } from '../../domain/kt.js';
import { PlanningOrder } from '../../domain/RollingPlan.js';

/** originalTitle defaults to title (Kotlin default argument referencing title). */
const OrganiseChapterRowBase = dataClass({
  id: undefined,
  subjectId: undefined,
  subjectName: undefined,
  title: undefined,
  /** The bundled (or custom-added) title, for "Reset to original name". */
  originalTitle: undefined,
  renamed: false,
  estimatedMinutes: undefined,
  excluded: undefined,
  custom: undefined,
  priority: null,
});
export function OrganiseChapterRow(props = {}) {
  return OrganiseChapterRowBase({ ...props, originalTitle: props.originalTitle !== undefined ? props.originalTitle : props.title });
}

/** date: 'YYYY-MM-DD' */
export const OrganiseDaySummary = dataClass({ date: undefined, plannedMinutes: undefined, blockCount: undefined });

export const OrganisePlanRow = dataClass({
  id: undefined, title: undefined, startMinuteOfDay: undefined, durationMinutes: undefined, isRevision: undefined,
});

export const OrganiseUiState = dataClass(
  {
    loading: true,
    saving: false,
    saveFailed: false,
    examId: '',
    selectedSubjectId: '',
    /** [id, name] pairs */
    subjects: [],
    chapters: [],
    week: [],
    todaySessions: [],
    beforeIncludedCount: 0,
    /** Uncovered minutes in the chapters in play, before and after the draft. */
    beforeRemainingMinutes: 0,
    remainingMinutes: 0,
    changedChapterCount: 0,
    planningOrder: lazyDefault(() => PlanningOrder.DEFAULT),
    orderChanged: false,
    dirty: false,
    saved: false,
    pyqFirstYear: 0,
    pyqLastYear: 0,
  },
  {
    get includedCount() { return this.chapters.filter((it) => !it.excluded).length; },
    get excludedCount() { return this.chapters.filter((it) => it.excluded).length; },
    get exactChangeCount() { return this.changedChapterCount + (this.orderChanged ? 1 : 0); },
  },
);
