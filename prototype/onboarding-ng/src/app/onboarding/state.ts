import { Injectable, computed, signal } from '@angular/core';
import { ALL_CHAPTERS, chapterIsDone } from './exam-pack';
import { persisted, persistedSet } from '../core/persist';
import { COMMITMENT_PRESETS, Commitment, committedMinutes } from './commitments';

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

export const STEPS = [
  'appearance', 'exam', 'coaching', 'commitments', 'date', 'shape', 'hours', 'syllabus', 'plan',
] as const;
export type StepId = (typeof STEPS)[number];

/** Segments shown in the top progress bar — the plan step has none, as on Android. */
const PROGRESS_STEPS: StepId[] = [
  'appearance', 'exam', 'coaching', 'commitments', 'date', 'shape', 'hours', 'syllabus',
];

/** The waking window study can be scheduled inside. */
export const DEFAULT_WAKE = 6 * 60;
export const DEFAULT_SLEEP = 23 * 60;



@Injectable({ providedIn: 'root' })
export class OnboardingStore {
  readonly accent = signal<AccentId>('purple');
  readonly appearance = signal<AppearanceId>('dark');

  readonly step = signal<StepId>('appearance');

  /**
   * Onboarding is done; the app shell takes over. The shell is what is being
   * worked on, so it boots straight there — add ?onboarding to the URL to walk
   * the flow instead.
   */
  readonly started = signal(!location.search.includes('onboarding'));
  readonly dateMode = signal<DateMode>('exam');
  readonly targetDate = signal<Date>(addDays(startOfToday(), 118));
  readonly examId = signal('cgl');
  readonly coachingId = signal('allen');
  readonly shapeId = signal('col');
  readonly weekdayHours = signal(4);
  readonly weekendHours = signal(7);
  readonly studyPlace = signal('');

  /** Units the user says they have already covered. */
  readonly doneUnits = persistedSet('done-units');

  /**
   * Chapters deliberately set aside. Triage, not failure: when the runway does
   * not fit the syllabus, the honest move is to choose what gets dropped
   * rather than to fall behind on everything at once.
   */
  readonly parkedChapters = persistedSet('parked');

  isParked(chapterId: string): boolean {
    return this.parkedChapters().has(chapterId);
  }

  togglePark(chapterId: string): void {
    const next = new Set(this.parkedChapters());
    next.has(chapterId) ? next.delete(chapterId) : next.add(chapterId);
    this.parkedChapters.set(next);
  }

  park(chapterIds: readonly string[]): void {
    this.parkedChapters.set(new Set([...this.parkedChapters(), ...chapterIds]));
  }

  unparkAll(): void {
    this.parkedChapters.set(new Set());
  }

  toggleUnit(id: string): void {
    const next = new Set(this.doneUnits());
    next.has(id) ? next.delete(id) : next.add(id);
    this.doneUnits.set(next);
  }

  /** Idempotent tick, used when a logged session covers a subtopic. */
  toggleUnitOn(id: string): void {
    if (this.doneUnits().has(id)) return;
    this.doneUnits.set(new Set(this.doneUnits()).add(id));
  }

  /* ---- Fixed hours -------------------------------------------------- */

  /**
   * What the day already owes to something else. Asked for in onboarding,
   * because a plan that schedules over school is not a plan.
   */
  readonly commitments = persisted<Commitment[]>('commitments', defaultCommitments());
  readonly wakeMinute = persisted<number>('wake', DEFAULT_WAKE);
  /** Gap between two sittings in the same stretch of free time. */
  readonly breakMinutes = persisted<number>('break', 15);
  /** Whether a running focus session blocks distracting apps. */
  readonly blockApps = persisted<boolean>('block-apps', true);
  readonly sleepMinute = persisted<number>('sleep', DEFAULT_SLEEP);

  addCommitment(preset: (typeof COMMITMENT_PRESETS)[number]): void {
    this.commitments.set([
      ...this.commitments(),
      { ...preset, id: crypto.randomUUID(), days: [...preset.days] },
    ]);
  }

  updateCommitment(id: string, change: Partial<Commitment>): void {
    this.commitments.set(this.commitments().map((c) => (c.id === id ? { ...c, ...change } : c)));
  }

  removeCommitment(id: string): void {
    this.commitments.set(this.commitments().filter((c) => c.id !== id));
  }

  toggleCommitmentDay(id: string, day: number): void {
    const c = this.commitments().find((x) => x.id === id);
    if (!c) return;
    const days = c.days.includes(day) ? c.days.filter((d) => d !== day) : [...c.days, day].sort();
    this.updateCommitment(id, { days });
  }

  /** Minutes left on a weekday once fixed hours are removed. */
  freeMinutesOn(weekday: number): number {
    const window = this.sleepMinute() - this.wakeMinute();
    return Math.max(0, window - committedMinutes(this.commitments(), weekday));
  }

  readonly weekdayFreeHours = computed(() => this.freeMinutesOn(3) / 60);
  readonly weekendFreeHours = computed(() => this.freeMinutesOn(0) / 60);

  /** True when the asked-for hours do not fit the day that is left. */
  readonly weekdayOverbooked = computed(() => this.weekdayHours() > this.weekdayFreeHours());
  readonly weekendOverbooked = computed(() => this.weekendHours() > this.weekendFreeHours());

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

  /** The same runway, capped by the hours the week physically has spare. */
  readonly realisticWeeklyHours = computed(() => {
    let total = 0;
    for (let day = 0; day < 7; day++) {
      const asked = day === 0 || day === 6 ? this.weekendHours() : this.weekdayHours();
      total += Math.min(asked, this.freeMinutesOn(day) / 60);
    }
    return Math.round(total);
  });

  /** Hours the remaining syllabus needs, shrinking as chapters are ticked. */
  readonly requiredHours = computed(() =>
    Math.round(
      ALL_CHAPTERS.filter((c) => !chapterIsDone(c, this.doneUnits()) && !this.isParked(c.id))
        .reduce((n, c) => n + c.hours, 0),
    ),
  );

  /** Raise or lower both sliders while keeping their weekday/weekend shape. */
  scaleHours(factor: number): void {
    const round = (h: number) => Math.max(1, Math.round(h * factor * 2) / 2);
    this.weekdayHours.set(Math.min(14, round(this.weekdayHours())));
    this.weekendHours.set(Math.min(16, round(this.weekendHours())));
  }

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

/**
 * What a day starts out owing: coaching, and the two meals that split a long
 * day into a morning, an afternoon and an evening. All three are editable.
 */
function defaultCommitments(): Commitment[] {
  const coaching = COMMITMENT_PRESETS.find((p) => p.kind === 'coaching')!;
  const every = [0, 1, 2, 3, 4, 5, 6];
  return [
    { ...coaching, id: 'seed-coaching', days: [...coaching.days] },
    { id: 'seed-lunch', label: 'Lunch', kind: 'meal', startMinute: 13 * 60, minutes: 60, days: every },
    { id: 'seed-dinner', label: 'Dinner', kind: 'meal', startMinute: 20 * 60 + 30, minutes: 60, days: every },
  ];
}
