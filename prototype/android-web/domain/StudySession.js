// Port of domain/StudySession.kt (legacy StudySessionRecord + Today helpers)
import { dataClass, idiv, padStart2, toIntOrNull, sortedBy } from './kt.js';
import { daysBetween, parseDateOrNull, nowMinuteOfDay } from './time.js';
import { TodayBlock, BlockTag, WeekDayStatus, buildTimeline } from './TodaySchedule.js';
import { leafKeys } from './Syllabus.js';

/** date: 'YYYY-MM-DD' */
export const StudySessionRecord = dataClass({
  id: undefined, date: undefined, startMinuteOfDay: undefined, durationMinutes: undefined,
  nodeKey: undefined, title: undefined, sectionName: undefined, subjectId: undefined,
  isRevision: false,
  /** Revisions already done on this topic; shown as "Revised ×N". */
  revisionCount: 0,
  completed: false,
  /** Planned intent stays separate from actual focused effort. */
  planned: true,
  /** Wall-clock end for an in-progress sprint. */
  runningEndsAtMs: null,
});

export function formatMinuteOfDay(minuteOfDay) {
  return `${padStart2(idiv(minuteOfDay, 60))}:${padStart2(minuteOfDay % 60)}`;
}

export const currentMinuteOfDay = () => nowMinuteOfDay();

/** StudySessionRecord.toTodayBlock() */
export function toTodayBlock(r) {
  return TodayBlock({
    id: r.id,
    time: formatMinuteOfDay(r.startMinuteOfDay),
    title: r.title,
    subtitle: r.isRevision ? `Revision · ${r.sectionName}` : r.sectionName,
    tag: r.isRevision ? BlockTag.REVISE : BlockTag.READ,
    subjectId: r.subjectId,
    minutes: r.durationMinutes,
    completed: r.completed,
    scheduled: !r.completed && r.runningEndsAtMs == null,
  });
}

export function sessionsToTimeline(sessions) {
  return buildTimeline(sortedBy(sessions, (s) => s.startMinuteOfDay).map(toTodayBlock));
}

export function nodeKey(sectionIndex, pathIndices, tierPrefix = 't1') {
  return `${tierPrefix}_${sectionIndex}_${pathIndices.join('_')}`;
}

export function findTopicNode(sections, key) {
  if (!key.startsWith('t1_')) return null;
  const indices = key.slice(3).split('_').map(toIntOrNull).filter((x) => x != null);
  if (indices.length < 2) return null;
  const section = sections[indices[0]];
  if (!section) return null;
  let node = section.topics[indices[1]];
  if (!node) return null;
  for (let i = 2; i < indices.length; i++) {
    node = node.children[indices[i]];
    if (!node) return null;
  }
  return node;
}

export function leafKeysForNodeKey(sections, key) {
  const node = findTopicNode(sections, key);
  return node ? leafKeys(node, key) : [];
}

/** LocalDate.toStoreString(): ISO_LOCAL_DATE — dates are already ISO strings. */
export const toStoreString = (date) => date;

export function weekStatusForDay(date, today, completedMinutes, budgetHours) {
  const offset = daysBetween(today, date);
  if (offset === 0) return WeekDayStatus.TODAY;
  if (offset > 0) return offset === 1 ? WeekDayStatus.PLANNED : WeekDayStatus.REST;
  const budgetMins = budgetHours * 60;
  if (completedMinutes <= 0) return WeekDayStatus.REST;
  if (budgetMins > 0 && completedMinutes >= budgetMins) return WeekDayStatus.DONE;
  return WeekDayStatus.PARTIAL;
}

export function weekStatusForDayMinutes(date, today, completedMinutes, budgetMinutes) {
  const offset = daysBetween(today, date);
  if (offset === 0) return WeekDayStatus.TODAY;
  if (offset > 0) return offset === 1 ? WeekDayStatus.PLANNED : WeekDayStatus.REST;
  if (completedMinutes <= 0) return WeekDayStatus.REST;
  return budgetMinutes > 0 && completedMinutes >= budgetMinutes ? WeekDayStatus.DONE : WeekDayStatus.PARTIAL;
}

export const parseStoreDate = (value) => parseDateOrNull(value);

/** A topic tapped from Syllabus's play button, handed off to Home. */
export const PendingSyllabusPick = dataClass({ nodeKey: undefined, title: undefined, sectionName: undefined, subjectId: undefined, topicPath: undefined });
