// Port of core/data/repo/RollingPlanRepository.kt. suspend -> async.
// Funs with default parameters accept positional args or ONE named-args object (see namedArgs):
//   replenish({ fromDate, horizonDays, nowMs }), planFit({ nowMs, withOptions }), missedPrompt({ today }) ...
// DataStore "plan_prompts" -> prefs('plan_prompts'), keys missed_resolved_through, missed_dismissed_on,
// fresh_start_on (epoch days) and live_backlog_split (int).
// R.string.plan_backlog_*_title are inlined from core/data/src/main/res/values/strings.xml.
import { prefs } from '../../platform/store.js';
import { dataClass, coerceIn, idiv } from '../../domain/kt.js';
import {
  today as localToday, nowMs as clockNowMs, plusDays, minusDays, toEpochDay, ofEpochDay, epochMsToLocalDate, minuteOfDayAt, maxDate,
} from '../../domain/time.js';
import { Calibration, personalCalibration } from '../../domain/Calibration.js';
import { DEFAULT_LIVE_BACKLOG_SPLIT, coachingBacklogItems } from '../../domain/CoachingBacklog.js';
import {
  PlannerConfig, PlanInputs, PlanningOrder, DEFAULT_PLANNING_HORIZON_DAYS, plan, projectedFinish, isReplacedByReplanFrom,
} from '../../domain/RollingPlan.js';
import { planFit } from '../../domain/PlanFit.js';
import { ExamAttempt, ExamAttemptStatus } from '../../domain/ExamAttempt.js';
import { NEET_EXAM_ID, chapters, leafIds } from '../../domain/ExamPack.js';
import { PlanMode } from '../../domain/PlanMode.js';
import { PlanBlockStatus, StudyActivityType } from '../../domain/StudyPlan.js';
import { TargetNodeState, effectiveTargetLeafIds } from '../../domain/TargetSyllabus.js';
import { TopicProgressStatus } from '../../domain/TopicProgress.js';
import { missedDayCount, missedPlannedBlocks, skipMissedBlocks } from '../../domain/MissedDays.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, mapOf, namedArgs } from '../common.js';
import { PrepTrackerDatabase } from '../db/PrepTrackerDatabase.js';
import { toEntity as blockToEntity } from '../db/StudyPlanBlockEntity.js';

/** Kotlin: internal Context.planPromptDataStore (BackupRepository exports it). */
export const planPromptDataStore = () => prefs('plan_prompts');
const missedResolvedThroughKey = 'missed_resolved_through';
const missedDismissedOnKey = 'missed_dismissed_on';
const freshStartOnKey = 'fresh_start_on';
const liveBacklogSplitKey = 'live_backlog_split';
/** DataStore Long keys in plan_prompts (for backup typing). */
export const PLAN_PROMPT_LONG_KEYS = [missedResolvedThroughKey, missedDismissedOnKey, freshStartOnKey];

const PLAN_BACKLOG_LECTURE_TITLE = 'Backlog: %1$s lecture %2$d';
const PLAN_BACKLOG_DPP_TITLE = 'Backlog: %1$s DPP %2$d';

const MOCK_LOOKBACK_DAYS = 14;
const MISSED_LOOKBACK_DAYS = 14;
const DRAFT_ATTEMPT_ID = 'draft';

/**
 * "You missed N blocks. Rebalance the next days?" Blocks are counted from [since] up to yesterday.
 * [days] is the recent run of days with missed blocks (missedDayCount).
 */
export const MissedPrompt = dataClass({ count: undefined, since: undefined, days: 1 });

/** What the student is choosing in onboarding, before it is saved. */
export const PlanDraft = dataClass({
  mode: undefined,
  examDate: undefined,
  finishBy: undefined,
  weekdayTargetMinutes: undefined,
  weekendTargetMinutes: undefined,
  weeklyAvailability: undefined,
  /** Chapters (top-level syllabus nodes) the student is preparing; every other chapter is parked. Set. */
  includedChapterIds: undefined,
  /** Chapters the student marked as finished (Learned) in this setup. Set. */
  coveredChapterIds: new Set(),
  startingPoint: null,
});

/**
 * Owns the rolling-plan transaction. Every consumer that needs a plan, a fit or a calibration goes
 * through here, so they are all computed from the same inputs by the same domain functions.
 */
export class RollingPlanRepository {
  constructor(context, dispatchers, examPackRepository, analytics = Analytics.NoOp) {
    this.examPackRepository = examPackRepository;
    this.analytics = analytics;
    this.db = PrepTrackerDatabase.get(context);
    /** The day the horizon was last topped up in this process (page load). */
    this.lastTopUpDate = null;
  }

  activeAttemptOnce() { return this.db.examAttemptDao().activeOnce()?.toDomain() ?? null; }

  /**
   * Regenerates future planner-made blocks from [fromDate] (never before now today) out to
   * [horizonDays]. Completed, started, manual, moved and past blocks stay. Safe on every app open.
   */
  async replenish(...args) {
    const [fromDate, horizonDays, nowMs] = namedArgs(args, ['fromDate', 'horizonDays', 'nowMs'],
      [localToday, () => DEFAULT_PLANNING_HORIZON_DAYS, clockNowMs]);
    const attempt = this.activeAttemptOnce();
    if (!attempt) return null;
    const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
    const priorities = (await this.examPackRepository.pyqPriorities()).chapters;
    return this.db.withTransaction(async () => {
      const inputs = await this.loadInputs(attempt, pack, priorities, fromDate, horizonDays, nowMs);
      const result = plan(inputs, horizonDays, nowMs);
      this.db.studyPlanBlockDao().deleteReplaceable(attempt.id, toEpochDay(fromDate), fromMinuteFor(fromDate, nowMs));
      this.db.studyPlanBlockDao().upsertAll(result.blocks.map(blockToEntity));
      if (fromDate === localToday()) this.lastTopUpDate = fromDate;
      return result;
    });
  }

  /** Percent of each day for live coaching work (§5.4); the rest goes to backlog. */
  async liveBacklogSplit() {
    const v = planPromptDataStore().get(liveBacklogSplitKey, null);
    return v != null ? coerceIn(v, 0, 100) : DEFAULT_LIVE_BACKLOG_SPLIT;
  }

  /** Saves the live/backlog split and replans from today so the change shows at once. */
  async setLiveBacklogSplit(percent) {
    planPromptDataStore().set(liveBacklogSplitKey, coerceIn(percent, 0, 100));
    await this.replenish();
  }

  /** The daily top-up back to the planning horizon, once per calendar day per process. */
  async topUpIfNewDay(today = localToday()) {
    if (this.lastTopUpDate === today) return;
    await this.replenish({ fromDate: today });
  }

  /** Calculates a real draft without mutating storage, used by Organise before confirmation. */
  async preview(pack, overridesByNodeId, planningOrder, fromDate = localToday(), horizonDays = 7, nowMs = clockNowMs()) {
    const attempt = this.activeAttemptOnce();
    if (!attempt) return null;
    const priorities = (await this.examPackRepository.pyqPriorities()).chapters;
    const inputs = await this.loadInputs(attempt, pack, priorities, fromDate, horizonDays, nowMs, overridesByNodeId, planningOrder);
    return plan(inputs, horizonDays, nowMs);
  }

  /**
   * Whether the remaining work fits before finish-by, with the ways to fix it when it does not.
   * Null in steady mode and with no plan. The same planner as replenish.
   */
  async planFit(...args) {
    const [nowMs, withOptions] = namedArgs(args, ['nowMs', 'withOptions'], [clockNowMs, () => true]);
    const attempt = this.activeAttemptOnce();
    if (!attempt) return null;
    if (attempt.planMode !== PlanMode.DEADLINE) return null;
    const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
    const priorities = (await this.examPackRepository.pyqPriorities()).chapters;
    const inputs = await this.loadInputs(attempt, pack, priorities, localToday(), 1, nowMs);
    return planFit(inputs, nowMs, withOptions);
  }

  /** The same fit, for a plan the student is still shaping in onboarding. Null in steady mode. */
  async draftFit(draft, nowMs = clockNowMs()) {
    if (draft.mode !== PlanMode.DEADLINE) return null;
    const inputs = await this.draftInputs(draft, nowMs);
    return planFit(inputs, nowMs);
  }

  /** Steady mode's projected finish for a draft, or null if the work does not end before the exam. */
  async draftProjection(draft, nowMs = clockNowMs()) {
    const inputs = await this.draftInputs(draft, nowMs);
    return projectedFinish(inputs, nowMs);
  }

  /** The projected finish for the saved plan (steady mode), from the same planner as replenish. */
  async projectedFinish(nowMs = clockNowMs()) {
    const attempt = this.activeAttemptOnce();
    if (!attempt) return null;
    const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
    const priorities = (await this.examPackRepository.pyqPriorities()).chapters;
    const inputs = await this.loadInputs(attempt, pack, priorities, localToday(), 1, nowMs);
    return projectedFinish(inputs, nowMs);
  }

  /**
   * Weekly and monthly targets for a draft plan: chapters worked on in the next 7 days, and
   * chapters whose learning finishes within 30 days. { week: [{ title, subject }], month: [{ subject, count }] }.
   */
  async draftTargets(draft, nowMs = clockNowMs()) {
    const inputs = await this.draftInputs(draft, nowMs);
    const chapters = new Map();
    const chapterOf = new Map();
    inputs.pack.subjects.forEach((subject) => subject.nodes.forEach((chapter) => {
      chapters.set(chapter.id, { id: chapter.id, title: chapter.title, subject: subject.shortName ?? subject.name, order: subject.order });
      const walk = (node) => { chapterOf.set(node.id, chapter.id); (node.children ?? []).forEach(walk); };
      walk(chapter);
    }));
    const touchedWithin = (days) => {
      const result = inputs.plan(days, nowMs);
      const touched = new Set(result.blocks.map((b) => chapterOf.get(b.nodeId)).filter(Boolean));
      const open = new Set(result.remainingWork.filter((w) => w.remainingMinutes > 0).map((w) => chapterOf.get(w.nodeId)));
      return { touched: [...touched].map((id) => chapters.get(id)), finished: [...touched].filter((id) => !open.has(id)).map((id) => chapters.get(id)) };
    };
    const finishedWithin = (days) => touchedWithin(days).finished;
    // The week names every chapter it works on; the planner runs subjects side by side, so few finish in 7 days.
    const week = touchedWithin(7).touched;
    const bySubject = new Map();
    finishedWithin(30).forEach((c) => {
      const row = bySubject.get(c.subject) ?? { subject: c.subject, order: c.order, count: 0 };
      row.count += 1;
      bySubject.set(c.subject, row);
    });
    return {
      week: week.sort((x, y) => x.order - y.order).map(({ title, subject }) => ({ title, subject })),
      month: [...bySubject.values()].sort((x, y) => x.order - y.order).map(({ subject, count }) => ({ subject, count })),
    };
  }

  /** private */
  async draftInputs(draft, nowMs) {
    const active = this.activeAttemptOnce();
    const existing = active && active.examId === NEET_EXAM_ID ? active : null;
    const pack = await this.examPackRepository.examPackFor(NEET_EXAM_ID);
    const priorities = (await this.examPackRepository.pyqPriorities()).chapters;
    const attempt = (existing ?? ExamAttempt({
      id: DRAFT_ATTEMPT_ID, examId: NEET_EXAM_ID, syllabusVersion: pack.syllabusVersion,
      examDate: draft.examDate, targetCompletionDate: draft.finishBy,
      createdAtEpochMs: nowMs, updatedAtEpochMs: nowMs, status: ExamAttemptStatus.ACTIVE,
    })).copy({ examDate: draft.examDate, targetCompletionDate: draft.finishBy, planMode: draft.mode });
    const parked = pack.subjects.flatMap((it) => it.nodes).map((it) => it.id).filter((id) => !draft.includedChapterIds.has(id));
    const loaded = await this.loadInputs(
      attempt, pack, priorities, localToday(), 1, nowMs,
      new Map(parked.map((id) => [id, TargetNodeState.EXCLUDED])),
    );
    return loaded.copy({
      weekdayTargetMinutes: draft.weekdayTargetMinutes,
      weekendTargetMinutes: draft.weekendTargetMinutes,
      weeklyAvailability: draft.weeklyAvailability,
      availabilityOverrides: [],
      coveredLeafIds: draftCoveredLeaves(pack, loaded.coveredLeafIds, draft.coveredChapterIds),
      revisionShaped: draft.startingPoint?.revisionShaped ?? loaded.revisionShaped,
    });
  }

  /** The student's pace against the estimates, from completed Learn sessions. Calibration.NONE with no plan. */
  async calibration() {
    const attempt = this.activeAttemptOnce();
    if (!attempt) return Calibration.NONE;
    const pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
    return this.calibrationFor(attempt, pack);
  }

  /** Planner-made blocks from earlier days that were never done, since [since] (inclusive). */
  async missedBlocks(since, today = localToday()) {
    return (await this.missedWithPlannedDates(since, today))[0];
  }

  /** private: the missed blocks, plus every earlier date that had planner blocks (for missedDayCount). */
  async missedWithPlannedDates(since, today) {
    const attempt = this.activeAttemptOnce();
    if (!attempt) return [[], new Set()];
    const blocks = this.db.studyPlanBlockDao().between(attempt.id, toEpochDay(since), toEpochDay(minusDays(today, 1)))
      .map((it) => it.toDomain());
    const sessions = this.db.studySessionDao().between(attempt.id, toEpochDay(since), toEpochDay(today)).map((it) => it.toDomain());
    const planned = new Set(blocks.filter((it) => it.status !== PlanBlockStatus.RESCHEDULED).map((it) => it.scheduledDate));
    return [missedPlannedBlocks(blocks, sessions, today, since), planned];
  }

  /**
   * The "whole day missed" prompt: set when at least one planner-made block from an earlier day was
   * never done, since the last time the student answered, and not dismissed for today.
   */
  async missedPrompt(...args) {
    const [today] = namedArgs(args, ['today'], [localToday]);
    const p = planPromptDataStore().all();
    if (p[missedDismissedOnKey] === toEpochDay(today)) return null;
    const resolved = p[missedResolvedThroughKey] != null ? ofEpochDay(p[missedResolvedThroughKey]) : null;
    const lookback = minusDays(today, MISSED_LOOKBACK_DAYS);
    const since = maxDate(resolved ?? lookback, lookback);
    const [missed, planned] = await this.missedWithPlannedDates(since, today);
    return missed.length > 0 ? MissedPrompt({ count: missed.length, since, days: missedDayCount(missed, planned) }) : null;
  }

  /** "Not now": hide the prompt for the rest of [today]. */
  async dismissMissedPrompt(...args) {
    const [today] = namedArgs(args, ['today'], [localToday]);
    planPromptDataStore().set(missedDismissedOnKey, toEpochDay(today));
  }

  /** "Rebalance the next days": the missed blocks become SKIPPED and the plan is regenerated from today. */
  async rebalanceMissed(...args) {
    const [since, today, nowMs] = namedArgs(args, ['since', 'today', 'nowMs'], [null, localToday, clockNowMs]);
    const missed = await this.missedBlocks(since, today);
    if (missed.length > 0) this.analytics.log(AnalyticsEvent.BLOCK_MISSED, mapOf([AnalyticsParam.COUNT, missed.length]));
    const skipped = skipMissedBlocks(missed, nowMs);
    if (skipped.length > 0) this.db.studyPlanBlockDao().markSkipped(skipped.map((it) => it.id), nowMs);
    planPromptDataStore().set(missedResolvedThroughKey, toEpochDay(today));
    return this.replenish({ fromDate: today, nowMs });
  }

  /** "Start fresh from today" (§5.5): the same move as rebalanceMissed, and today is remembered. */
  async startFresh(...args) {
    const [since, today, nowMs] = namedArgs(args, ['since', 'today', 'nowMs'], [null, localToday, clockNowMs]);
    const result = await this.rebalanceMissed(since, today, nowMs);
    planPromptDataStore().set(freshStartOnKey, toEpochDay(today));
    return result;
  }

  /** Did the student start fresh on [today]? */
  async startedFreshOn(today = localToday()) {
    return planPromptDataStore().get(freshStartOnKey, null) === toEpochDay(today);
  }

  /** private */
  calibrationFor(attempt, pack) {
    const covered = this.coveredLeafIds(attempt);
    const focused = new Map(this.db.studySessionDao().completedLearnSumsByNode(attempt.id).map((it) => [it.nodeId, idiv(it.totalSeconds, 60)]));
    return personalCalibration(pack, focused, covered);
  }

  /** private */
  coveredLeafIds(attempt) {
    return new Set(this.db.topicProgressDao().allOnce(attempt.id)
      .filter((it) => TopicProgressStatus.valueOf(it.status) === TopicProgressStatus.COVERED)
      .map((it) => it.nodeId));
  }

  /** private: everything the planner reads, loaded once. Replan, preview and fit all start here. */
  async loadInputs(attempt, pack, priorities, fromDate, horizonDays, nowMs, overridesByNodeId = null, planningOrderOverride = null) {
    const db = this.db;
    const preferences = db.studyPreferenceDao().forAttempt(attempt.id)?.toDomain() ?? null;
    const overrides = overridesByNodeId ?? new Map(db.targetNodeOverrideDao().allOnce(attempt.id)
      .map((it) => [it.nodeId, TargetNodeState.valueOf(it.state)]));
    const covered = this.coveredLeafIds(attempt);
    const focusedByNode = new Map(db.studySessionDao().completedLearnSumsByNode(attempt.id).map((it) => [it.nodeId, idiv(it.totalSeconds, 60)]));
    const calibration = personalCalibration(pack, focusedByNode, covered);
    const deadline = attempt.planMode === PlanMode.DEADLINE;
    const rangeEnd = deadline
      ? maxDate(attempt.targetCompletionDate, attempt.examDate ?? attempt.targetCompletionDate)
      : plusDays(fromDate, horizonDays + 60);
    const fromMinute = fromMinuteFor(fromDate, nowMs);
    const inRange = db.studyPlanBlockDao()
      .between(attempt.id, toEpochDay(minusDays(fromDate, MOCK_LOOKBACK_DAYS)), toEpochDay(rangeEnd))
      .map((it) => it.toDomain());
    const preserved = inRange
      .filter((it) => it.scheduledDate >= fromDate)
      .filter((it) => !isReplacedByReplanFrom(it, fromDate, fromMinute));
    const recentMocks = inRange
      .filter((it) => it.scheduledDate < fromDate &&
        it.activityType === StudyActivityType.MOCK_TEST &&
        it.status === PlanBlockStatus.COMPLETED)
      .map((it) => it.scheduledDate);
    const practicedNodes = new Set(db.studySessionDao().completedNodeIdsOfType(attempt.id, StudyActivityType.PRACTICE.name));
    const practicedChapters = new Set(pack.subjects.flatMap((it) => it.nodes)
      .filter((chapter) => practicedNodes.size > 0 && flattenIds(chapter).some((id) => practicedNodes.has(id)))
      .map((it) => it.id));
    return PlanInputs({
      attemptId: attempt.id,
      pack,
      startDate: fromDate,
      targetDate: deadline ? attempt.targetCompletionDate : plusDays(fromDate, horizonDays),
      weeklyAvailability: db.availabilityDao().weeklyFor(attempt.id).map((it) => it.toDomain()),
      availabilityOverrides: db.availabilityDao().overridesFor(attempt.id).map((it) => it.toDomain()),
      includedLeafIds: effectiveTargetLeafIds(pack, overrides),
      coveredLeafIds: covered,
      focusedLearnMinutesByNode: focusedByNode,
      preservedBlocks: preserved,
      weekdayTargetMinutes: preferences?.weekdayTargetMinutes ?? 4 * 60,
      weekendTargetMinutes: preferences?.weekendTargetMinutes ?? 7 * 60,
      preferredSessionMinutes: preferences?.preferredSessionMinutes ?? 50,
      breakMinutes: preferences?.shortBreakMinutes ?? 10,
      planningOrder: planningOrderOverride ?? preferences?.planningOrder ?? PlanningOrder.DEFAULT,
      mode: attempt.planMode,
      examDate: attempt.examDate,
      allowLearnInRevisionPhase: preferences?.allowLearnInRevisionPhase ?? false,
      calibrationFactor: calibration.factor,
      calibrationActive: calibration.active,
      priorities,
      revisionShaped: attempt.startingPoint?.revisionShaped ?? false,
      revisionStates: db.revisionStateDao().allEnabled(attempt.id).map((it) => it.toDomain()),
      weakNodeCounts: new Map(db.studySessionDao().struggledCountsByNode(attempt.id).map((it) => [it.nodeId, it.total])),
      practicedChapterIds: practicedChapters,
      recentMockDates: recentMocks,
      coachingBacklog: this.coachingBacklogFor(attempt, pack),
      config: PlannerConfig({
        liveBacklogSplit: await this.liveBacklogSplit(),
        backlogLectureTitle: PLAN_BACKLOG_LECTURE_TITLE,
        backlogDppTitle: PLAN_BACKLOG_DPP_TITLE,
      }),
    });
  }

  /** private: coaching lectures and DPPs behind the batch, in syllabus order. */
  coachingBacklogFor(attempt, pack) {
    const coaching = this.db.coachingDao().allOnce(attempt.id).map((it) => it.toDomain());
    if (coaching.length === 0) return [];
    const position = this.db.coachingDao().positionFor(attempt.id)?.toDomain() ?? null;
    const subjectOf = new Map(pack.subjects.flatMap((subject) => subject.nodes.map((it) => [it.id, subject.id])));
    return coachingBacklogItems(coaching, position, chapters(pack).map((it) => it.id), subjectOf);
  }
}

/** The minute of day a replan from [fromDate] starts at: now when that is today, else the start of the day. */
function fromMinuteFor(fromDate, nowMs) {
  return epochMsToLocalDate(nowMs) === fromDate ? minuteOfDayAt(nowMs) : 0;
}

/** SyllabusNode.flattenIds() */
function flattenIds(node) { return [node.id, ...node.children.flatMap(flattenIds)]; }

/**
 * Finished work as the student has it on screen: chapters ticked in the draft count, chapters that
 * were finished when the screen opened but are unticked now do not, and partial progress stays.
 */
function draftCoveredLeaves(pack, saved, finished) {
  const chs = pack.subjects.flatMap((it) => it.nodes);
  const wasFinished = new Set(chs.filter((it) => {
    const leaves = leafIds(it);
    return leaves.length > 0 && leaves.every((l) => saved.has(l));
  }).map((it) => it.id));
  const unticked = new Set(chs.filter((it) => wasFinished.has(it.id) && !finished.has(it.id)).flatMap((it) => leafIds(it)));
  const ticked = chs.filter((it) => finished.has(it.id)).flatMap((it) => leafIds(it));
  const out = new Set([...saved].filter((x) => !unticked.has(x)));
  for (const t of ticked) out.add(t);
  return out;
}
