// Port of domain/RollingPlan.kt — the deterministic rolling planner.
// PlanInputs extension funs (plan, chapterNeeds, projectedFinish, planFit) are exported as
// functions taking the inputs first AND available as methods on a PlanInputs value.
// `zone` is accepted but times always resolve in the local zone (ZoneId.systemDefault()).
import {
  dataClass, enumClass, lazyDefault, roundToInt, coerceIn, coerceAtLeast, coerceAtMost, sumOf,
  sortedBy, sortedWith, compareBy, maxOfOrNull, groupBy, toInt,
} from './kt.js';
import { DayOfWeek, dayOfWeek, plusDays, minusDays, daysBetween, epochMsToLocalDate, minuteOfDayAt, minDate, maxDate } from './time.js';
import { leafIds as nodeLeafIds, flatten, chapters as packChapters } from './ExamPack.js';
import { StudyPlanBlock, StudyActivityType, PlanBlockStatus, PlanBlockSource } from './StudyPlan.js';
import { PlanMode } from './PlanMode.js';
import { effectiveWindowsFor } from './Availability.js';
import { PriorityBand, yieldRank } from './ChapterPriority.js';
import {
  DEFAULT_LIVE_BACKLOG_SPLIT, BACKLOG_LECTURE_MINUTES, BACKLOG_DPP_MINUTES, BacklogKind, backlogDailyMinutes,
} from './CoachingBacklog.js';
import { PlanFit, planFit as planFitFn } from './PlanFit.js';

export const DEFAULT_PLANNING_HORIZON_DAYS = 21;
export const DEFAULT_STUDY_SESSION_MINUTES = 50;
export const DEFAULT_STUDY_BREAK_MINUTES = 10;
export const PRACTICE_FRACTION = 0.25;
export const PRACTICE_MIN_MINUTES = 20;
export const PRACTICE_MAX_MINUTES = 90;
export const REVISION_BLOCK_MINUTES = 25;
export const REVISION_MIN_BLOCK_MINUTES = 20;
export const REVISION_DAY_CAP_FRACTION = 0.4;
export const REVISION_PASS_MINUTES = 45;
export const MOCK_TEST_MINUTES = 200;
export const MOCK_FAR_DAYS = 56;
export const MOCK_NEAR_DAYS = 28;
export const MIN_AUTO_BLOCK_MINUTES = 15;
export const PROJECTION_DAYS = 730;
export const ENGINE_REVISION_ID_PREFIX = 'rev_';

const INT_MAX = 2147483647;

/** Block sizes the planner uses. Titles: %1$s chapter title, %2$d lecture/DPP number. */
export const PlannerConfig = dataClass({
  practiceFraction: PRACTICE_FRACTION,
  practiceMinMinutes: PRACTICE_MIN_MINUTES,
  practiceMaxMinutes: PRACTICE_MAX_MINUTES,
  revisionBlockMinutes: REVISION_BLOCK_MINUTES,
  revisionMinBlockMinutes: REVISION_MIN_BLOCK_MINUTES,
  revisionDayCapFraction: REVISION_DAY_CAP_FRACTION,
  revisionPassMinutes: REVISION_PASS_MINUTES,
  mockMinutes: MOCK_TEST_MINUTES,
  minBlockMinutes: MIN_AUTO_BLOCK_MINUTES,
  liveBacklogSplit: DEFAULT_LIVE_BACKLOG_SPLIT,
  backlogLectureMinutes: BACKLOG_LECTURE_MINUTES,
  backlogDppMinutes: BACKLOG_DPP_MINUTES,
  backlogLectureTitle: 'Backlog: %1$s lecture %2$d',
  backlogDppTitle: 'Backlog: %1$s DPP %2$d',
});

export const RemainingWork = dataClass({ nodeId: undefined, subjectId: undefined, estimatedMinutes: undefined, remainingMinutes: undefined });

export const RollingPlanResult = dataClass({
  blocks: undefined, remainingWork: undefined, unscheduledMinutes: undefined,
  fit: null, mockTestsPlanned: 0, projectedFinish: null,
});

export const PlanningOrder = enumClass(['DEFAULT', 'HIGH_YIELD_FIRST', 'SHORTEST_FIRST']);

const PlanInputsData = dataClass(
  {
    attemptId: undefined,
    pack: undefined,
    startDate: undefined,
    targetDate: undefined,
    weeklyAvailability: undefined,
    availabilityOverrides: [],
    includedLeafIds: undefined, // default: every leaf of pack (applied in PlanInputs())
    coveredLeafIds: new Set(),
    focusedLearnMinutesByNode: new Map(),
    preservedBlocks: [],
    weekdayTargetMinutes: INT_MAX,
    weekendTargetMinutes: INT_MAX,
    preferredSessionMinutes: DEFAULT_STUDY_SESSION_MINUTES,
    breakMinutes: DEFAULT_STUDY_BREAK_MINUTES,
    planningOrder: lazyDefault(() => PlanningOrder.DEFAULT),
    mode: lazyDefault(() => PlanMode.DEADLINE),
    examDate: null,
    allowLearnInRevisionPhase: false,
    calibrationFactor: 1.0,
    calibrationActive: false,
    priorities: new Map(),
    revisionShaped: false,
    revisionStates: [],
    weakNodeCounts: new Map(),
    practicedChapterIds: new Set(),
    recentMockDates: [],
    coachingBacklog: [],
    zone: null,
    config: lazyDefault(() => PlannerConfig()),
  },
  {
    plan(horizonDays, nowMs, simulateToEnd = false) { return plan(this, horizonDays, nowMs, simulateToEnd); },
    chapterNeeds(nowMs) { return chapterNeeds(this, nowMs); },
    projectedFinish(nowMs) { return projectedFinish(this, nowMs); },
    planFit(nowMs, withOptions = true) { return planFitFn(this, nowMs, withOptions); },
  },
);

/** Everything the planner reads. Sets/Maps as JS Set/Map; dates ISO strings. */
export function PlanInputs(props) {
  return PlanInputsData({ ...props, includedLeafIds: props.includedLeafIds ?? new Set(nodeLeafIds(props.pack)) });
}

/**
 * Builds a deterministic rolling plan. Kotlin named args -> one props object with the same keys
 * (nowMs required, horizonDays defaults to DEFAULT_PLANNING_HORIZON_DAYS).
 */
export function generateRollingPlan({ horizonDays = DEFAULT_PLANNING_HORIZON_DAYS, nowMs, ...rest }) {
  return plan(PlanInputs(rest), horizonDays, nowMs);
}

// ---- Work model ----
class LeafWork {
  constructor(nodeId, subjectId, estimate, remaining) { this.nodeId = nodeId; this.subjectId = subjectId; this.estimate = estimate; this.remaining = remaining; }
}
class ChapterWork {
  constructor(id, subjectId, leaves, practiceRemaining, syllabusIndex) {
    this.id = id; this.subjectId = subjectId; this.leaves = leaves; this.practiceRemaining = practiceRemaining; this.syllabusIndex = syllabusIndex;
  }
  get learnRemaining() { return sumOf(this.leaves, (l) => l.remaining); }
  get done() { return this.learnRemaining === 0 && this.practiceRemaining === 0; }
  nextLeaf() { return this.leaves.find((l) => l.remaining > 0) ?? null; }
}

/** Per-chapter work at the start of a plan. */
export const ChapterNeed = dataClass(
  { chapterId: undefined, subjectId: undefined, leafIds: undefined, learnMinutes: undefined, practiceMinutes: undefined },
  { get totalMinutes() { return this.learnMinutes + this.practiceMinutes; } },
);

function buildWork(inp, nowMs) {
  const clock = clockAt(inp, nowMs);
  const plannedMinutesByNode = new Map();
  for (const b of inp.preservedBlocks) {
    if (b.status !== PlanBlockStatus.PLANNED || endedUnrun(b, clock)) continue;
    if (b.customTitle != null) continue; // titled block on a chapter is coaching backlog
    if (b.nodeId == null) continue;
    plannedMinutesByNode.set(b.nodeId, (plannedMinutesByNode.get(b.nodeId) ?? 0) + b.plannedMinutes);
  }
  const practiceCovered = new Set(inp.practicedChapterIds);
  for (const b of inp.preservedBlocks) {
    if (b.activityType === StudyActivityType.PRACTICE && b.status !== PlanBlockStatus.SKIPPED &&
      b.status !== PlanBlockStatus.RESCHEDULED && b.nodeId != null) practiceCovered.add(b.nodeId);
  }

  const nodeChapter = new Map();
  const nodeSubject = new Map();
  const nodeLeaves = new Map();
  const chapters = [];
  let index = 0;
  for (const subject of inp.pack.subjects) {
    for (const chapter of subject.nodes) {
      const byId = new Map();
      for (const n of flatten(chapter)) byId.set(n.id, n);
      for (const node of byId.values()) {
        nodeChapter.set(node.id, chapter.id);
        nodeSubject.set(node.id, subject.id);
        nodeLeaves.set(node.id, nodeLeafIds(node));
      }
      const leaves = [];
      for (const leafId of nodeLeafIds(chapter)) {
        const leaf = byId.get(leafId);
        if (!inp.includedLeafIds.has(leaf.id) || inp.coveredLeafIds.has(leaf.id)) continue;
        if (leaf.estimatedMinutes == null) continue;
        const estimate = scaledEstimate(inp, leaf.estimatedMinutes);
        const accountedFor = (inp.focusedLearnMinutesByNode.get(leaf.id) ?? 0) + (plannedMinutesByNode.get(leaf.id) ?? 0);
        leaves.push(new LeafWork(leaf.id, subject.id, estimate, coerceAtLeast(estimate - accountedFor, 0)));
      }
      const needsPractice = leaves.length > 0 && !practiceCovered.has(chapter.id);
      const chapterEstimate = sumOf(nodeLeafIds(chapter).filter((id) => inp.includedLeafIds.has(id)),
        (id) => scaledEstimate(inp, byId.get(id).estimatedMinutes ?? 0));
      chapters.push(new ChapterWork(chapter.id, subject.id, leaves, needsPractice ? practiceMinutesFor(inp, chapterEstimate) : 0, index++));
    }
  }
  return { chapters: chapters.filter((c) => !c.done), nodeChapter, nodeSubject, nodeLeaves };
}

const scaledEstimate = (inp, minutes) => roundToInt(coerceAtLeast(minutes, 0) * inp.calibrationFactor);

const practiceMinutesFor = (inp, chapterEstimateMinutes) =>
  coerceIn(roundToInt(chapterEstimateMinutes * inp.config.practiceFraction), inp.config.practiceMinMinutes, inp.config.practiceMaxMinutes);

function clockAt(inp, nowMs) {
  const minute = minuteOfDayAt(nowMs);
  return { date: epochMsToLocalDate(nowMs), minute, roundedMinute: toInt(Math.ceil(minute / 5.0)) * 5 };
}

/** A planned block that ended before now without being done. */
const endedUnrun = (b, clock) =>
  b.scheduledDate < clock.date || (b.scheduledDate === clock.date && b.startMinuteOfDay + b.plannedMinutes <= clock.minute);

/** PlanInputs.chapterNeeds(nowMs) */
export function chapterNeeds(inp, nowMs) {
  return buildWork(inp, nowMs).chapters.map((c) => ChapterNeed({
    chapterId: c.id, subjectId: c.subjectId, leafIds: c.leaves.map((l) => l.nodeId),
    learnMinutes: c.learnRemaining, practiceMinutes: c.practiceRemaining,
  }));
}

// ---- The simulation ----
const CLOSED_PLAN_STATUSES = new Set([PlanBlockStatus.SKIPPED, PlanBlockStatus.RESCHEDULED]);
const isNonStudy = (t) => t === StudyActivityType.CLASS || t === StudyActivityType.BREAK;

class DayPlacer {
  constructor(windows, breakMinutes) {
    this.breakMinutes = breakMinutes;
    this.free = sortedBy(windows.filter((w) => w[1] > w[0]), (w) => w[0]).map((w) => [w[0], w[1]]);
  }
  /** Puts a block of up to want minutes (at least minAcceptable) in the earliest window it fits: [start, duration]. */
  place(want, minAcceptable) {
    for (let i = 0; i < this.free.length; i++) {
      const [start, end] = this.free[i];
      if (end - start < minAcceptable) continue;
      const duration = Math.min(want, end - start);
      const next = Math.min(end, start + duration + coerceAtLeast(this.breakMinutes, 0));
      if (next >= end) this.free.splice(i, 1); else this.free[i][0] = next;
      return [start, duration];
    }
    return null;
  }
  hasRoomFor(minutes) { return this.free.some((w) => w[1] - w[0] >= minutes); }
}

const blockCountsAsStudy = (b) => !isNonStudy(b.activityType);

/** PlanInputs.projectedFinish(nowMs): date|null */
export const projectedFinish = (inp, nowMs) => plan(inp, 1, nowMs, true).projectedFinish;

/** Java String.format for the two backlog title patterns (%1$s, %2$d). */
function formatTitle(pattern, s, d) {
  return pattern.replace(/%1\$s/g, s).replace(/%2\$d/g, String(d)).replace(/%%/g, '%');
}

/** PlanInputs.plan(horizonDays, nowMs, simulateToEnd = false): RollingPlanResult */
export function plan(inp, horizonDays, nowMs, simulateToEnd = false) {
  const deadline = inp.mode === PlanMode.DEADLINE;
  const lastExamDay = inp.examDate != null ? minusDays(inp.examDate, 1) : null;
  let hardEnd;
  if (deadline) hardEnd = lastExamDay != null && lastExamDay > inp.targetDate ? lastExamDay : inp.targetDate;
  else if (simulateToEnd) hardEnd = lastExamDay ?? plusDays(inp.startDate, PROJECTION_DAYS);
  else hardEnd = lastExamDay ?? plusDays(inp.startDate, coerceAtLeast(horizonDays, 1) - 1);
  if (horizonDays <= 0 || inp.startDate > hardEnd) return RollingPlanResult({ blocks: [], remainingWork: [], unscheduledMinutes: 0 });

  const emitEnd = minDate(hardEnd, plusDays(inp.startDate, horizonDays - 1));
  const learnEnd = deadline ? inp.targetDate : null;
  let simEnd;
  if (learnEnd != null) simEnd = minDate(hardEnd, maxDate(emitEnd, learnEnd));
  else if (simulateToEnd) simEnd = hardEnd;
  else simEnd = emitEnd;

  const clock = clockAt(inp, nowMs);
  const nowDate = clock.date;
  const nowMinute = clock.roundedMinute;
  const config = inp.config;

  const model = buildWork(inp, nowMs);
  const queues = orderedQueues(inp, model);
  const learnNeeded = sumOf(model.chapters, (c) => c.learnRemaining);
  const practiceNeeded = sumOf(model.chapters, (c) => c.practiceRemaining);
  const passChapters = revisionPassOrder(inp, model);

  const pendingRevisions = sortedWith(
    [...inp.revisionStates].filter((s) => s.enabled && s.nextDueDate != null && isTargetNode(inp, s.nodeId, model)),
    compareBy((s) => s.nextDueDate, (s) => s.nodeId),
  );
  const manualRevisionNodes = new Set(inp.preservedBlocks
    .filter((b) => b.activityType === StudyActivityType.REVISION && b.status === PlanBlockStatus.PLANNED)
    .map((b) => b.nodeId).filter((x) => x != null));
  for (let i = pendingRevisions.length - 1; i >= 0; i--) if (manualRevisionNodes.has(pendingRevisions[i].nodeId)) pendingRevisions.splice(i, 1);

  const mockDates = [...inp.recentMockDates, ...inp.preservedBlocks
    .filter((b) => b.activityType === StudyActivityType.MOCK_TEST && !CLOSED_PLAN_STATUSES.has(b.status))
    .map((b) => b.scheduledDate)];

  const chapterTitles = new Map(packChapters(inp.pack).map((c) => [c.id, c.title]));
  const backlogTitle = (item) => formatTitle(
    item.kind === BacklogKind.LECTURE ? config.backlogLectureTitle : config.backlogDppTitle,
    chapterTitles.get(item.chapterId) ?? '',
    item.number,
  );
  const keptTitles = new Set(inp.preservedBlocks.filter((b) => !CLOSED_PLAN_STATUSES.has(b.status)).map((b) => b.customTitle).filter((t) => t != null));
  const backlogQueue = inp.coachingBacklog.filter((it) => !keptTitles.has(backlogTitle(it)));

  const blocks = [];
  let subjectCursor = 0;
  let passCursor = 0;
  let mockCount = 0;
  let snapshotAtLearnEnd = null;
  let snapshotAtEmitEnd = null;
  let lastWorkDate = null;

  const learnRemainingNow = () => sumOf(model.chapters, (c) => c.learnRemaining);
  const practiceRemainingNow = () => sumOf(model.chapters, (c) => c.practiceRemaining);
  const workSnapshot = () => model.chapters.flatMap((c) => c.leaves.filter((l) => l.remaining > 0)
    .map((l) => RemainingWork({ nodeId: l.nodeId, subjectId: l.subjectId, estimatedMinutes: l.estimate, remainingMinutes: l.remaining })));

  const pickChapter = () => {
    if (queues.length === 0) return null;
    for (let offset = 0; offset < queues.length; offset++) {
      const queue = queues[(subjectCursor + offset) % queues.length];
      while (queue.length > 0 && queue[0].done) queue.shift();
      if (queue.length > 0) {
        subjectCursor = (subjectCursor + offset + 1) % queues.length;
        return queue[0];
      }
    }
    return null;
  };

  let date = inp.startDate;
  while (date <= simEnd) {
    const today = date;
    if (learnEnd != null && snapshotAtLearnEnd == null && today > learnEnd) {
      snapshotAtLearnEnd = [learnRemainingNow(), practiceRemainingNow()];
    }
    if (snapshotAtEmitEnd == null && today > emitEnd) snapshotAtEmitEnd = workSnapshot();
    date = plusDays(date, 1);
    if (today < nowDate) continue;

    const emit = today <= emitEnd;
    const inRevisionPhase = deadline && today > inp.targetDate;
    const dow = dayOfWeek(today);
    const weekend = dow === DayOfWeek.SATURDAY || dow === DayOfWeek.SUNDAY;
    const dailyTarget = coerceAtLeast(weekend ? inp.weekendTargetMinutes : inp.weekdayTargetMinutes, 0);

    const dayPreserved = inp.preservedBlocks.filter((b) => b.scheduledDate === today);
    const preservedStudy = sumOf(dayPreserved.filter((b) =>
      (b.status === PlanBlockStatus.COMPLETED || (b.status === PlanBlockStatus.PLANNED && !endedUnrun(b, clock))) &&
      blockCountsAsStudy(b)), (b) => b.plannedMinutes);
    let budget = coerceAtLeast(dailyTarget - preservedStudy, 0);
    const preservedRevision = sumOf(dayPreserved.filter((b) =>
      b.activityType === StudyActivityType.REVISION &&
      (b.status === PlanBlockStatus.PLANNED || b.status === PlanBlockStatus.COMPLETED)), (b) => b.plannedMinutes);

    const occupied = dayPreserved.filter((b) => !CLOSED_PLAN_STATUSES.has(b.status))
      .map((b) => [b.startMinuteOfDay, b.startMinuteOfDay + b.plannedMinutes]);
    let windows = subtractOccupied(effectiveWindowsFor(today, inp.weeklyAvailability, inp.availabilityOverrides), occupied);
    if (today === nowDate) {
      windows = windows.map(([start, end]) => { const s = Math.max(start, nowMinute); return end > s ? [s, end] : null; }).filter((w) => w != null);
    }
    const widestWindow = maxOfOrNull(windows, (w) => w[1] - w[0]) ?? 0;
    const placer = new DayPlacer(windows, inp.breakMinutes);
    let blockIndex = 0;

    const add = (type, nodeId, subjectId, start, minutes, source, title = null) => {
      if (!emit) return;
      const prefix = source === PlanBlockSource.REVISION_ENGINE ? ENGINE_REVISION_ID_PREFIX : 'auto_';
      blocks.push(StudyPlanBlock({
        id: deterministicBlockId(prefix, inp.attemptId, today, start, blockIndex, nodeId, type),
        attemptId: inp.attemptId,
        nodeId, subjectId, customTitle: title, activityType: type,
        scheduledDate: today, startMinuteOfDay: start, plannedMinutes: minutes,
        status: PlanBlockStatus.PLANNED, source, rescheduledFromId: null, replacedById: null,
        createdAtEpochMs: nowMs, updatedAtEpochMs: nowMs,
      }));
    };

    // 1. A mock test.
    if (mockDue(inp, today, inRevisionPhase, mockDates) && placer.hasRoomFor(config.mockMinutes)) {
      const p = placer.place(config.mockMinutes, config.mockMinutes);
      if (p) {
        add(StudyActivityType.MOCK_TEST, null, null, p[0], p[1], PlanBlockSource.AUTO);
        blockIndex++;
        budget = coerceAtLeast(budget - p[1], 0);
        mockDates.push(today);
        mockCount++;
      }
    }

    // 2. Due revisions from the revision engine, within the day's cap.
    const cap = inRevisionPhase ? budget
      : coerceAtMost(Math.trunc(dailyTarget * config.revisionDayCapFraction), INT_MAX) - preservedRevision;
    let revisionBudget = Math.min(coerceAtLeast(cap, 0), budget);
    while (revisionBudget >= config.revisionMinBlockMinutes && pendingRevisions.length > 0) {
      const due = pendingRevisions[0];
      if (due.nextDueDate > today) break;
      const want = Math.min(config.revisionBlockMinutes, revisionBudget);
      const placed = placer.place(want, config.revisionMinBlockMinutes);
      if (!placed) break;
      pendingRevisions.shift();
      add(StudyActivityType.REVISION, due.nodeId, model.nodeSubject.get(due.nodeId) ?? null, placed[0], placed[1], PlanBlockSource.REVISION_ENGINE);
      blockIndex++;
      budget -= placed[1];
      revisionBudget -= placed[1];
    }

    // 2b. Coaching backlog.
    if (!inRevisionPhase && backlogQueue.length > 0) {
      let backlogBudget = Math.min(budget, backlogDailyMinutes(dailyTarget, config.liveBacklogSplit));
      let placedToday = 0;
      while (backlogQueue.length > 0 && budget > 0) {
        const item = backlogQueue[0];
        const want = item.kind === BacklogKind.LECTURE ? config.backlogLectureMinutes : config.backlogDppMinutes;
        if (want <= 0) { backlogQueue.shift(); continue; }
        const withinShare = want <= backlogBudget;
        if (!withinShare && (placedToday > 0 || backlogBudget <= 0 || want > budget)) break;
        const placed = placer.place(want, want);
        if (!placed) break;
        backlogQueue.shift();
        add(item.kind === BacklogKind.LECTURE ? StudyActivityType.LEARN : StudyActivityType.PRACTICE,
          item.chapterId, item.subjectId, placed[0], placed[1], PlanBlockSource.AUTO, backlogTitle(item));
        blockIndex++;
        placedToday++;
        budget -= placed[1];
        backlogBudget -= placed[1];
      }
    }

    // 3. Learn then Practice, or revision passes in the revision phase.
    const learnAllowed = !inRevisionPhase || inp.allowLearnInRevisionPhase;
    if (learnAllowed) {
      let failures = 0;
      while (budget > 0 && failures < queues.length) {
        const chapter = pickChapter();
        if (!chapter) break;
        const leaf = chapter.nextLeaf();
        let type, want, minAcceptable;
        if (leaf != null) {
          type = StudyActivityType.LEARN;
          want = Math.min(coerceAtLeast(inp.preferredSessionMinutes, 1), leaf.remaining, budget);
          minAcceptable = Math.min(want, config.minBlockMinutes);
        } else {
          type = StudyActivityType.PRACTICE;
          const full = Math.min(chapter.practiceRemaining, config.practiceMaxMinutes);
          want = Math.min(full, budget);
          minAcceptable = widestWindow >= full ? full : Math.min(full, config.practiceMinMinutes);
        }
        const placed = want >= minAcceptable ? placer.place(want, minAcceptable) : null;
        if (placed == null) { failures++; continue; }
        failures = 0;
        add(type, leaf?.nodeId ?? chapter.id, chapter.subjectId, placed[0], placed[1], PlanBlockSource.AUTO);
        blockIndex++;
        if (leaf != null) leaf.remaining -= placed[1]; else chapter.practiceRemaining -= placed[1];
        lastWorkDate = today;
        budget -= placed[1];
      }
    }
    let passPool;
    if (inRevisionPhase) passPool = passChapters;
    else if (inp.revisionShaped) passPool = passChapters.filter((ref) => !model.chapters.some((c) => c.id === ref.id && c.learnRemaining > 0));
    else passPool = [];
    if (passPool.length > 0) {
      while (budget > 0) {
        const chapter = passPool[passCursor % passPool.length];
        const want = Math.min(config.revisionPassMinutes, budget);
        const placed = placer.place(want, Math.min(want, config.minBlockMinutes));
        if (!placed) break;
        add(StudyActivityType.REVISION, chapter.id, chapter.subjectId, placed[0], placed[1], PlanBlockSource.AUTO);
        blockIndex++;
        passCursor++;
        budget -= placed[1];
      }
    }
  }
  if (snapshotAtLearnEnd == null) snapshotAtLearnEnd = [learnRemainingNow(), practiceRemainingNow()];
  const remainingAtEmit = snapshotAtEmitEnd ?? workSnapshot();

  const fit = deadline
    ? PlanFit({
      finishBy: inp.targetDate,
      learnMinutes: learnNeeded,
      practiceMinutes: practiceNeeded,
      shortByMinutes: snapshotAtLearnEnd[0] + snapshotAtLearnEnd[1],
      revisionWindowDays: inp.examDate != null ? coerceAtLeast(daysBetween(inp.targetDate, inp.examDate), 0) : 0,
      calibrated: inp.calibrationActive,
    })
    : null;
  return RollingPlanResult({
    blocks,
    remainingWork: remainingAtEmit,
    unscheduledMinutes: sumOf(remainingAtEmit, (w) => w.remainingMinutes),
    fit,
    mockTestsPlanned: mockCount,
    projectedFinish: simulateToEnd && learnRemainingNow() + practiceRemainingNow() === 0 ? lastWorkDate ?? inp.startDate : null,
  });
}

const isTargetNode = (inp, nodeId, model) =>
  inp.includedLeafIds.has(nodeId) || (model.nodeLeaves.get(nodeId)?.some((l) => inp.includedLeafIds.has(l)) ?? false);

const bandOf = (inp, chapterId) => inp.priorities.get(chapterId)?.band ?? PriorityBand.NO_DATA;

/** Chapter queues, one per subject, in the planning order. */
function orderedQueues(inp, model) {
  return [...groupBy(model.chapters, (c) => c.subjectId).values()].map((chs) => {
    switch (inp.planningOrder) {
      case PlanningOrder.SHORTEST_FIRST:
        return sortedWith(chs, compareBy((c) => c.learnRemaining + c.practiceRemaining, (c) => c.syllabusIndex));
      case PlanningOrder.HIGH_YIELD_FIRST:
        return sortedWith(chs, compareBy(
          (c) => yieldRank(bandOf(inp, c.id)),
          (c) => -(inp.priorities.get(c.id)?.meanQuestionsPerYear ?? 0.0),
          (c) => c.syllabusIndex,
        ));
      default:
        return sortedBy(chs, (c) => c.syllabusIndex);
    }
  });
}

/** Revision-phase pass order: high-yield first, then weak chapters, then syllabus order. */
function revisionPassOrder(inp, model) {
  const weakByChapter = new Map();
  for (const [nodeId, n] of inp.weakNodeCounts) {
    const ch = model.nodeChapter.get(nodeId);
    if (ch != null) weakByChapter.set(ch, (weakByChapter.get(ch) ?? 0) + n);
  }
  let index = 0;
  const refs = [];
  for (const subject of inp.pack.subjects) {
    for (const chapter of subject.nodes) {
      const order = index++;
      if (nodeLeafIds(chapter).some((l) => inp.includedLeafIds.has(l))) refs.push({ id: chapter.id, subjectId: subject.id, order });
    }
  }
  return sortedWith(refs, compareBy(
    (r) => yieldRank(bandOf(inp, r.id)),
    (r) => -(weakByChapter.get(r.id) ?? 0),
    (r) => -(inp.priorities.get(r.id)?.meanQuestionsPerYear ?? 0.0),
    (r) => r.order,
  ));
}

/** Mock density (§4.3). */
function mockDue(inp, date, inRevisionPhase, mockDates) {
  const exam = inp.examDate;
  if (exam == null) return false;
  const daysToExam = daysBetween(date, exam);
  if (daysToExam <= 0) return false;
  const eligible = inp.mode === PlanMode.DEADLINE ? inRevisionPhase : daysToExam <= MOCK_FAR_DAYS;
  if (!eligible || mockDates.some((d) => d === date)) return false;
  let windowDays, maxInWindow;
  if (daysToExam > MOCK_FAR_DAYS) { windowDays = 14; maxInWindow = 1; }
  else if (daysToExam >= MOCK_NEAR_DAYS) { windowDays = 7; maxInWindow = 1; }
  else { windowDays = 7; maxInWindow = 3; }
  const windowStart = minusDays(date, windowDays - 1);
  if (mockDates.filter((d) => d >= windowStart && d < date).length >= maxInWindow) return false;
  if (maxInWindow > 1 && mockDates.some((d) => d >= minusDays(date, 1) && d < date)) return false;
  return true;
}

/**
 * windows: [start, end] pairs; occupied: [start, endExclusive] pairs (Kotlin passes
 * `start until end` IntRanges, which map to exactly these).
 */
export function subtractOccupied(windows, occupied) {
  const merged = [];
  for (const iv of sortedBy(occupied.filter((o) => o[1] > o[0]), (o) => o[0])) {
    const last = merged[merged.length - 1];
    if (last == null || iv[0] > last[1]) merged.push([iv[0], iv[1]]);
    else merged[merged.length - 1] = [last[0], Math.max(last[1], iv[1])];
  }
  return sortedBy(windows, (w) => w[0]).flatMap(([start, end]) => {
    if (end <= start) return [];
    const result = [];
    let cursor = start;
    for (const [busyStart, busyEnd] of merged) {
      if (busyEnd <= cursor || busyStart >= end) continue;
      if (busyStart > cursor) result.push([cursor, Math.min(busyStart, end)]);
      cursor = Math.max(cursor, busyEnd);
    }
    if (cursor < end) result.push([cursor, end]);
    return result;
  });
}

/** java.lang.String.hashCode() */
export function javaStringHashCode(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

function deterministicBlockId(prefix, attemptId, date, startMinute, blockIndex, nodeId, type) {
  let key;
  if (nodeId == null) key = type.name;
  else if (type === StudyActivityType.LEARN) key = nodeId;
  else key = `${nodeId}:${type.name}`;
  return `${prefix}${attemptId}_${date}_${startMinute}_${blockIndex}_${(javaStringHashCode(key) >>> 0).toString(16)}`;
}

/** StudyPlanBlock.isRegenerable(): a future, still-planned block the planner created itself. */
export const isRegenerable = (b) =>
  b.status === PlanBlockStatus.PLANNED &&
  b.rescheduledFromId == null &&
  (b.source === PlanBlockSource.AUTO || (b.source === PlanBlockSource.REVISION_ENGINE && b.id.startsWith(ENGINE_REVISION_ID_PREFIX)));

/** StudyPlanBlock.isReplacedByReplanFrom(fromDate, fromMinute) */
export const isReplacedByReplanFrom = (b, fromDate, fromMinute) =>
  isRegenerable(b) && (b.scheduledDate > fromDate || (b.scheduledDate === fromDate && b.startMinuteOfDay >= fromMinute));
