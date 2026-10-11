// Port of domain/Metrics.kt — canonical metrics (§9). Pure: callers pass today/now.
// Sets in/out are JS Set; maps are JS Map. `zone` params are ignored (local zone).
import {
  dataClass, enumClass, idiv, roundToInt, coerceAtLeast, coerceIn, sumOf, sortedBy, sortedWith,
  compareByDescending, thenBy, groupBy, toInt,
} from './kt.js';
import { DayOfWeek, dayOfWeek, plusDays, minusDays, daysBetween, epochMsToLocalDate } from './time.js';
import { leafIds, flatten, chapters } from './ExamPack.js';
import { effectiveTargetLeafIds } from './TargetSyllabus.js';
import { TopicProgressStatus } from './TopicProgress.js';
import { StudySessionStatus } from './StudyHistory.js';
import { StudyActivityType, PlanBlockStatus } from './StudyPlan.js';
import { effectiveWindowsFor } from './Availability.js';
import {
  CoachingBacklog, coachingBacklogBySubject, backlogMinutes, backlogDailyMinutes,
  BACKLOG_LECTURE_MINUTES, BACKLOG_DPP_MINUTES,
} from './CoachingBacklog.js';
import { MockSubject } from './MockTest.js';

const inRange = (d, from, to) => d >= from && d <= to;

// ---- Coverage ----
export const Coverage = dataClass(
  { covered: undefined, total: undefined },
  {
    /** Rounded down: 99.9% is 99%, never 100%. */
    get percent() { return this.total <= 0 ? 0 : idiv(this.covered * 100, this.total); },
    get remaining() { return coerceAtLeast(this.total - this.covered, 0); },
  },
);

/** Ids marked covered: Set<String>. */
export const coveredNodeIds = (progress) =>
  new Set([...progress].filter((p) => p.status === TopicProgressStatus.COVERED).map((p) => p.nodeId));

/** Covered ÷ target; covered ids outside target don't count. */
export function coverage(targetIds, coveredIds) {
  let c = 0;
  for (const id of targetIds) if (coveredIds.has(id)) c++;
  return Coverage({ covered: c, total: targetIds.size });
}

/** ExamPack.subjectCoverage(targetLeafIds, coveredIds): Map<subjectId, Coverage> */
export function subjectCoverage(pack, targetLeafIds, coveredIds) {
  const m = new Map();
  for (const subject of pack.subjects) {
    m.set(subject.id, coverage(new Set(leafIds(subject).filter((id) => targetLeafIds.has(id))), coveredIds));
  }
  return m;
}

// ---- Remaining work ----
/** ExamPack.leafMinutes(): Map<leafId, Int> */
export function leafMinutes(pack) {
  const result = new Map();
  for (const subject of pack.subjects) {
    for (const leaf of subject.nodes.flatMap((n) => flatten(n)).filter((n) => n.children.length === 0)) {
      result.set(leaf.id, coerceAtLeast(leaf.estimatedMinutes ?? 0, 0));
    }
  }
  return result;
}

const scaled = (minutes, factor) => roundToInt(minutes * factor);

export function remainingMinutes(pack, targetLeafIds, coveredIds, calibrationFactor = 1.0) {
  let s = 0;
  for (const [id, minutes] of leafMinutes(pack)) if (targetLeafIds.has(id) && !coveredIds.has(id)) s += minutes;
  return scaled(s, calibrationFactor);
}

export function targetMinutes(pack, targetLeafIds, calibrationFactor = 1.0) {
  let s = 0;
  for (const [id, minutes] of leafMinutes(pack)) if (targetLeafIds.has(id)) s += minutes;
  return scaled(s, calibrationFactor);
}

/** Map<subjectId, Int> */
export function remainingMinutesBySubject(pack, targetLeafIds, coveredIds, calibrationFactor = 1.0) {
  const minutes = leafMinutes(pack);
  const m = new Map();
  for (const subject of pack.subjects) {
    m.set(subject.id, scaled(sumOf(leafIds(subject), (id) => (targetLeafIds.has(id) && !coveredIds.has(id) ? minutes.get(id) ?? 0 : 0)), calibrationFactor));
  }
  return m;
}

/** Format minutes as whole hours only after summing. */
export const minutesToHours = (minutes) => roundToInt(minutes / 60.0);

/** Map<nodeId, TargetNodeState> */
export const targetStates = (overrides) => new Map([...overrides].map((o) => [o.nodeId, o.state]));

export const SyllabusMetrics = dataClass({
  targetLeafIds: undefined, coveredIds: undefined, overall: undefined, bySubject: undefined,
  targetMinutes: undefined, remainingMinutes: undefined, remainingMinutesBySubject: undefined,
});

/** overrides: Map<String, TargetNodeState> */
export function syllabusMetrics(pack, overrides, progress, calibrationFactor = 1.0) {
  const target = effectiveTargetLeafIds(pack, overrides);
  const covered = coveredNodeIds(progress);
  return SyllabusMetrics({
    targetLeafIds: target,
    coveredIds: covered,
    overall: coverage(target, covered),
    bySubject: subjectCoverage(pack, target, covered),
    targetMinutes: targetMinutes(pack, target, calibrationFactor),
    remainingMinutes: remainingMinutes(pack, target, covered, calibrationFactor),
    remainingMinutesBySubject: remainingMinutesBySubject(pack, target, covered, calibrationFactor),
  });
}

// ---- Studied time ----
/** StudySession.countsAsStudy(): completed, including partial saves. */
export const countsAsStudy = (s) => s.status === StudySessionStatus.COMPLETED && s.focusedSeconds > 0;

export function studiedSeconds(sessions, from = null, to = null) {
  let s = 0;
  for (const x of sessions) {
    if (!countsAsStudy(x)) continue;
    if ((from == null || x.studyDate >= from) && (to == null || x.studyDate <= to)) s += x.focusedSeconds;
  }
  return s;
}

export const studiedMinutes = (sessions, from = null, to = null) => idiv(studiedSeconds(sessions, from, to), 60);

/** One session's focused time in whole minutes. */
export const focusedMinutes = (session) => idiv(session.focusedSeconds, 60);

/** Map<date, Int> */
export function studiedMinutesByDate(sessions) {
  const g = groupBy([...sessions].filter(countsAsStudy), (s) => s.studyDate);
  const m = new Map();
  for (const [d, day] of g) m.set(d, idiv(sumOf(day, (s) => s.focusedSeconds), 60));
  return m;
}

// ---- Plan adherence ----
export const PlanAdherence = dataClass(
  { plannedMinutes: undefined, completedMinutes: undefined },
  {
    get percent() {
      return this.plannedMinutes > 0 ? coerceIn(roundToInt((this.completedMinutes / this.plannedMinutes) * 100), 0, 100) : null;
    },
  },
);

/** StudyPlanBlock.hasEnded(today, nowMinuteOfDay) */
export const hasEnded = (block, today, nowMinuteOfDay) =>
  block.scheduledDate < today || (block.scheduledDate === today && block.startMinuteOfDay + block.plannedMinutes <= nowMinuteOfDay);

export function planAdherence(blocks, sessions, from, to, today, nowMinuteOfDay) {
  const g = groupBy(sessions.filter((s) => countsAsStudy(s) && s.planBlockId != null), (s) => s.planBlockId);
  const focusedByBlock = new Map();
  for (const [k, linked] of g) focusedByBlock.set(k, idiv(sumOf(linked, (s) => s.focusedSeconds), 60));
  const due = blocks.filter((b) =>
    inRange(b.scheduledDate, from, to) &&
    b.status !== PlanBlockStatus.RESCHEDULED &&
    b.status !== PlanBlockStatus.SKIPPED &&
    b.activityType !== StudyActivityType.BREAK &&
    b.activityType !== StudyActivityType.CLASS &&
    hasEnded(b, today, nowMinuteOfDay));
  const completed = sumOf(due, (b) => {
    const focused = focusedByBlock.get(b.id);
    if (focused != null) return Math.min(focused, b.plannedMinutes);
    if (b.status === PlanBlockStatus.COMPLETED) return b.plannedMinutes;
    return 0;
  });
  return PlanAdherence({ plannedMinutes: sumOf(due, (b) => b.plannedMinutes), completedMinutes: completed });
}

// ---- Pace, forecast, required pace ----
export const PACE_WINDOW_DAYS = 14;
export const PACE_MIN_ACTIVE_DAYS = 7;
export const FORECAST_FAST_FACTOR = 1.15;
export const FORECAST_SLOW_FACTOR = 0.85;

export const Pace = dataClass({ minutesPerDay: undefined, activeDays: undefined, windowDays: PACE_WINDOW_DAYS });

export function pace(sessions, today, learnOnly = false) {
  const start = minusDays(today, PACE_WINDOW_DAYS - 1);
  const window = sessions.filter((s) => countsAsStudy(s) && inRange(s.studyDate, start, today));
  const activeDays = new Set(window.map((s) => s.studyDate)).size;
  if (activeDays < PACE_MIN_ACTIVE_DAYS) return null;
  const counted = learnOnly ? window.filter((s) => s.activityType === StudyActivityType.LEARN) : window;
  return Pace({ minutesPerDay: sumOf(counted, (s) => s.focusedSeconds) / 60.0 / PACE_WINDOW_DAYS, activeDays });
}

export const FinishForecast = dataClass({ earliest: undefined, expected: undefined, latest: undefined });

export function finishForecast(remainingMinutesValue, learnPace, today) {
  const perDay = learnPace?.minutesPerDay;
  if (perDay == null || !(perDay > 0.0)) return null;
  const finishAt = (rate) => plusDays(today, remainingMinutesValue <= 0 ? 0 : Math.ceil(remainingMinutesValue / rate));
  return FinishForecast({
    earliest: finishAt(perDay * FORECAST_FAST_FACTOR),
    expected: finishAt(perDay),
    latest: finishAt(perDay * FORECAST_SLOW_FACTOR),
  });
}

export const AvailableStudyTime = dataClass({ minutes: undefined, days: undefined });

export function availableStudyTime(from, to, weekly, overrides, weekdayTargetMinutes, weekendTargetMinutes) {
  if (to < from) return AvailableStudyTime({ minutes: 0, days: 0 });
  let minutes = 0;
  let days = 0;
  let date = from;
  while (date <= to) {
    const windowMinutes = sumOf(effectiveWindowsFor(date, weekly, overrides), ([s, e]) => coerceAtLeast(e - s, 0));
    const dow = dayOfWeek(date);
    const target = dow === DayOfWeek.SATURDAY || dow === DayOfWeek.SUNDAY ? weekendTargetMinutes : weekdayTargetMinutes;
    const usable = Math.min(windowMinutes, coerceAtLeast(target, 0));
    if (usable > 0) { minutes += usable; days++; }
    date = plusDays(date, 1);
  }
  return AvailableStudyTime({ minutes, days });
}

export const RequiredPace = dataClass(
  { remainingMinutes: undefined, available: undefined },
  {
    get minutesPerStudyDay() {
      if (this.remainingMinutes <= 0) return 0;
      if (this.available.days <= 0) return null;
      return toInt(Math.ceil(this.remainingMinutes / this.available.days));
    },
    get load() { return this.available.minutes <= 0 ? null : this.remainingMinutes / this.available.minutes; },
    get fits() { return this.remainingMinutes <= this.available.minutes; },
  },
);

export const requiredPace = (remaining, available) => RequiredPace({ remainingMinutes: coerceAtLeast(remaining, 0), available });

// ---- Subject status ----
export const SUBJECT_STATUS_MARGIN_PERCENT = 5;
export const SUBJECT_STATUS_HIDDEN_DAYS = 7;
export const SubjectStatus = enumClass(['AHEAD', 'ON_TRACK', 'BEHIND']);

export function subjectStatus(percent, startedOn, targetDate, today) {
  const elapsed = daysBetween(startedOn, today);
  if (elapsed < SUBJECT_STATUS_HIDDEN_DAYS) return null;
  const totalDays = coerceAtLeast(daysBetween(startedOn, targetDate), 1);
  const expected = idiv(coerceIn(elapsed, 0, totalDays) * 100, totalDays);
  if (percent >= expected + SUBJECT_STATUS_MARGIN_PERCENT) return SubjectStatus.AHEAD;
  if (percent <= expected - SUBJECT_STATUS_MARGIN_PERCENT) return SubjectStatus.BEHIND;
  return SubjectStatus.ON_TRACK;
}

// ---- Streak and dates ----
/** Set<date> with at least one counting session. */
export const studyDates = (sessions) => new Set([...sessions].filter(countsAsStudy).map((s) => s.studyDate));

export function currentStreak(dates, today) {
  let cursor = dates.has(today) ? today : minusDays(today, 1);
  let n = 0;
  while (dates.has(cursor)) { n++; cursor = minusDays(cursor, 1); }
  return n;
}

export function longestStreak(dates) {
  let longest = 0, current = 0, previous = null;
  for (const date of [...dates].sort()) {
    current = previous != null && plusDays(previous, 1) === date ? current + 1 : 1;
    longest = Math.max(longest, current);
    previous = date;
  }
  return longest;
}

export const streakBroken = (dates, today) => currentStreak(dates, today) === 0 && [...dates].some((d) => d < today);

/** Days from today to targetDate, never negative. */
export const daysToTarget = (targetDate, today) => coerceAtLeast(daysBetween(today, targetDate), 0);

// ---- Revision ----
export const REVISION_WEEK_DAYS = 7;
const dueOrNull = (s) => (s.enabled ? s.nextDueDate : null);

/** Due today or overdue, most overdue first. */
export const revisionsDueNow = (states, today) =>
  sortedBy([...states].filter((s) => { const d = dueOrNull(s); return d != null && d <= today; }), (s) => s.nextDueDate);

export const revisionsOverdue = (states, today) =>
  [...states].filter((s) => { const d = dueOrNull(s); return d != null && d < today; });

export function revisionsDueThisWeek(states, today) {
  const end = plusDays(today, REVISION_WEEK_DAYS - 1);
  return [...states].filter((s) => { const d = dueOrNull(s); return d != null && d <= end; });
}

export const revisionsCompleted = (sessions, from, to) =>
  [...sessions].filter((s) => s.activityType === StudyActivityType.REVISION && countsAsStudy(s) && inRange(s.studyDate, from, to)).length;

// ---- Progress ----
export const chaptersLearnedBetween = (progress, targetIds, from, to, zone) =>
  [...progress].filter((p) => p.status === TopicProgressStatus.COVERED && targetIds.has(p.nodeId) &&
    p.coveredAtEpochMs != null && inRange(epochMsToLocalDate(p.coveredAtEpochMs), from, to)).length;

export const lecturesDone = (coaching) => sumOf([...coaching], (c) => c.normalized().lecturesDone);

export const RevisionOnTime = dataClass(
  { onTime: undefined, due: undefined },
  { get percent() { return this.due <= 0 ? null : idiv(this.onTime * 100, this.due); } },
);

export function revisionOnTime(sessions, states, from, to, today) {
  const completed = [...sessions].filter((s) =>
    s.activityType === StudyActivityType.REVISION && s.status === StudySessionStatus.COMPLETED &&
    s.dueDateAtCompletion != null && inRange(s.dueDateAtCompletion, from, to));
  const onTime = completed.filter((s) => s.studyDate <= s.dueDateAtCompletion).length;
  const stillOverdue = revisionsOverdue(states, today).filter((s) => inRange(s.nextDueDate, from, to)).length;
  return RevisionOnTime({ onTime, due: completed.length + stillOverdue });
}

// ---- Coaching backlog ----
export function backlogClearDate(backlog, dailyMinutes, liveSplitPct, today, lectureMin = BACKLOG_LECTURE_MINUTES, dppMin = BACKLOG_DPP_MINUTES) {
  const need = backlogMinutes(backlog, lectureMin, dppMin);
  if (need <= 0) return null;
  const perDay = backlogDailyMinutes(dailyMinutes, liveSplitPct);
  if (perDay <= 0) return null;
  const days = idiv(need + perDay - 1, perDay);
  return plusDays(today, days - 1);
}

export const averageDailyMinutes = (weekdayTargetMinutes, weekendTargetMinutes) =>
  idiv(coerceAtLeast(weekdayTargetMinutes, 0) * 5 + coerceAtLeast(weekendTargetMinutes, 0) * 2, 7);

export const CoachingBacklogMetrics = dataClass({ bySubject: undefined, total: undefined, clearDate: undefined });

export function coachingBacklogMetrics(coaching, position, pack, dailyMinutes, liveSplitPct, today) {
  const order = chapters(pack).map((c) => c.id);
  const subjectOf = new Map(pack.subjects.flatMap((subject) => subject.nodes.map((n) => [n.id, subject.id])));
  const bySubject = coachingBacklogBySubject(coaching, position, order, subjectOf);
  const total = [...bySubject.values()].reduce((sum, part) => sum.plus(part), CoachingBacklog.NONE);
  return CoachingBacklogMetrics({ bySubject, total, clearDate: backlogClearDate(total, dailyMinutes, liveSplitPct, today) });
}

// ---- Mocks and mistakes ----
export const MOST_LOST_LIMIT = 5;
export const LostChapter = dataClass({ chapterId: undefined, count: undefined });

export function mostLostChapters(mistakes, limit = MOST_LOST_LIMIT) {
  const counts = new Map();
  for (const m of mistakes) counts.set(m.chapterId, (counts.get(m.chapterId) ?? 0) + 1);
  return sortedWith([...counts].map(([id, n]) => LostChapter({ chapterId: id, count: n })),
    thenBy(compareByDescending((x) => x.count), (x) => x.chapterId)).slice(0, limit);
}

/** subjects: Map<MockSubject, Int> */
export const MockTrendPoint = dataClass({ mockId: undefined, date: undefined, total: undefined, maxTotal: undefined, subjects: undefined });

export const mockTrend = (mocks) =>
  sortedBy([...mocks], (m) => m.date).map((mock) => MockTrendPoint({
    mockId: mock.id, date: mock.date, total: mock.total, maxTotal: mock.maxTotal,
    subjects: new Map(MockSubject.entries.map((s) => [s, mock.score(s)]).filter(([, v]) => v != null)),
  }));
