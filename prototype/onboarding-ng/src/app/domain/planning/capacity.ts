export const REVISION_MULTIPLIER = 1.28;
export const SSC_CGL_RAW_HOURS = 634;

export interface Cushion {
  need: number;
  have: number;
  gap: number;
  coverage: number;
  extraPerDay: number;
  topicsToDrop: number;
  daysToPush: number;
  bufferDays: number;
  isShort: boolean;
}
export function needHours(rawHours: number): number {
  return Math.round(rawHours * REVISION_MULTIPLIER);
}

/** Identical to the Android domain contract: leftover days are weekdays. */
export function availableHours(days: number, weekdayHours: number, weekendHours: number): number {
  const safeDays = Math.max(0, Math.trunc(days));
  const weeks = Math.floor(safeDays / 7);
  const remainder = safeDays % 7;
  return Math.round(weeks * (5 * weekdayHours + 2 * weekendHours) + remainder * weekdayHours);
}

export function cushion(
  rawHours: number,
  days: number,
  weekdayHours: number,
  weekendHours: number,
): Cushion {
  const need = needHours(rawHours);
  const have = availableHours(days, weekdayHours, weekendHours);
  const gap = need - have;
  const averageDay = (weekdayHours + weekendHours) / 2;
  return {
    need,
    have,
    gap,
    coverage: need === 0 ? 0 : clamp(Math.round((have / need) * 100), 0, 100),
    extraPerDay: gap > 0 && days > 0 ? Math.round((gap / days) * 10) / 10 : 0,
    topicsToDrop: gap > 0 ? Math.ceil(gap / 14) : 0,
    daysToPush: gap > 0 && averageDay > 0 ? Math.ceil(gap / averageDay) : 0,
    bufferDays: gap <= 0 && weekdayHours > 0 ? Math.floor(Math.abs(gap) / weekdayHours) : 0,
    isShort: gap > 0,
  };
}

/** Split hours in half-hour units while preserving the original total. */
export function splitHours(total: number, count: number): number[] {
  if (count <= 0) return [];
  const base = Math.floor((total / count) * 2) / 2;
  const parts = Array.from({ length: count }, () => base);
  let remaining = Math.round((total - base * count) * 2) / 2;
  let index = 0;
  while (remaining > 0.001 && index < count * 8) {
    parts[index % count] += 0.5;
    remaining -= 0.5;
    index++;
  }
  return parts;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
