// Port of domain/StudyAnalytics.kt — Insights assembly. computeInsights takes one props object
// (Kotlin named args), with the same keys and defaults.
import {
  dataClass, enumClass, lazyDefault, idiv, roundToInt, coerceAtLeast, sumOf, maxOfOrNull, maxByOrNull,
  sortedBy, groupBy, chunked, toInt,
} from './kt.js';
import { plusDays, minusDays, daysBetween, minDate, formatMediumDate } from './time.js';
import { PlanMode } from './PlanMode.js';
import { leafIds, findNode } from './ExamPack.js';
import { Calibration } from './Calibration.js';
import {
  PACE_WINDOW_DAYS, REVISION_WEEK_DAYS, countsAsStudy, studiedMinutesByDate, studiedMinutes, planAdherence,
  subjectStatus, revisionsDueNow, revisionsOverdue, revisionsDueThisWeek, revisionsCompleted, revisionOnTime,
  studyDates, currentStreak, longestStreak, streakBroken, chaptersLearnedBetween, lecturesDone,
  pace, finishForecast, availableStudyTime, requiredPace,
} from './Metrics.js';

export const InsightPeriod = enumClass(['DAY', 'WEEK', 'MONTH', 'THREE_MONTHS', 'ALL'], (name) => ({
  days: { DAY: 1, WEEK: 7, MONTH: 30, THREE_MONTHS: 90, ALL: null }[name],
}));

export const InsightPlan = dataClass({
  startedOn: undefined,
  targetDate: undefined,
  weeklyAvailability: [],
  availabilityOverrides: [],
  weekdayTargetMinutes: 4 * 60,
  weekendTargetMinutes: 7 * 60,
  planMode: lazyDefault(() => PlanMode.DEADLINE),
});

export const StudyDayInsight = dataClass({ date: undefined, minutes: undefined, sessions: undefined });

export const SubjectInsight = dataClass({
  key: undefined, name: undefined, subjectId: undefined, percent: undefined, remainingSections: undefined,
  totalSections: undefined, completedSections: undefined, status: undefined,
});

export const RevisionInsight = dataClass({
  dueNow: 0, overdue: 0, dueThisWeek: 0, completedThisWeek: 0, onTimePercent: null, dueTitles: [],
});

export const ForecastInsight = dataClass({
  targetDate: undefined, hasTarget: true, forecastDate: undefined, daysDelta: undefined,
  recentAverageMinutesPerDay: undefined, requiredMinutesPerDay: undefined, extraMinutesPerDay: undefined,
  hasReliablePace: undefined, forecastEarliestDate: null, forecastLatestDate: null,
  evidenceActiveDays: 0, evidenceWindowDays: PACE_WINDOW_DAYS,
});

export const InsightsData = dataClass({
  period: undefined, hasStudyHistory: undefined, hasStudyInPeriod: undefined, periodStart: undefined,
  periodDays: undefined, trendDays: undefined, heatmapDays: undefined, totalStudiedMinutes: undefined,
  completedSessionCount: undefined, averagePerActiveDayMinutes: undefined, bestDay: undefined,
  plannedMinutes: undefined, completedPlannedMinutes: undefined, extraStudyMinutes: undefined,
  planPercent: undefined, planDeltaMinutes: undefined, syllabusTotalSections: undefined,
  syllabusCompletedSections: undefined, syllabusRemainingSections: undefined, syllabusPercent: undefined,
  officialSectionCount: undefined, excludedSectionCount: undefined, targetMinutes: undefined,
  remainingMinutes: undefined, subjects: undefined, forecast: undefined, revision: undefined,
  averageSessionMinutes: undefined, longestSessionMinutes: undefined, averageBreakMinutes: undefined,
  studyDaysInPeriod: undefined, currentStreak: undefined, longestStreak: undefined,
  calibration: lazyDefault(() => Calibration.NONE),
  fit: null,
  chaptersLearnedThisWeek: 0,
  lecturesDone: null,
  streakBroken: false,
});

export const HEATMAP_DAYS = 84;

/** Calculates all Insights values from prepared local state. */
export function computeInsights({
  pack, syllabus, sessions, planBlocks, revisionStates, plan, period, today, nowMinuteOfDay,
  calibration = Calibration.NONE, fit = null, progress = [], coaching = [], zone = null,
}) {
  const counted = sessions.filter(countsAsStudy);
  let periodStart;
  if (period.days != null) periodStart = minusDays(today, period.days - 1);
  else if (counted.length > 0) periodStart = minDate(counted.reduce((m, s) => (s.studyDate < m ? s.studyDate : m), counted[0].studyDate), today);
  else periodStart = today;
  const minutesByDate = studiedMinutesByDate(sessions);
  const sessionsByDate = new Map();
  for (const s of counted) sessionsByDate.set(s.studyDate, (sessionsByDate.get(s.studyDate) ?? 0) + 1);
  const day = (date) => StudyDayInsight({ date, minutes: minutesByDate.get(date) ?? 0, sessions: sessionsByDate.get(date) ?? 0 });

  const span = daysBetween(periodStart, today);
  const periodDays = [];
  for (let i = 0; i <= span; i++) periodDays.push(day(plusDays(periodStart, i)));
  const trendDays = aggregateTrend(periodDays, period);
  const heatmapDays = Array.from({ length: HEATMAP_DAYS }, (_, i) => day(minusDays(today, HEATMAP_DAYS - 1 - i)));

  const periodSessions = counted.filter((s) => s.studyDate >= periodStart && s.studyDate <= today);
  const totalMinutes = studiedMinutes(sessions, periodStart, today);
  const activeDays = periodDays.filter((d) => d.minutes > 0 || d.sessions > 0);
  const adherence = planAdherence(planBlocks, sessions, periodStart, today, today, nowMinuteOfDay);
  const plannedSessions = periodSessions.filter((s) => s.planBlockId != null);
  const extraStudyMinutes = coerceAtLeast(totalMinutes - idiv(sumOf(plannedSessions, (s) => s.focusedSeconds), 60), 0);

  const overall = syllabus.overall;
  const officialLeafCount = leafIds(pack).length;
  const remaining = syllabus.remainingMinutes;
  const subjects = [];
  for (const subject of pack.subjects) {
    const cover = syllabus.bySubject.get(subject.id);
    if (cover == null) throw new Error(`NoSuchElementException: Key ${subject.id} is missing in the map.`);
    if (cover.total === 0) continue;
    subjects.push(SubjectInsight({
      key: subject.id, name: subject.name, subjectId: subject.id, percent: cover.percent,
      remainingSections: cover.remaining, totalSections: cover.total, completedSections: cover.covered,
      status: plan.planMode === PlanMode.DEADLINE ? subjectStatus(cover.percent, plan.startedOn, plan.targetDate, today) : null,
    }));
  }

  const forecast = forecastInsight(sessions, remaining, plan, today);

  const weekStart = minusDays(today, REVISION_WEEK_DAYS - 1);
  const dueNow = revisionsDueNow(revisionStates, today);
  const revision = RevisionInsight({
    dueNow: dueNow.length,
    overdue: revisionsOverdue(revisionStates, today).length,
    dueThisWeek: revisionsDueThisWeek(revisionStates, today).length,
    completedThisWeek: revisionsCompleted(sessions, weekStart, today),
    onTimePercent: revisionOnTime(sessions, revisionStates, periodStart, today, today).percent,
    dueTitles: dueNow.map((s) => findNode(pack, s.nodeId)?.title).filter((t) => t != null),
  });

  const dates = studyDates(sessions);
  return InsightsData({
    period,
    hasStudyHistory: counted.length > 0,
    hasStudyInPeriod: periodSessions.length > 0,
    periodStart,
    periodDays,
    trendDays,
    heatmapDays,
    totalStudiedMinutes: totalMinutes,
    completedSessionCount: periodSessions.length,
    averagePerActiveDayMinutes: activeDays.length > 0 ? idiv(totalMinutes, activeDays.length) : null,
    bestDay: maxByOrNull(activeDays, (d) => d.minutes),
    plannedMinutes: adherence.plannedMinutes,
    completedPlannedMinutes: adherence.completedMinutes,
    extraStudyMinutes,
    planPercent: adherence.percent,
    planDeltaMinutes: adherence.completedMinutes - adherence.plannedMinutes,
    syllabusTotalSections: overall.total,
    syllabusCompletedSections: overall.covered,
    syllabusRemainingSections: overall.remaining,
    syllabusPercent: overall.percent,
    officialSectionCount: officialLeafCount,
    excludedSectionCount: officialLeafCount - overall.total,
    targetMinutes: syllabus.targetMinutes,
    remainingMinutes: remaining,
    subjects,
    forecast,
    revision,
    averageSessionMinutes: periodSessions.length > 0 ? idiv(idiv(sumOf(periodSessions, (s) => s.focusedSeconds), 60), periodSessions.length) : null,
    longestSessionMinutes: maxOfOrNull(periodSessions, (s) => idiv(s.focusedSeconds, 60)),
    averageBreakMinutes: averageBreakMinutes(periodSessions),
    studyDaysInPeriod: activeDays.length,
    currentStreak: currentStreak(dates, today),
    longestStreak: longestStreak(dates),
    calibration,
    fit,
    chaptersLearnedThisWeek: chaptersLearnedBetween(progress, syllabus.targetLeafIds, weekStart, today, zone),
    lecturesDone: coaching.some((c) => !c.isEmpty) ? lecturesDone(coaching) : null,
    streakBroken: streakBroken(dates, today),
  });
}

function aggregateTrend(days, period) {
  if (days.length === 0) return [];
  let bucketSize;
  switch (period) {
    case InsightPeriod.DAY: case InsightPeriod.WEEK: bucketSize = 1; break;
    case InsightPeriod.MONTH: bucketSize = 5; break;
    case InsightPeriod.THREE_MONTHS: bucketSize = 7; break;
    default: bucketSize = coerceAtLeast(toInt(Math.ceil(days.length / 8.0)), 1);
  }
  return chunked(days, bucketSize).map((bucket) => StudyDayInsight({
    date: bucket[0].date,
    minutes: sumOf(bucket, (d) => d.minutes),
    sessions: sumOf(bucket, (d) => d.sessions),
  }));
}

function forecastInsight(sessions, remainingMinutes, plan, today) {
  const allPace = pace(sessions, today);
  const learnPace = pace(sessions, today, true);
  const forecast = finishForecast(remainingMinutes, learnPace, today);
  const available = availableStudyTime(today, plan.targetDate, plan.weeklyAvailability, plan.availabilityOverrides,
    plan.weekdayTargetMinutes, plan.weekendTargetMinutes);
  const deadline = plan.planMode === PlanMode.DEADLINE;
  const perDay = requiredPace(remainingMinutes, available).minutesPerStudyDay;
  const required = perDay != null && remainingMinutes > 0 && deadline ? perDay : null;
  const recentAverage = allPace != null ? roundToInt(allPace.minutesPerDay) : null;
  return ForecastInsight({
    targetDate: plan.targetDate,
    hasTarget: plan.planMode === PlanMode.DEADLINE,
    forecastDate: forecast?.expected ?? null,
    daysDelta: forecast != null && deadline ? daysBetween(plan.targetDate, forecast.expected) : null,
    recentAverageMinutesPerDay: recentAverage,
    requiredMinutesPerDay: required,
    extraMinutesPerDay: recentAverage != null && required != null && required > recentAverage ? required - recentAverage : 0,
    hasReliablePace: forecast != null,
    forecastEarliestDate: forecast?.earliest ?? null,
    forecastLatestDate: forecast?.latest ?? null,
    evidenceActiveDays: allPace?.activeDays ?? activeDaysInPaceWindow(sessions, today),
    evidenceWindowDays: PACE_WINDOW_DAYS,
  });
}

function activeDaysInPaceWindow(sessions, today) {
  const start = minusDays(today, PACE_WINDOW_DAYS - 1);
  return new Set(sessions.filter((s) => countsAsStudy(s) && s.studyDate >= start && s.studyDate <= today).map((s) => s.studyDate)).size;
}

function averageBreakMinutes(sessions) {
  const gaps = [];
  for (const day of groupBy(sessions, (s) => s.studyDate).values()) {
    const sorted = sortedBy(day, (s) => s.startedAtEpochMs);
    for (let i = 0; i + 1 < sorted.length; i++) {
      const first = sorted[i], second = sorted[i + 1];
      const firstEnd = first.endedAtEpochMs ?? first.startedAtEpochMs + first.focusedSeconds * 1000;
      const gap = Math.trunc((second.startedAtEpochMs - firstEnd) / 60_000);
      if (gap >= 1 && gap <= 180) gaps.push(gap);
    }
  }
  return gaps.length > 0 ? roundToInt(sumOf(gaps) / gaps.length) : null;
}

/** FormatStyle.MEDIUM in the default locale. */
export const formatInsightDate = (date) => formatMediumDate(date);

export function formatInsightMinutes(minutes) {
  if (minutes == null) return '—';
  const hours = idiv(minutes, 60);
  const remainder = minutes % 60;
  if (hours === 0) return `${remainder}m`;
  if (remainder === 0) return `${hours}h`;
  return `${hours}h ${remainder}m`;
}
