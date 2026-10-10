// Port of core/data/db/Converters.kt.
// Room entities use primitive types only (epoch day / epoch millis / enum name strings).
// LocalDate is a 'YYYY-MM-DD' string here (domain/time.js); these are the only boundary crossers.
import { ofEpochDay, toEpochDay as dateToEpochDay } from '../../domain/time.js';

export class Converters {
  fromEpochDay(epochDay) { return epochDay == null ? null : ofEpochDay(epochDay); }
  toEpochDay(date) { return date == null ? null : dateToEpochDay(date); }
}

/** Shared helpers used by the entity files (LocalDate.ofEpochDay / LocalDate.toEpochDay). */
export const epochDayToDate = (n) => (n == null ? null : ofEpochDay(n));
export const dateToEpochDayOrNull = (d) => (d == null ? null : dateToEpochDay(d));
