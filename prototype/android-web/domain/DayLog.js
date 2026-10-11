// Port of domain/DayLog.kt. Zones: always the browser's local zone (see time.js).
import { dataClass, sortedBy } from './kt.js';
import { minuteOfDayAt } from './time.js';
import { findNode, chapterOf, findSubjectOf } from './ExamPack.js';
import { countsAsStudy, focusedMinutes, studiedMinutes } from './Metrics.js';

/** One calendar day's sessions. */
export const DayLog = dataClass({ date: undefined, entries: undefined, totalFocusedMinutes: undefined });

export const DaySessionEntry = dataClass({
  sessionId: undefined, title: undefined, topicTitle: undefined, subjectName: undefined,
  activityType: undefined, focusedMinutes: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined,
});

/** customTitles: Map<planBlockId, title> */
export function dayLog(date, sessions, pack, customTitles = new Map()) {
  const day = sortedBy(sessions.filter((s) => s.studyDate === date && countsAsStudy(s)), (s) => s.startedAtEpochMs);
  const entries = day.map((session) => {
    const node = session.nodeId != null && pack ? findNode(pack, session.nodeId) : null;
    const chapter = session.nodeId != null && pack ? chapterOf(pack, session.nodeId) : null;
    const subjectFromNode = session.nodeId != null && pack ? findSubjectOf(pack, session.nodeId)?.name : null;
    const subjectFromId = session.subjectId != null && pack ? pack.subjects.find((s) => s.id === session.subjectId)?.name : null;
    return DaySessionEntry({
      sessionId: session.id,
      title: chapter?.title ?? node?.title ?? (session.planBlockId != null ? customTitles.get(session.planBlockId) ?? null : null),
      topicTitle: node != null && chapter != null && node.id !== chapter.id ? node.title : null,
      subjectName: subjectFromNode ?? subjectFromId ?? null,
      activityType: session.activityType,
      focusedMinutes: focusedMinutes(session),
      startMinuteOfDay: minuteOfDayAt(session.startedAtEpochMs),
      endMinuteOfDay: session.endedAtEpochMs != null ? minuteOfDayAt(session.endedAtEpochMs) : null,
    });
  });
  return DayLog({ date, entries, totalFocusedMinutes: studiedMinutes(day, date, date) });
}
