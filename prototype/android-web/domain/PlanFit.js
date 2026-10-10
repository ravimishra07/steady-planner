// Port of domain/PlanFit.kt — whether the remaining work fits before finish-by (§3.4, §4.4).
import { dataClass, idiv, coerceAtLeast, coerceAtMost, sortedWith, compareBy } from './kt.js';
import { plusDays, minusDays, daysBetween } from './time.js';
import { PlanMode } from './PlanMode.js';
import { PriorityBand } from './ChapterPriority.js';
import { plan, chapterNeeds } from './RollingPlan.js';

export const PlanFit = dataClass(
  {
    finishBy: undefined,
    learnMinutes: undefined,
    practiceMinutes: undefined,
    shortByMinutes: undefined,
    revisionWindowDays: undefined,
    calibrated: false,
    /** PlanFitOption[] */
    options: [],
  },
  {
    get requiredMinutes() { return this.learnMinutes + this.practiceMinutes; },
    get scheduledMinutes() { return coerceAtLeast(this.requiredMinutes - this.shortByMinutes, 0); },
    get fits() { return this.shortByMinutes <= 0; },
    /** Share of the remaining work covered by finish-by, rounded down. 100 when it fits. */
    get coveragePercent() {
      return this.requiredMinutes <= 0 || this.fits ? 100 : idiv(this.scheduledMinutes * 100, this.requiredMinutes);
    },
  },
);

/** sealed PlanFitOption — tagged objects: {type:'AddMinutesPerDay', minutes} | {type:'ParkChapters', chapterIds, minutesFreed, count} | {type:'MoveFinishBy', date} | {type:'AcceptCoverage', percent} */
export const PlanFitOption = Object.freeze({
  AddMinutesPerDay: (minutes) => Object.freeze({ type: 'AddMinutesPerDay', minutes }),
  ParkChapters: (chapterIds, minutesFreed) => Object.freeze({ type: 'ParkChapters', chapterIds, minutesFreed, count: chapterIds.length }),
  MoveFinishBy: (date) => Object.freeze({ type: 'MoveFinishBy', date }),
  AcceptCoverage: (percent) => Object.freeze({ type: 'AcceptCoverage', percent }),
});

export const PLAN_FIT_MINUTES_STEP = 15;
export const PLAN_FIT_MAX_EXTRA_MINUTES = 8 * 60;

const INT_MAX = 2147483647;

/** PlanInputs.planFit(nowMs, withOptions = true): PlanFit|null (null in steady mode). */
export function planFit(inp, nowMs, withOptions = true) {
  if (inp.mode !== PlanMode.DEADLINE) return null;
  const fit = plan(inp, 1, nowMs).fit;
  if (fit == null) return null;
  if (fit.fits || !withOptions) return fit;
  return fit.copy({ options: planFitOptions(inp, fit, nowMs) });
}

const fitsAt = (inp, nowMs) => planFit(inp, nowMs, false)?.fits === true;

function planFitOptions(inp, fit, nowMs) {
  const out = [];
  const a = addMinutesOption(inp, nowMs); if (a) out.push(a);
  const p = parkOption(inp, fit, nowMs); if (p) out.push(p);
  const m = moveFinishByOption(inp, nowMs); if (m) out.push(m);
  out.push(PlanFitOption.AcceptCoverage(fit.coveragePercent));
  return out;
}

const plusCapped = (v, extra) => coerceAtMost(v + extra, INT_MAX);

function addMinutesOption(inp, nowMs) {
  if (inp.weekdayTargetMinutes >= Math.trunc(INT_MAX / 2) || inp.weekendTargetMinutes >= Math.trunc(INT_MAX / 2)) return null;
  const fitsWith = (units) => fitsAt(inp.copy({
    weekdayTargetMinutes: plusCapped(inp.weekdayTargetMinutes, units * PLAN_FIT_MINUTES_STEP),
    weekendTargetMinutes: plusCapped(inp.weekendTargetMinutes, units * PLAN_FIT_MINUTES_STEP),
  }), nowMs);
  const maxUnits = idiv(PLAN_FIT_MAX_EXTRA_MINUTES, PLAN_FIT_MINUTES_STEP);
  if (!fitsWith(maxUnits)) return null;
  let low = 1, high = maxUnits;
  while (low < high) {
    const mid = idiv(low + high, 2);
    if (fitsWith(mid)) high = mid; else low = mid + 1;
  }
  return PlanFitOption.AddMinutesPerDay(low * PLAN_FIT_MINUTES_STEP);
}

function parkOption(inp, fit, nowMs) {
  const candidates = sortedWith(chapterNeeds(inp, nowMs).filter((c) => c.totalMinutes > 0), compareBy(
    (c) => parkRank(inp.priorities.get(c.chapterId)?.band ?? PriorityBand.NO_DATA),
    (c) => inp.priorities.get(c.chapterId)?.meanQuestionsPerYear ?? 0.0,
    (c) => -c.totalMinutes,
    (c) => c.chapterId,
  ));
  const parked = [];
  let freed = 0;
  for (const chapter of candidates) {
    parked.push(chapter);
    freed += chapter.totalMinutes;
    if (freed < fit.shortByMinutes) continue;
    const parkedLeaves = new Set(parked.flatMap((c) => c.leafIds));
    const remaining = new Set([...inp.includedLeafIds].filter((id) => !parkedLeaves.has(id)));
    if (fitsAt(inp.copy({ includedLeafIds: remaining }), nowMs)) {
      return PlanFitOption.ParkChapters(parked.map((c) => c.chapterId), freed);
    }
  }
  return null;
}

/** Park low yield first, then unknown, then medium, then high. */
function parkRank(band) {
  switch (band) {
    case PriorityBand.LOW: return 0;
    case PriorityBand.NO_DATA: return 1;
    case PriorityBand.MEDIUM: return 2;
    default: return 3;
  }
}

function moveFinishByOption(inp, nowMs) {
  const latest = inp.examDate != null ? minusDays(inp.examDate, 1) : plusDays(inp.targetDate, 365);
  const maxShift = daysBetween(inp.targetDate, latest);
  if (maxShift <= 0) return null;
  const fitsShift = (shift) => fitsAt(inp.copy({ targetDate: plusDays(inp.targetDate, shift) }), nowMs);
  if (!fitsShift(maxShift)) return null;
  let low = 1, high = Math.max(maxShift, 1);
  while (low < high) {
    const mid = idiv(low + high, 2);
    if (fitsShift(mid)) high = mid; else low = mid + 1;
  }
  return PlanFitOption.MoveFinishBy(plusDays(inp.targetDate, low));
}
