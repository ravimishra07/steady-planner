/**
 * Fixed hours: the parts of the day that are already spoken for. Study is
 * scheduled into what these leave behind, never over them.
 */
export type CommitmentKind = 'school' | 'coaching' | 'lecture' | 'tuition' | 'work' | 'meal' | 'other';

export interface Commitment {
  id: string;
  label: string;
  kind: CommitmentKind;
  /** Minutes from midnight. */
  startMinute: number;
  minutes: number;
  /** Weekdays it runs on, 0 = Sunday. */
  days: number[];
}

export interface CommitmentPreset {
  kind: CommitmentKind;
  label: string;
  icon: string;
  startMinute: number;
  minutes: number;
  days: number[];
}

const MON_SAT = [1, 2, 3, 4, 5, 6];
const EVERY_DAY = [0, 1, 2, 3, 4, 5, 6];

/** Shapes an aspirant recognises; every field stays editable after picking. */
export const COMMITMENT_PRESETS: CommitmentPreset[] = [
  { kind: 'school', label: 'School / college', icon: 'school', startMinute: 8 * 60, minutes: 6 * 60, days: MON_SAT },
  { kind: 'coaching', label: 'Coaching class', icon: 'groups', startMinute: 16 * 60, minutes: 3 * 60, days: MON_SAT },
  { kind: 'lecture', label: 'Online lectures', icon: 'play_lesson', startMinute: 19 * 60, minutes: 2 * 60, days: MON_SAT },
  { kind: 'tuition', label: 'Tuition', icon: 'person', startMinute: 18 * 60, minutes: 90, days: [2, 4, 6] },
  { kind: 'work', label: 'Job', icon: 'work', startMinute: 10 * 60, minutes: 8 * 60, days: [1, 2, 3, 4, 5] },
  { kind: 'meal', label: 'Meal break', icon: 'restaurant', startMinute: 13 * 60, minutes: 60, days: EVERY_DAY },
];

export const DAY_INITIALS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

export function commitmentsOn(list: readonly Commitment[], weekday: number): Commitment[] {
  return list.filter((c) => c.days.includes(weekday)).sort((a, b) => a.startMinute - b.startMinute);
}

/** Committed minutes on a given weekday, overlaps counted once. */
export function committedMinutes(list: readonly Commitment[], weekday: number): number {
  let total = 0;
  let cursor = -1;
  for (const c of commitmentsOn(list, weekday)) {
    const start = Math.max(c.startMinute, cursor);
    const end = c.startMinute + c.minutes;
    if (end > start) total += end - start;
    cursor = Math.max(cursor, end);
  }
  return total;
}

export function clockLabel(minuteOfDay: number): string {
  const h = Math.floor(minuteOfDay / 60) % 24;
  const m = minuteOfDay % 60;
  const suffix = h < 12 ? 'am' : 'pm';
  const hour = h % 12 === 0 ? 12 : h % 12;
  return m === 0 ? `${hour}${suffix}` : `${hour}:${String(m).padStart(2, '0')}${suffix}`;
}

export function timeValue(minuteOfDay: number): string {
  const h = Math.floor(minuteOfDay / 60) % 24;
  return `${String(h).padStart(2, '0')}:${String(minuteOfDay % 60).padStart(2, '0')}`;
}

export function minutesFromTimeValue(value: string): number {
  const [h, m] = value.split(':').map(Number);
  return (h || 0) * 60 + (m || 0);
}
