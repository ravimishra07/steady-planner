// Port of domain/Availability.kt
import { dataClass, toInt, coerceAtLeast, coerceAtMost } from './kt.js';
import { DayOfWeek, dayOfWeek } from './time.js';
import { DAY_TIMELINE_START, DAY_TIMELINE_END } from './DayTimeline.js';

/** A recurring weekly free-time window — a hard scheduling constraint. dayOfWeek: DayOfWeek. */
export const WeeklyAvailability = dataClass({ id: undefined, attemptId: undefined, dayOfWeek: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined });

/** A one-off override of the weekly pattern for a specific date. date: 'YYYY-MM-DD'. */
export const AvailabilityOverride = dataClass({ id: undefined, attemptId: undefined, date: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined });

/** Windows in effect for [date] as [start, end] pairs: the override if one exists, else the weekly pattern. */
export function effectiveWindowsFor(date, weekly, overrides) {
  const dayOverrides = overrides.filter((o) => o.date === date);
  if (dayOverrides.length > 0) return dayOverrides.map((o) => [o.startMinuteOfDay, o.endMinuteOfDay]);
  const dow = dayOfWeek(date);
  return weekly.filter((w) => w.dayOfWeek === dow).map((w) => [w.startMinuteOfDay, w.endMinuteOfDay]);
}

/** Legacy flat weekday/weekend hours -> one daily window per day of week anchored at DAY_TIMELINE_START. */
export function defaultWeeklyAvailability(attemptId, weekdayHours, weekendHours) {
  return DayOfWeek.entries.map((day) => {
    const hours = day === DayOfWeek.SATURDAY || day === DayOfWeek.SUNDAY ? weekendHours : weekdayHours;
    const minutes = coerceAtLeast(toInt(Math.fround(Math.fround(hours) * 60)), 0);
    return WeeklyAvailability({
      id: `default_${attemptId}_${day.name}`,
      attemptId,
      dayOfWeek: day,
      startMinuteOfDay: DAY_TIMELINE_START,
      endMinuteOfDay: coerceAtMost(DAY_TIMELINE_START + minutes, DAY_TIMELINE_END),
    });
  });
}
