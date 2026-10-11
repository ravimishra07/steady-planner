// Port of domain/DayTimeline.kt. Kotlin default params -> trailing positional params (pass undefined to keep a default).
import { dataClass, enumClass, sortedBy, coerceAtMost, coerceAtLeast, sumOf, toInt, idiv } from './kt.js';
import { plusDays } from './time.js';
import { topicHours } from './Syllabus.js';
import { findTopicNode } from './StudySession.js';

export const MIN_ACTIONABLE_GAP_MINUTES = 30;
export const DAY_TIMELINE_START = 6 * 60;
export const DAY_TIMELINE_END = 23 * 60;

export const SubtopicSlot = dataClass({ title: undefined, startMinuteOfDay: undefined });

export const DayBlock = dataClass(
  {
    id: undefined, subjectId: undefined, subjectLabel: undefined, title: undefined,
    startMinuteOfDay: undefined, endMinuteOfDay: undefined, isRevision: undefined, completed: undefined,
    subtopics: undefined, revisionCount: 0,
  },
  { get durationMinutes() { return this.endMinuteOfDay - this.startMinuteOfDay; } },
);

/** A user-entered fixed obligation shown on Today and treated as unavailable time. */
export const FixedCommitmentBlock = dataClass({ id: undefined, kind: undefined, startMinuteOfDay: undefined, endMinuteOfDay: undefined, customLabel: null });

/** sealed DayTimelineEntry, tagged by `type`. */
export const DayTimelineEntry = Object.freeze({
  Study: (block) => Object.freeze({ type: 'Study', block }),
  Fixed: (block) => Object.freeze({ type: 'Fixed', block }),
  Gap: (startMinuteOfDay, endMinuteOfDay) => Object.freeze({ type: 'Gap', startMinuteOfDay, endMinuteOfDay }),
  NowMarker: (minuteOfDay) => Object.freeze({ type: 'NowMarker', minuteOfDay }),
});

const Slot = (start, end) => ({ start, end });

export const StudyPlacementIssue = enumClass(['INVALID_DURATION', 'OUTSIDE_DAY', 'OVERLAPS_STUDY', 'OVERLAPS_FIXED', 'NO_AVAILABLE_TIME']);

export const SessionPlacement = dataClass({ sessionId: undefined, date: undefined, startMinuteOfDay: undefined });

export const isActionableStudyGap = (minutes) => minutes >= MIN_ACTIONABLE_GAP_MINUTES;

/** Validates one half-open study interval before it is written to either plan store. */
export function validateStudyPlacement(
  sessions, date, startMinuteOfDay, durationMinutes,
  fixedCommitments = [], dayStartMinute = DAY_TIMELINE_START, dayEndMinute = DAY_TIMELINE_END, excludingSessionId = null,
) {
  if (durationMinutes <= 0) return StudyPlacementIssue.INVALID_DURATION;
  const endMinuteOfDay = startMinuteOfDay + durationMinutes;
  if (startMinuteOfDay < 0 || endMinuteOfDay > 24 * 60 || startMinuteOfDay < dayStartMinute || endMinuteOfDay > dayEndMinute) {
    return StudyPlacementIssue.OUTSIDE_DAY;
  }
  const overlaps = (s, e) => startMinuteOfDay < e && endMinuteOfDay > s;
  if (sessions.some((s) => s.id !== excludingSessionId && s.date === date && !s.completed &&
    overlaps(s.startMinuteOfDay, s.startMinuteOfDay + s.durationMinutes))) {
    return StudyPlacementIssue.OVERLAPS_STUDY;
  }
  if (fixedCommitments.some((f) => overlaps(f.startMinuteOfDay, f.endMinuteOfDay))) return StudyPlacementIssue.OVERLAPS_FIXED;
  return null;
}

/** Places today's missed sessions into the earliest safe windows from now onward. fixedCommitmentsByDate: Map<date, FixedCommitmentBlock[]>. */
export function planMissedDayRecovery(
  sessions, date, nowMinuteOfDay,
  fixedCommitmentsByDate = new Map(), dayStartMinute = DAY_TIMELINE_START, dayEndMinute = DAY_TIMELINE_END, horizonDays = 7,
) {
  const missed = sortedBy(sessions.filter((s) => s.date === date && !s.completed && s.runningEndsAtMs == null &&
    s.startMinuteOfDay + s.durationMinutes <= nowMinuteOfDay), (s) => s.startMinuteOfDay);
  if (missed.length === 0) return [];
  const missedIds = new Set(missed.map((s) => s.id));
  const working = sessions.filter((s) => !missedIds.has(s.id));
  const out = [];
  for (const session of missed) {
    let placement = null;
    for (let offset = 0; offset <= horizonDays; offset++) {
      const candidateDate = plusDays(date, offset);
      const notBefore = offset === 0 ? nowMinuteOfDay : dayStartMinute;
      const start = findNextFreeSlot(working, candidateDate, session.durationMinutes, notBefore,
        fixedCommitmentsByDate.get(candidateDate) ?? [], dayStartMinute, dayEndMinute);
      if (start == null) continue;
      placement = SessionPlacement({ sessionId: session.id, date: candidateDate, startMinuteOfDay: start });
      working.push(session.copy({ date: candidateDate, startMinuteOfDay: start }));
      break;
    }
    if (placement) out.push(placement);
  }
  return out;
}

function subtopicsFor(node, startMinuteOfDay, endMinuteOfDay) {
  const children = node?.children ?? [];
  if (children.length === 0) return [];
  const total = endMinuteOfDay - startMinuteOfDay;
  const weights = children.map((c) => { const h = topicHours(c); return h > 0 ? h : 1.0; });
  const weightSum = sumOf(weights);
  let cursor = startMinuteOfDay;
  return children.map((child, index) => {
    const slot = SubtopicSlot({ title: child.name, startMinuteOfDay: cursor });
    const share = weightSum > 0 ? toInt((weights[index] / weightSum) * total) : idiv(total, children.length);
    cursor = coerceAtMost(cursor + share, endMinuteOfDay);
    return slot;
  });
}

function sessionToDayBlock(session, sections) {
  const node = findTopicNode(sections, session.nodeKey);
  const start = session.startMinuteOfDay;
  const end = start + session.durationMinutes;
  return DayBlock({
    id: session.id, subjectId: session.subjectId, subjectLabel: session.sectionName, title: session.title,
    startMinuteOfDay: start, endMinuteOfDay: end, isRevision: session.isRevision, completed: session.completed,
    subtopics: session.isRevision ? [] : subtopicsFor(node, start, end),
    revisionCount: session.revisionCount,
  });
}

const entryStart = (e) => {
  switch (e.type) {
    case 'Study': case 'Fixed': return e.block.startMinuteOfDay;
    case 'Gap': return e.startMinuteOfDay;
    default: return e.minuteOfDay;
  }
};

/** The day as one continuous schedule, with a NowMarker when date is today. */
export function buildDayTimeline(
  sessions, sections, date, today, nowMinuteOfDay,
  fixedCommitments = [], dayStartMinute = DAY_TIMELINE_START, dayEndMinute = DAY_TIMELINE_END,
) {
  const isToday = date === today;
  const realBlocks = sortedBy(sessions, (s) => s.startMinuteOfDay).map((s) => sessionToDayBlock(s, sections));
  let dayStart = dayStartMinute;
  let dayEnd = dayEndMinute;
  for (const b of realBlocks) { dayStart = Math.min(dayStart, b.startMinuteOfDay); dayEnd = Math.max(dayEnd, b.endMinuteOfDay); }
  for (const b of fixedCommitments) { dayStart = Math.min(dayStart, b.startMinuteOfDay); dayEnd = Math.max(dayEnd, b.endMinuteOfDay); }
  if (isToday) dayEnd = Math.max(dayEnd, nowMinuteOfDay + 60);

  const gaps = [];
  let cursor = dayStart;
  const occupied = sortedBy([
    ...realBlocks.map((b) => Slot(b.startMinuteOfDay, b.endMinuteOfDay)),
    ...fixedCommitments.map((b) => Slot(b.startMinuteOfDay, b.endMinuteOfDay)),
  ], (s) => s.start);
  for (const slot of occupied) {
    if (slot.start > cursor) gaps.push(Slot(cursor, slot.start));
    cursor = Math.max(cursor, slot.end);
  }
  if (dayEnd > cursor) gaps.push(Slot(cursor, dayEnd));

  const allBlocks = sortedBy(realBlocks, (b) => b.startMinuteOfDay);
  const timelineGaps = sortedBy(gaps.filter((g) => g.end > g.start), (g) => g.start);
  const entries = sortedBy([
    ...allBlocks.map((b) => DayTimelineEntry.Study(b)),
    ...fixedCommitments.map((b) => DayTimelineEntry.Fixed(b)),
    ...timelineGaps.map((g) => DayTimelineEntry.Gap(g.start, g.end)),
  ], entryStart);

  if (!isToday) return entries;

  const withNow = [];
  let markerPlaced = false;
  const placeMarker = () => { withNow.push(DayTimelineEntry.NowMarker(nowMinuteOfDay)); markerPlaced = true; };
  for (const entry of entries) {
    if (entry.type === 'Study' || entry.type === 'Fixed') {
      if (!markerPlaced && nowMinuteOfDay <= entry.block.startMinuteOfDay) placeMarker();
      withNow.push(entry);
    } else if (entry.type === 'Gap') {
      if (!markerPlaced && nowMinuteOfDay >= entry.startMinuteOfDay && nowMinuteOfDay < entry.endMinuteOfDay) {
        if (nowMinuteOfDay > entry.startMinuteOfDay) withNow.push(DayTimelineEntry.Gap(entry.startMinuteOfDay, nowMinuteOfDay));
        placeMarker();
        if (nowMinuteOfDay < entry.endMinuteOfDay) withNow.push(DayTimelineEntry.Gap(nowMinuteOfDay, entry.endMinuteOfDay));
      } else {
        if (!markerPlaced && nowMinuteOfDay <= entry.startMinuteOfDay) placeMarker();
        withNow.push(entry);
      }
    }
  }
  if (!markerPlaced) withNow.push(DayTimelineEntry.NowMarker(nowMinuteOfDay));
  return withNow;
}

/** First free slot on [date] at or after [notBefore] long enough for [minMinutes], or null. */
export function findNextFreeSlot(
  sessions, date, minMinutes, notBefore,
  fixedCommitments = [], dayStartMinute = DAY_TIMELINE_START, dayEndMinute = DAY_TIMELINE_END,
) {
  const occupied = sortedBy([
    ...sessions.filter((s) => s.date === date && !s.completed).map((s) => Slot(s.startMinuteOfDay, s.startMinuteOfDay + s.durationMinutes)),
    ...fixedCommitments.map((f) => Slot(f.startMinuteOfDay, f.endMinuteOfDay)),
  ], (s) => s.start);
  let cursor = coerceAtLeast(notBefore, dayStartMinute);
  for (const slot of occupied) {
    if (slot.start - cursor >= minMinutes) return cursor;
    cursor = Math.max(cursor, slot.end);
  }
  return dayEndMinute - cursor >= minMinutes ? cursor : null;
}
