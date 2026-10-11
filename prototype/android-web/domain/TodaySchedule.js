// Port of domain/TodaySchedule.kt
import { dataClass, enumClass, idiv, coerceAtLeast, toInt, sumOf } from './kt.js';
import { DayOfWeek, dayOfWeek, minusDays, plusDays, today as todayDate } from './time.js';

export const BlockTag = enumClass(['READ', 'PRACTICE', 'REVISE']);

export const TodayBlock = dataClass({
  id: undefined, time: undefined, title: '', subtitle: '', tag: null, subjectId: null,
  minutes: 0, isBreak: false, completed: false, scheduled: false,
});

export const WeekDayStatus = enumClass(['DONE', 'PARTIAL', 'TODAY', 'PLANNED', 'REST']);

/** sealed TimelineItem: {type:'Gap', minutes} | {type:'Entry', block} */
export const TimelineItem = Object.freeze({
  Gap: (minutes) => Object.freeze({ type: 'Gap', minutes }),
  Entry: (block) => Object.freeze({ type: 'Entry', block }),
});

/** Fixed NEET demo schedule used by domain tests. */
export function demoTodayBlocks() {
  return [
    TodayBlock({ id: '0', time: '06:30', title: 'Physics — Motion in a Straight Line', subtitle: 'NCERT chapter · 90 min', tag: BlockTag.READ, subjectId: 'physics', minutes: 90 }),
    TodayBlock({ id: 'break1', time: '08:00', isBreak: true, minutes: 15 }),
    TodayBlock({ id: '2', time: '08:15', title: 'Physics — 40 practice questions', subtitle: 'NEET question set · 75 min', tag: BlockTag.PRACTICE, subjectId: 'physics', minutes: 75 }),
    TodayBlock({ id: '3', time: '14:00', title: 'Revision: Some Basic Concepts of Chemistry', subtitle: 'Done 6 days ago · 30 min', tag: BlockTag.REVISE, subjectId: 'chemistry', minutes: 30 }),
    TodayBlock({ id: '4', time: '15:30', title: 'Cell: The Unit of Life', subtitle: 'NCERT review · 30 min', tag: BlockTag.READ, subjectId: 'botany', minutes: 30 }),
    TodayBlock({ id: '5', time: '18:30', title: 'Animal Kingdom — Practice', subtitle: 'NEET question set · 45 min', tag: BlockTag.PRACTICE, subjectId: 'zoology', minutes: 45 }),
  ];
}

const isWeekend = (date) => { const d = dayOfWeek(date); return d === DayOfWeek.SATURDAY || d === DayOfWeek.SUNDAY; };

export function todayBudget(weekdayHours, weekendHours, date = todayDate()) {
  return isWeekend(date) ? toInt(weekendHours) : toInt(weekdayHours);
}

/** Exact capacity for scheduling and progress; preserves half-hour targets such as 4.5h. */
export function todayBudgetMinutes(weekdayHours, weekendHours, date = todayDate()) {
  const h = isWeekend(date) ? weekendHours : weekdayHours;
  return coerceAtLeast(Math.round(Math.fround(Math.fround(h) * 60)), 0);
}

/** Monday-first week containing [anchor]. */
export function weekAround(anchor) {
  const start = minusDays(anchor, dayOfWeek(anchor).value - DayOfWeek.MONDAY.value);
  return Array.from({ length: 7 }, (_, i) => plusDays(start, i));
}

export function dayStatus(offsetFromToday) {
  if (offsetFromToday < 0) return offsetFromToday === -1 ? WeekDayStatus.PARTIAL : WeekDayStatus.DONE;
  if (offsetFromToday === 0) return WeekDayStatus.TODAY;
  if (offsetFromToday === 1) return WeekDayStatus.PLANNED;
  return WeekDayStatus.REST;
}

export function parseTimeMinutes(time) {
  const parts = time.split(':');
  const toI = (s) => { if (!/^[+-]?\d+$/.test(s ?? '')) throw new Error(`NumberFormatException: For input string: "${s}"`); return Number(s); };
  return toI(parts[0]) * 60 + toI(parts[1]);
}

export function formatGap(minutes) {
  if (minutes < 60) return `${minutes} min`;
  const hours = idiv(minutes, 60);
  const remainder = minutes % 60;
  return remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`;
}

export const blockIsDone = (blocksDone, id) => blocksDone.has(id);

export const doneMinutes = (blocks, blocksDone) =>
  sumOf(blocks.filter((b) => !b.isBreak && blockIsDone(blocksDone, b.id)), (b) => b.minutes);

export const filterBlocks = (blocks, activeSubjects) => blocks.filter((b) => b.isBreak || activeSubjects.has(b.subjectId));

export function buildTimeline(blocks) {
  const items = [];
  let previousEnd = null;
  for (const block of blocks) {
    if (!block.isBreak && previousEnd != null) {
      const gap = parseTimeMinutes(block.time) - previousEnd;
      if (gap >= 45) items.push(TimelineItem.Gap(gap));
    }
    items.push(TimelineItem.Entry(block));
    if (!block.isBreak) previousEnd = parseTimeMinutes(block.time) + block.minutes;
  }
  return items;
}
