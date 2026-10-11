// java.time stand-ins. Not a port of a Kotlin file.
//
//  LocalDate  -> ISO string 'YYYY-MM-DD'. String comparison (<, >, ===) == LocalDate comparison.
//  LocalTime  -> minute-of-day int.
//  Instant    -> epoch ms number.
//  ZoneId     -> the browser's local zone (ZoneId.systemDefault()); a session's stored
//                timeZoneId is kept but resolution always uses the local zone.
//  DayOfWeek  -> enum MONDAY..SUNDAY, value 1..7 (ISO, Monday first), like java.time.
import { enumClass } from './kt.js';

export const DayOfWeek = enumClass(
  ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
  (_, i) => ({ value: i + 1 }),
);
/** DayOfWeek.of(1..7). */
export const dayOfWeekOf = (v) => DayOfWeek.entries[v - 1];

const pad = (n, w = 2) => String(n).padStart(w, '0');
const MS_DAY = 86_400_000;

/** LocalDate.of(y, m, d). */
export function dateOf(y, m, d) {
  return `${y < 0 ? '-' : ''}${pad(Math.abs(y), 4)}-${pad(m)}-${pad(d)}`;
}
const fields = (date) => {
  const m = /^(-?\d{4,})-(\d{2})-(\d{2})$/.exec(date);
  if (!m) throw new Error(`DateTimeParseException: Text '${date}' could not be parsed`);
  return [Number(m[1]), Number(m[2]), Number(m[3])];
};
/** LocalDate.parse — throws (like DateTimeParseException) for anything not a real date. */
export function parseDate(text) {
  const [y, mo, d] = fields(String(text ?? ''));
  if (mo < 1 || mo > 12 || d < 1 || d > lengthOfMonth(dateOf(y, mo, 1))) {
    throw new Error(`DateTimeParseException: Text '${text}' could not be parsed`);
  }
  return dateOf(y, mo, d);
}
/** LocalDate.parse or null. */
export const parseDateOrNull = (t) => { try { return parseDate(t); } catch { return null; } };

export const year = (d) => fields(d)[0];
export const monthValue = (d) => fields(d)[1];
export const dayOfMonth = (d) => fields(d)[2];

export function toEpochDay(date) {
  const [y, m, d] = fields(date);
  const dt = new Date(0);
  dt.setUTCFullYear(y, m - 1, d);
  return Math.floor(dt.getTime() / MS_DAY);
}
export function ofEpochDay(n) {
  const dt = new Date(n * MS_DAY);
  return dateOf(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}
export const plusDays = (d, n) => (n === 0 ? d : ofEpochDay(toEpochDay(d) + n));
export const minusDays = (d, n) => plusDays(d, -n);
export const plusWeeks = (d, n) => plusDays(d, 7 * n);
export const minusWeeks = (d, n) => plusDays(d, -7 * n);
export function lengthOfMonth(d) {
  const [y, m] = fields(d);
  return [31, isLeap(y) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][m - 1];
}
const isLeap = (y) => (y % 4 === 0 && y % 100 !== 0) || y % 400 === 0;
/** LocalDate.plusMonths: day clamped to the target month's length (java.time semantics). */
export function plusMonths(d, n) {
  const [y, m, day] = fields(d);
  const total = y * 12 + (m - 1) + n;
  const ny = Math.floor(total / 12), nm = (total % 12 + 12) % 12 + 1;
  const len = lengthOfMonth(dateOf(ny, nm, 1));
  return dateOf(ny, nm, Math.min(day, len));
}
export const minusMonths = (d, n) => plusMonths(d, -n);
export const withDayOfMonth = (d, dom) => { const [y, m] = fields(d); return parseDate(dateOf(y, m, dom)); };
/** date.dayOfWeek. 1970-01-01 was a Thursday. */
export const dayOfWeek = (d) => DayOfWeek.entries[(((toEpochDay(d) + 3) % 7) + 7) % 7];
/** ChronoUnit.DAYS.between(start, end). */
export const daysBetween = (start, end) => toEpochDay(end) - toEpochDay(start);
export const maxDate = (a, b) => (a >= b ? a : b);
export const minDate = (a, b) => (a <= b ? a : b);
export const coerceDateIn = (d, lo, hi) => {
  if (lo > hi) throw new Error(`Cannot coerce value to an empty range: maximum ${hi} is less than minimum ${lo}.`);
  return d < lo ? lo : d > hi ? hi : d;
};

// ---- clock (overridable for testing) ----
let clockOffsetMs = 0;
/** Test hook: shifts the app clock. */
export const setClockOffsetMs = (ms) => { clockOffsetMs = ms; };
/** System.currentTimeMillis(). */
export const nowMs = () => Date.now() + clockOffsetMs;
/** Instant.ofEpochMilli(ms).atZone(systemDefault).toLocalDate(). */
export function epochMsToLocalDate(ms) {
  const dt = new Date(ms);
  return dateOf(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}
/** LocalDate.now(). */
export const today = () => epochMsToLocalDate(nowMs());
/** date.atStartOfDay(systemDefault).toInstant().toEpochMilli(). */
export function atStartOfDayMs(date) {
  const [y, m, d] = fields(date);
  const dt = new Date(0);
  dt.setFullYear(y, m - 1, d);
  dt.setHours(0, 0, 0, 0);
  return dt.getTime();
}
/** Local minute-of-day of an instant (LocalTime hour*60+minute). */
export function minuteOfDayAt(ms) {
  const dt = new Date(ms);
  return dt.getHours() * 60 + dt.getMinutes();
}
/** LocalTime.now() as minute-of-day. */
export const nowMinuteOfDay = () => minuteOfDayAt(nowMs());
/** ZoneId.systemDefault().id */
export const systemZoneId = () => {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch { return 'UTC'; }
};
/** DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) for the default locale, e.g. "2 May 2027". */
export function formatMediumDate(date) {
  const [y, m, d] = fields(date);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}
