// Port of feature/home HomeUiState.kt. Dates are 'YYYY-MM-DD'.
import { dataClass, enumClass, lazyDefault } from '../../domain/kt.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { today } from '../../domain/time.js';

export const WeekDayUi = dataClass({ date: undefined, dayOfMonth: undefined, selected: undefined, status: undefined });

export const HomeSheet = enumClass(['None', 'PickTopic', 'PickDuration']);

export const StudyPickerLevel = enumClass(['Subjects', 'Topics', 'Subtopics']);

export const PendingTopic = dataClass(
  {
    nodeKey: undefined, title: undefined, sectionName: undefined, subjectId: undefined,
    topicPath: '', activityType: lazyDefault(() => StudyActivityType.LEARN),
  },
  { get isRevision() { return this.activityType === StudyActivityType.REVISION; } },
);

export const ActiveSprintUi = dataClass({ sessionId: undefined, title: undefined, remainingSec: undefined, durationMinutes: undefined });

export const MissedDayRecoveryUi = dataClass({
  missedCount: undefined, availableTodayMinutes: undefined, nextSessionId: undefined, nextSessionTitle: undefined, canStartNow: undefined,
});

/** Deadline mode: part of the remaining work cannot be scheduled before finish-by. Estimate, until calibrated. */
export const PlanFitUi = dataClass({ shortByMinutes: undefined, finishBy: undefined, calibrated: undefined });

/** "You missed N blocks. Rebalance the next days?" */
export const MissedBlocksUi = dataClass({ count: undefined, since: undefined });

/** "Start fresh from today". Carries no count on purpose: nothing on it says how much was missed. */
export const FreshStartUi = dataClass({ since: undefined });

/** One chapter the student can say their batch taught today. */
export const CoachingChapterOption = dataClass({ chapterId: undefined, title: undefined, subjectId: undefined, subjectName: undefined });

/** "Physics: 23 lectures, 41 DPPs behind". Numbers from Metrics.coachingBacklogMetrics. */
export const CoachingBacklogRowUi = dataClass({ subjectName: undefined, lectures: undefined, dpps: undefined });

/** Coaching on Today (§4.5, §5.4). Null when the student has no counts and no batch position. */
export const CoachingTodayUi = dataClass({
  batchChapterId: undefined, batchChapterTitle: undefined, batchLecture: undefined, chapters: undefined,
  backlog: undefined, clearDate: undefined, liveSplit: undefined,
});

export const HomeUiState = dataClass({
  loading: true,
  hasPlan: false,
  selectedDate: lazyDefault(() => today()),
  monthTitle: '',
  weekDays: [],
  monthDays: [],
  calendarExpanded: false,
  selectedIsToday: true,
  selectedDayLabel: '',
  dayBudgetMinutes: 0,
  completionPercent: 0,
  dayTimeline: [],
  sections: [],
  sheet: lazyDefault(() => HomeSheet.None),
  pickerLevel: lazyDefault(() => StudyPickerLevel.Subjects),
  pickerSectionIndex: null,
  pickerTopicPath: [],
  pickerQuery: '',
  pendingTopic: null,
  /** When this topic's next revision falls due if the pending session is completed unrated; null for a custom topic. */
  pendingRevisionDue: null,
  selectedDurationMinutes: 30,
  customEndMinuteOfDay: null,
  activeSprint: null,
  completedTodayMinutes: 0,
  plannedTodayMinutes: 0,
  missedDayRecovery: null,
  placementIssue: null,
  daysUntilExam: 0,
  syllabusPercent: 0,
  planFit: null,
  missedBlocks: null,
  /** Two or more missed days, or a broken streak: one "Start fresh from today" card instead of the missed prompts. */
  freshStart: null,
  /** The student started fresh today: no catch-up count on Today for the rest of the day. */
  startedFreshToday: false,
  /** A past day with logged study is selected: Today links to "What you studied" for it. */
  canOpenDayLog: false,
  coaching: null,
});
