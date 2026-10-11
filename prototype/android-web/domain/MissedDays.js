// Port of domain/MissedDays.kt
import { minusDays } from './time.js';
import { StudyActivityType, PlanBlockStatus } from './StudyPlan.js';
import { countsAsStudy } from './Metrics.js';
import { isRegenerable } from './RollingPlan.js';

/** Past planner blocks the student never did (§4.4). */
export function missedPlannedBlocks(blocks, sessions, today, since) {
  const doneBlockIds = new Set(sessions.filter((s) => countsAsStudy(s)).map((s) => s.planBlockId).filter((x) => x != null));
  return blocks.filter((b) =>
    b.scheduledDate >= since &&
    b.scheduledDate < today &&
    isRegenerable(b) &&
    b.activityType !== StudyActivityType.CLASS && b.activityType !== StudyActivityType.BREAK &&
    !doneBlockIds.has(b.id));
}

/** Rebalance step one: the missed blocks as SKIPPED. */
export const skipMissedBlocks = (missed, nowMs) =>
  missed.map((b) => b.copy({ status: PlanBlockStatus.SKIPPED, updatedAtEpochMs: nowMs }));

/** Run of missed days ending at the latest one; a rest day does not break it. plannedDates: Set<date>. */
export function missedDayCount(missed, plannedDates = new Set()) {
  const missedDates = [...new Set(missed.map((b) => b.scheduledDate))].sort();
  if (missedDates.length === 0) return 0;
  const missedSet = new Set(missedDates);
  const latest = missedDates[missedDates.length - 1];
  let n = 0;
  let cursor = latest;
  const earliest = missedDates[0];
  while (cursor >= earliest) {
    if (missedSet.has(cursor)) n++;
    else if (plannedDates.has(cursor)) return n;
    cursor = minusDays(cursor, 1);
  }
  return n;
}

/** Two or more missed days, or a broken streak with missed blocks: offer "Start fresh from today". */
export const FRESH_START_MISSED_DAYS = 2;

export const shouldStartFresh = (missedDays, streakBroken) =>
  missedDays >= FRESH_START_MISSED_DAYS || (missedDays >= 1 && streakBroken);
