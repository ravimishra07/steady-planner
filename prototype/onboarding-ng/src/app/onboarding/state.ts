import { Injectable, computed, signal } from '@angular/core';
import { ALL_CHAPTERS, chapterIsDone } from './exam-pack';

export type AccentId = 'blue' | 'purple' | 'green' | 'amber' | 'rose';
export type AppearanceId = 'light' | 'dark' | 'grey' | 'slate';

export interface Accent { id: AccentId; label: string; swatch: string; }
export interface Appearance { id: AppearanceId; label: string; swatch: string; ink: string; }

/** Mirrors AccentPalette.kt — the five selectable brand hues. */
export const ACCENTS: Accent[] = [
  { id: 'blue', label: 'Blue', swatch: '#2563eb' },
  { id: 'purple', label: 'Purple', swatch: '#7c3aed' },
  { id: 'green', label: 'Green', swatch: '#059669' },
  { id: 'amber', label: 'Amber', swatch: '#ea580c' },
  { id: 'rose', label: 'Rose', swatch: '#e11d48' },
];

/** Mirrors BackgroundAppearance.kt. */
export const APPEARANCES: Appearance[] = [
  { id: 'light', label: 'Light', swatch: '#fcfcff', ink: '#191c25' },
  { id: 'dark', label: 'Dark', swatch: '#0a0a0f', ink: '#f4f3f8' },
  { id: 'grey', label: 'Grey', swatch: '#f3f4f6', ink: '#1b1c20' },
  { id: 'slate', label: 'Slate', swatch: '#111820', ink: '#f0f4f8' },
];

export interface ExamOption { id: string; label: string; available: boolean; }

/** Mirrors ExamCatalog.kt — only entries with a syllabus tree are selectable. */
export const EXAMS: ExamOption[] = [
  { id: 'cgl', label: 'SSC CGL', available: true },
  { id: 'chsl', label: 'SSC CHSL', available: false },
  { id: 'ntpc', label: 'RRB NTPC', available: false },
  { id: 'neet', label: 'NEET UG', available: false },
  { id: 'jee', label: 'JEE Main', available: false },
  { id: 'ibps', label: 'IBPS PO', available: false },
];

export interface DayShape { id: string; label: string; weekday: number; weekend: number; }

/** Mirrors DAY_SHAPES in OnboardingUiState.kt. */
export const DAY_SHAPES: DayShape[] = [
  { id: 'ft', label: 'Full-time', weekday: 8, weekend: 8 },
  { id: 'job', label: 'Working', weekday: 3, weekend: 8 },
  { id: 'col', label: 'College', weekday: 4, weekend: 7 },
];

export interface CoachingOption { id: string; label: string; mode: string; icon: string; }

/** Institutes most named in 2026 NEET coaching rankings, plus a self-study exit. */
export const COACHINGS: CoachingOption[] = [
  { id: 'allen', label: 'Allen Career Institute', mode: 'Classroom · Kota', icon: 'school' },
  { id: 'aakash', label: 'Aakash Institute', mode: 'Classroom · Pan-India', icon: 'account_balance' },
  { id: 'pw', label: 'Physics Wallah', mode: 'Online', icon: 'play_lesson' },
  { id: 'narayana', label: 'Narayana', mode: 'Classroom · Hyderabad', icon: 'domain' },
  { id: 'chaitanya', label: 'Sri Chaitanya', mode: 'Classroom · Hyderabad', icon: 'apartment' },
  { id: 'resonance', label: 'Resonance', mode: 'Classroom · Kota', icon: 'science' },
  { id: 'unacademy', label: 'Unacademy', mode: 'Online', icon: 'cast_for_education' },
  { id: 'self', label: 'I self-study', mode: 'No institute', icon: 'person' },
];

export type DateMode = 'exam' | 'syllabus';

export function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

export function addDays(from: Date, n: number): Date {
  const d = new Date(from);
  d.setDate(d.getDate() + n);
  return d;
}

/** Places people actually name; the last one opens a free-text field. */
export const STUDY_SPOTS = ['Home desk', 'Library', 'Coaching', 'Hostel room', 'Terrace', 'Cafe', 'Other'];

export const STEPS = ['appearance', 'exam', 'coaching', 'date', 'shape', 'hours', 'syllabus', 'plan'] as const;
export type StepId = (typeof STEPS)[number];

/** Segments shown in the top progress bar — the plan step has none, as on Android. */
const PROGRESS_STEPS: StepId[] = ['appearance', 'exam', 'coaching', 'date', 'shape', 'hours', 'syllabus'];



@Injectable({ providedIn: 'root' })
export class OnboardingStore {
  readonly accent = signal<AccentId>('purple');
  readonly appearance = signal<AppearanceId>('dark');

  readonly step = signal<StepId>('appearance');

  /**
   * Onboarding is done; the app shell takes over. Defaults to true while the
   * shell is being built — set to false to walk the onboarding flow.
   */
  readonly started = signal(true);
  readonly dateMode = signal<DateMode>('exam');
  readonly targetDate = signal<Date>(addDays(startOfToday(), 118));
  readonly examId = signal('cgl');
  readonly coachingId = signal('allen');
  readonly shapeId = signal('col');
  readonly weekdayHours = signal(4);
  readonly weekendHours = signal(7);
  readonly studyPlace = signal('');

  /** Units the user says they have already covered. */
  readonly doneUnits = signal<ReadonlySet<string>>(new Set());

  toggleUnit(id: string): void {
    const next = new Set(this.doneUnits());
    next.has(id) ? next.delete(id) : next.add(id);
    this.doneUnits.set(next);
  }

  /** Days from today to the chosen target, floored at 1. */
  readonly days = computed(() => {
    const ms = this.targetDate().getTime() - startOfToday().getTime();
    return Math.max(1, Math.round(ms / 86_400_000));
  });

  readonly stepIndex = computed(() => STEPS.indexOf(this.step()));
  readonly canGoBack = computed(() => this.stepIndex() > 0);

  readonly progressIndex = computed(() => {
    const i = PROGRESS_STEPS.indexOf(this.step());
    return i === -1 ? null : i;
  });
  readonly progressSegments = PROGRESS_STEPS.length;

  /** Hours the plan asks for today. */
  readonly todayHours = computed(() => {
    const day = new Date().getDay();
    return day === 0 || day === 6 ? this.weekendHours() : this.weekdayHours();
  });

  readonly weeklyHours = computed(() => this.weekdayHours() * 5 + this.weekendHours() * 2);

  readonly availableHours = computed(() =>
    Math.round((this.days() / 7) * this.weeklyHours()),
  );

  /** Hours the remaining syllabus needs, shrinking as chapters are ticked. */
  readonly requiredHours = computed(() =>
    Math.round(
      ALL_CHAPTERS.filter((c) => !chapterIsDone(c, this.doneUnits())).reduce((n, c) => n + c.hours, 0),
    ),
  );

  readonly gapHours = computed(() => this.requiredHours() - this.availableHours());

  readonly dailyAverage = computed(() => this.weeklyHours() / 7);

  /** Days the syllabus itself consumes at the chosen pace, capped by the runway. */
  readonly learnDays = computed(() =>
    Math.min(this.days(), Math.ceil(this.requiredHours() / this.dailyAverage())),
  );

  /** What the syllabus does not consume is split: most to revision, the rest slack. */
  private readonly leftoverDays = computed(() => this.days() - this.learnDays());

  readonly bufferDays = computed(() => Math.round(this.leftoverDays() * 0.4));

  readonly reviseDays = computed(() => this.leftoverDays() - this.bufferDays());

  readonly coverage = computed(() =>
    Math.min(100, Math.round((this.availableHours() / this.requiredHours()) * 100)),
  );

  readonly examDate = computed(() => this.targetDate());

  next(): void {
    const i = this.stepIndex();
    if (i < STEPS.length - 1) this.step.set(STEPS[i + 1]);
  }

  back(): void {
    const i = this.stepIndex();
    if (i > 0) this.step.set(STEPS[i - 1]);
  }

  applyShape(shape: DayShape): void {
    this.shapeId.set(shape.id);
    this.weekdayHours.set(shape.weekday);
    this.weekendHours.set(shape.weekend);
  }
}
