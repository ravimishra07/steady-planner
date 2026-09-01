import { addDays, startOfToday } from '../domain/time/date';
import { Recall } from '../domain/study/models';
export type { Recall } from '../domain/study/models';

/**
 * Retention: the part of studying that the tick box hides. A chapter that was
 * learnt and never seen again is not "done", and the app should be able to say
 * so. Intervals are the standard expanding schedule, bent by how the user said
 * the sitting actually went.
 */

/** What the user reports after a sitting. One tap, no numbers. */
export const RECALLS: { id: Recall; label: string; icon: string }[] = [
  { id: 'shaky', label: 'Shaky', icon: 'sentiment_dissatisfied' },
  { id: 'okay', label: 'Okay', icon: 'sentiment_neutral' },
  { id: 'solid', label: 'Solid', icon: 'sentiment_satisfied' },
];

/**
 * Days until the next pass, by how many passes are already done. Expanding
 * rehearsal: see it again soon, then less and less often.
 */
/**
 * How fast revision comes back round. The student picks one — a schedule they
 * cannot see or change is a schedule they cannot trust.
 */
export type Pace = 'relaxed' | 'standard' | 'intense';

export interface PaceOption { id: Pace; label: string; hint: string; days: number[]; }

export const PACES: PaceOption[] = [
  { id: 'relaxed', label: 'Relaxed', hint: 'Comes back after 5, 15, 45 then 90 days', days: [5, 15, 45, 90] },
  { id: 'standard', label: 'Standard', hint: 'Comes back after 3, 10, 30 then 60 days', days: [3, 10, 30, 60] },
  { id: 'intense', label: 'Intense', hint: 'Comes back after 2, 6, 16 then 35 days', days: [2, 6, 16, 35] },
];

let baseInterval = [3, 10, 30, 60];

/** Set once from the store, so every caller reads the same schedule. */
export function setPace(pace: Pace): void {
  baseInterval = PACES.find((p) => p.id === pace)?.days ?? PACES[1].days;
}

/** Shaky pulls the next pass in; solid pushes it out. */
const RECALL_FACTOR: Record<Recall, number> = { shaky: 0.5, okay: 1, solid: 1.6 };

/**
 * Interval in days for the pass after `revisions` completed ones.
 *
 * `daysLeft` caps it against the runway. A sixty-day gap is meaningless to
 * someone thirty days from the exam — the chapter would simply never come back
 * — so no interval may exceed a third of what is left, which gives a chapter
 * about three more looks whatever the runway.
 */
export function nextInterval(
  revisions: number,
  recall: Recall | null,
  daysLeft?: number,
): number {
  const base = baseInterval[Math.min(revisions, baseInterval.length - 1)];
  const wanted = Math.round(base * RECALL_FACTOR[recall ?? 'okay']);
  const cap = daysLeft === undefined ? wanted : Math.max(1, Math.floor(daysLeft / 3));
  return Math.max(1, Math.min(wanted, cap));
}

export function dueDate(
  from: Date,
  revisions: number,
  recall: Recall | null,
  daysLeft?: number,
): Date {
  return addDays(from, nextInterval(revisions, recall, daysLeft));
}

/**
 * How well a chapter is probably still held, 0 to 1. Full strength on the day
 * it was revised, decaying to zero one whole interval past its due date — so a
 * chapter three weeks overdue reads as nearly gone, which it is.
 */
export function strength(
  lastTouched: string | null,
  dueKey: string | null,
  today: Date = startOfToday(),
): number {
  if (!lastTouched || !dueKey) return 0;
  const due = keyToDate(dueKey);
  const overdue = Math.round((today.getTime() - due.getTime()) / 86_400_000);
  if (overdue <= 0) return 1;
  const last = keyToDate(lastTouched);
  const interval = Math.max(1, Math.round((due.getTime() - last.getTime()) / 86_400_000));
  return Math.max(0, 1 - overdue / interval);
}

/** Days a chapter is past due; negative means it is not due yet. */
export function overdueDays(dueKey: string | null, today: Date = startOfToday()): number {
  if (!dueKey) return 0;
  return Math.round((today.getTime() - keyToDate(dueKey).getTime()) / 86_400_000);
}

export type RetentionState = 'fresh' | 'due' | 'slipping' | 'lost' | 'new';

/** The four words the whole system reduces to. */
export function retentionState(
  lastTouched: string | null,
  dueKey: string | null,
  today: Date = startOfToday(),
): RetentionState {
  if (!lastTouched || !dueKey) return 'new';
  const over = overdueDays(dueKey, today);
  if (over < 0) return 'fresh';
  if (over === 0) return 'due';
  return strength(lastTouched, dueKey, today) > 0 ? 'slipping' : 'lost';
}

function keyToDate(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}
