// Port of feature/progress/LogSessionUiState.kt
import { dataClass, lazyDefault } from '../../domain/kt.js';
import { today } from '../../domain/time.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { SessionEntry, MANUAL_SESSION_DEFAULT_MINUTES } from '../../domain/ManualSession.js';

/** "Log study I did offline" and "Edit session" (PRODUCTION_V1_PLAN §6.3) share this form. */
export const LogSessionUiState = dataClass(
  {
    loading: true,
    isEdit: false,
    subjects: [],
    subjectId: null,
    chapterId: null,
    activityType: lazyDefault(() => StudyActivityType.LEARN),
    durationMinutes: MANUAL_SESSION_DEFAULT_MINUTES,
    date: lazyDefault(() => today()),
    startMinuteOfDay: 0,
    saving: false,
    saveFailed: false,
    /** The session is gone or there is no plan; the form cannot be used. */
    unavailable: false,
    /** Saved or deleted: the screen closes. */
    finished: false,
  },
  {
    get entry() {
      return SessionEntry({
        nodeId: this.chapterId, subjectId: this.subjectId, activityType: this.activityType,
        durationMinutes: this.durationMinutes, date: this.date, startMinuteOfDay: this.startMinuteOfDay,
      });
    },
    get canSave() { return !this.loading && !this.saving && !this.unavailable && this.entry.isValid; },
    get chapters() { return this.subjects.find((it) => it.id === this.subjectId)?.chapters ?? []; },
    get chapterTitle() { return this.chapters.find((it) => it.id === this.chapterId)?.title ?? null; },
  },
);

export const SubjectChoice = dataClass({ id: undefined, name: undefined, chapters: undefined });

export const ChapterChoice = dataClass({ id: undefined, title: undefined });
