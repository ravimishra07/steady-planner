// Port of feature/onboarding/model/OnboardingUiState.kt
// LocalDate = 'YYYY-MM-DD'; Set<String> = JS Set; @StringRes labelRes = string name in res/values/strings.js.
import { dataClass, enumClass, lazyDefault, coerceAtLeast } from '../../../domain/kt.js';
import { DEFAULT_NEET_EXAM_DATE, defaultFinishByDate } from '../../../domain/ExamDates.js';
import { NEET_EXAM_ID } from '../../../domain/ExamPack.js';
import { PlanMode } from '../../../domain/PlanMode.js';
import { today, daysBetween } from '../../../domain/time.js';
// Cyclic with OnboardingStep.js; both only read each other lazily (defaults / getters).
import { OnboardingStep } from './OnboardingStep.js';

export const WORK_FULLTIME = 'ft';
export const WORK_WORKING = 'job';
export const WORK_COLLEGE = 'col';
export const DEFAULT_WAKE_MINUTE = 6 * 60;
export const DEFAULT_SLEEP_MINUTE = 23 * 60;
export const PROGRESS_SEGMENTS = 7;

export const OnboardingUiState = dataClass(
  {
    loading: true,
    editMode: false,
    step: lazyDefault(() => OnboardingStep.Appearance),
    examId: NEET_EXAM_ID,
    coachingId: '',
    commitments: lazyDefault(() => defaultCommitments()),
    wakeMinute: DEFAULT_WAKE_MINUTE,
    sleepMinute: DEFAULT_SLEEP_MINUTE,
    /** The NEET date. Distinct from targetDate, the student's finish-by goal. */
    examDate: DEFAULT_NEET_EXAM_DATE,
    /** False until NTA announces the date: the UI says "expected, not yet announced". */
    examDateOfficial: false,
    /** The date the app ships with (or RemoteConfig supplies). */
    configExamDate: DEFAULT_NEET_EXAM_DATE,
    planMode: lazyDefault(() => PlanMode.DEADLINE),
    /** The finish-by date. Always on or before examDate and at least a week out. */
    targetDate: lazyDefault(() => defaultFinishByDate(DEFAULT_NEET_EXAM_DATE, today())),
    workId: WORK_COLLEGE,
    weekdayHours: 4,
    weekendHours: 7,
    studyPlace: '',
    useProvidedSyllabus: true,
    selectedChapterIds: lazyDefault(() => new Set()),
    /** Chapters the student has already finished (Learned). Saved when the plan is built. */
    coveredChapterIds: lazyDefault(() => new Set()),
    /** "Which class / attempt?" Null until the student answers it. */
    startingPoint: null,
    /** True while coveredChapterIds is still the dropper default nobody has touched. */
    coveredIsAutoFilled: false,
    syllabusSubjects: [],
    /** Deadline mode: PlanFit | null. */
    fit: null,
    fitLoading: false,
    /** Steady mode: projected finish date | null. */
    projectedFinish: null,
    projectionLoading: false,
    finishing: false,
    finishFailed: false,
  },
  {
    get canGoBack() { return this.step !== OnboardingStep.Appearance; },
    /** True when the exam date is the configured one, so the "expected / official" label applies to it. */
    get examDateIsConfigured() { return this.examDate === this.configExamDate; },
    get daysUntilTarget() { return coerceAtLeast(daysBetween(today(), this.targetDate), 1); },
  },
);

export const CoverageBaseline = enumClass(['STARTING_FRESH', 'PARTLY_COVERED', 'MOSTLY_REVISION']);

/** days: Set<Int>, Sunday = 0, Monday = 1, through Saturday = 6. */
export const OnboardingCommitment = dataClass({
  id: undefined, kind: undefined, labelRes: undefined, startMinute: undefined, endMinute: undefined, days: undefined, customLabel: null,
});

export const OnboardingSyllabusSubject = dataClass({ id: undefined, name: undefined, chapters: undefined });
export const OnboardingSyllabusChapter = dataClass({ id: undefined, name: undefined, classNumber: undefined });

export const DayShapeOption = dataClass({ id: undefined, labelRes: undefined, weekdayHours: undefined, weekendHours: undefined });

export const DAY_SHAPES = Object.freeze([
  DayShapeOption({ id: WORK_FULLTIME, labelRes: 'onboarding_shape_fulltime', weekdayHours: 8, weekendHours: 8 }),
  DayShapeOption({ id: WORK_WORKING, labelRes: 'onboarding_shape_working', weekdayHours: 3, weekendHours: 8 }),
  DayShapeOption({ id: WORK_COLLEGE, labelRes: 'onboarding_shape_college', weekdayHours: 4, weekendHours: 7 }),
]);

const range = (a, b) => new Set(Array.from({ length: b - a + 1 }, (_, i) => a + i));

export function defaultCommitments() {
  return [
    OnboardingCommitment({ id: 'seed-lunch', kind: 'meal', labelRes: 'onboarding_commitment_lunch', startMinute: 13 * 60, endMinute: 14 * 60, days: range(0, 6) }),
    OnboardingCommitment({ id: 'seed-dinner', kind: 'meal', labelRes: 'onboarding_commitment_dinner', startMinute: 20 * 60 + 30, endMinute: 21 * 60 + 30, days: range(0, 6) }),
  ];
}

export function commitmentsForStudyMode(current, modeId) {
  const withoutModeSeed = current.filter((it) => !(it.id === 'seed-school' || it.id === 'seed-coaching'));
  let seeded = null;
  if (modeId === 'school') {
    seeded = OnboardingCommitment({ id: 'seed-school', kind: 'school', labelRes: 'onboarding_commitment_school', startMinute: 8 * 60, endMinute: 14 * 60, days: range(1, 6) });
  } else if (modeId === 'coaching') {
    seeded = OnboardingCommitment({ id: 'seed-coaching', kind: 'coaching', labelRes: 'onboarding_commitment_coaching', startMinute: 16 * 60, endMinute: 19 * 60, days: range(1, 6) });
  }
  return seeded ? [seeded, ...withoutModeSeed] : withoutModeSeed;
}
