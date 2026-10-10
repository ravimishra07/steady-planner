// Port of feature/home HomeViewModel.kt.
// viewModelScope.launch → async fire-and-forget; the 60s clock ticker is a setInterval cleared in onCleared().
// focusRequests: Channel(BUFFERED).receiveAsFlow() → `focusRequests.collect(fn)` (see _local.js Channel).
import { MutableStateFlow } from '../../platform/flow.js';
import { randomUUID } from '../../data/common.js';
import {
  DAY_TIMELINE_START, DAY_TIMELINE_END, FixedCommitmentBlock, StudyPlacementIssue,
  buildDayTimeline, findNextFreeSlot, planMissedDayRecovery, validateStudyPlacement,
} from '../../domain/DayTimeline.js';
import { StudySessionRecord, currentMinuteOfDay, weekStatusForDayMinutes } from '../../domain/StudySession.js';
import { StudyPlanBlock, StudyActivityType, PlanBlockSource, PlanBlockStatus } from '../../domain/StudyPlan.js';
import { StudySessionStatus } from '../../domain/StudyHistory.js';
import { SyllabusSection, SyllabusTopicNode } from '../../domain/Syllabus.js';
import { effectiveWindowsFor } from '../../domain/Availability.js';
import {
  averageDailyMinutes, coachingBacklogMetrics, studiedMinutesByDate, studyDates, streakBroken, syllabusMetrics, targetStates,
} from '../../domain/Metrics.js';
import { findNode, findSubjectOf, chapterOf } from '../../domain/ExamPack.js';
import { daysToExam } from '../../domain/ExamDates.js';
import { nextRevisionDueIfCompleted } from '../../domain/RevisionSchedule.js';
import { shouldStartFresh } from '../../domain/MissedDays.js';
import { todayBudgetMinutes, weekAround } from '../../domain/TodaySchedule.js';
import { today as localToday, nowMs as clockNowMs, plusDays, plusMonths, withDayOfMonth, lengthOfMonth, dayOfWeek, dayOfMonth } from '../../domain/time.js';
import { minOfOrNull, maxOfOrNull, sortedBy, associateBy, coerceAtLeast, coerceAtMost, sumOf, toInt } from '../../domain/kt.js';
import {
  HomeUiState, HomeSheet, StudyPickerLevel, PendingTopic, ActiveSprintUi, MissedDayRecoveryUi, PlanFitUi,
  MissedBlocksUi, FreshStartUi, CoachingChapterOption, CoachingBacklogRowUi, CoachingTodayUi, WeekDayUi,
} from './HomeUiState.js';
import { Channel } from './_local.js';

const RECOVERY_HORIZON_DAYS = 7;
const DEFAULT_WEEKDAY_MINUTES = 4 * 60;
const DEFAULT_WEEKEND_MINUTES = 7 * 60;

/** runCatching { block() }.getOrNull() */
async function getOrNull(block) {
  try { return await block(); } catch { return null; }
}

export class HomeViewModel {
  constructor(
    examPackRepository, attemptRepository, availabilityRepository, planRepository, studySessionRepository,
    topicProgressRepository, studyPreferenceRepository, revisionRepository, rollingPlanRepository,
    targetSyllabusRepository, chapterStageRepository,
  ) {
    Object.assign(this, {
      examPackRepository, attemptRepository, availabilityRepository, planRepository, studySessionRepository,
      topicProgressRepository, studyPreferenceRepository, revisionRepository, rollingPlanRepository,
      targetSyllabusRepository, chapterStageRepository,
    });
    this._state = new MutableStateFlow(HomeUiState());
    this.state = this._state.asStateFlow();
    /** Emits the session the instant it starts running, so the host can launch full-screen Focus. */
    this._focusRequests = new Channel();
    this.focusRequests = this._focusRequests;

    this.sections = [];
    this.allSessions = [];
    this.pack = null;
    this.attemptId = null;
    this.planBlocks = [];
    this.history = [];
    this.revisionStates = new Map();
    /** Focused minutes per day, from StudySession: the only source for "studied". */
    this.studiedByDate = new Map();
    this.weekdayHours = 4;
    this.weekendHours = 7;
    this.wakeMinute = DAY_TIMELINE_START;
    this.sleepMinute = DAY_TIMELINE_END;
    this.weeklyAvailability = [];
    this.availabilityOverrides = [];

    this.refresh();
    this.startClockTicker();
  }

  _update(fn) { this._state.update(fn); }

  startClockTicker() {
    this._ticker = setInterval(() => this._update((it) => this.rebuild(it)), 60_000);
  }

  onCleared() { clearInterval(this._ticker); }

  refresh() {
    (async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      this.attemptId = attempt?.id ?? null;
      this.pack = attempt ? await this.examPackRepository.examPackFor(attempt.examId, attempt.id) : null;
      this.sections = this.pack ? toPickerSections(this.pack) : [];
      const preferences = attempt ? await this.studyPreferenceRepository.forAttempt(attempt.id) : null;
      this.weekdayHours = preferences?.weekdayTargetMinutes != null ? preferences.weekdayTargetMinutes / 60 : 4;
      this.weekendHours = preferences?.weekendTargetMinutes != null ? preferences.weekendTargetMinutes / 60 : 7;
      this.weeklyAvailability = attempt ? await this.availabilityRepository.weeklyFor(attempt.id) : [];
      this.availabilityOverrides = attempt ? await this.availabilityRepository.overridesFor(attempt.id) : [];
      this.wakeMinute = minOfOrNull(this.weeklyAvailability, (it) => it.startMinuteOfDay) ?? DAY_TIMELINE_START;
      this.sleepMinute = maxOfOrNull(this.weeklyAvailability, (it) => it.endMinuteOfDay) ?? DAY_TIMELINE_END;
      const today = localToday();
      // Daily top-up back to the 21 day horizon, before the day is read.
      if (attempt) await getOrNull(() => this.rollingPlanRepository.topUpIfNewDay(today));
      this.revisionStates = new Map((attempt ? await this.revisionRepository.allFor(attempt.id) : []).map((it) => [it.nodeId, it]));
      if (attempt) await this.reloadCanonical(attempt.id);
      let fit = attempt ? await getOrNull(() => this.rollingPlanRepository.planFit({ withOptions: false })) : null;
      if (fit && fit.fits) fit = null;
      const missed = attempt ? await getOrNull(() => this.rollingPlanRepository.missedPrompt(today)) : null;
      const fresh = missed != null && shouldStartFresh(missed.days, streakBroken(studyDates(this.history), today));
      const startedFresh = (attempt ? await getOrNull(() => this.rollingPlanRepository.startedFreshOn(today)) : null) === true;
      const syllabus = attempt && this.pack
        ? syllabusMetrics(
          this.pack,
          targetStates(await this.targetSyllabusRepository.overridesFor(attempt.id)),
          await this.topicProgressRepository.allOnce(attempt.id),
        )
        : null;
      const coaching = attempt ? await getOrNull(() => this.coachingToday(attempt.id, today)) : null;
      const running = this.allSessions
        .filter((it) => it.date === today)
        .find((it) => it.runningEndsAtMs != null && !it.completed) ?? null;
      this._update((current) => this.rebuild(current.copy({
        loading: false,
        hasPlan: attempt != null,
        sections: this.sections,
        daysUntilExam: (attempt ? daysToExam(attempt, today) : null) ?? 0,
        syllabusPercent: syllabus?.overall?.percent ?? 0,
        planFit: fit ? PlanFitUi({ shortByMinutes: fit.shortByMinutes, finishBy: fit.finishBy, calibrated: fit.calibrated }) : null,
        missedBlocks: missed && !fresh ? MissedBlocksUi({ count: missed.count, since: missed.since }) : null,
        freshStart: missed && fresh ? FreshStartUi({ since: missed.since }) : null,
        startedFreshToday: startedFresh,
        coaching,
        activeSprint: running && running.runningEndsAtMs != null ? ActiveSprintUi({
          sessionId: running.id,
          title: running.title,
          remainingSec: coerceAtLeast(toInt((running.runningEndsAtMs - clockNowMs()) / 1000), 0),
          durationMinutes: running.durationMinutes,
        }) : null,
      })));
    })();
  }

  async reloadCanonical(id) {
    const today = localToday();
    this.planBlocks = await this.planRepository.planBetween(id, plusMonths(today, -24), plusMonths(today, 24));
    this.history = await this.studySessionRepository.between(id, plusMonths(today, -24), plusMonths(today, 24));
    this.studiedByDate = studiedMinutesByDate(this.history);
    const sessionsByBlock = associateBy(this.history.filter((it) => it.planBlockId != null), (it) => it.planBlockId);
    const currentPack = this.pack;
    this.allSessions = this.planBlocks
      .filter((it) => it.status !== PlanBlockStatus.RESCHEDULED && it.status !== PlanBlockStatus.SKIPPED)
      .map((block) => {
        const actual = sessionsByBlock.get(block.id) ?? null;
        const node = block.nodeId != null && currentPack ? findNode(currentPack, block.nodeId) : null;
        const subject = block.nodeId != null && currentPack ? findSubjectOf(currentPack, block.nodeId) : null;
        const isRevision = block.activityType === StudyActivityType.REVISION;
        // A revision block names its chapter (§7.2); renames come with the pack.
        const chapter = isRevision && block.nodeId != null && currentPack ? chapterOf(currentPack, block.nodeId) : null;
        return StudySessionRecord({
          id: block.id,
          date: block.scheduledDate,
          startMinuteOfDay: block.startMinuteOfDay,
          durationMinutes: block.plannedMinutes,
          nodeKey: block.nodeId ?? '',
          title: block.customTitle ?? chapter?.title ?? node?.title ?? 'Study session',
          sectionName: subject?.name ?? '',
          subjectId: block.subjectId ?? '',
          isRevision,
          revisionCount: isRevision ? ((block.nodeId != null ? this.revisionStates.get(block.nodeId)?.revisionCount : null) ?? 0) : 0,
          completed: block.status === PlanBlockStatus.COMPLETED || actual?.status === StudySessionStatus.COMPLETED,
          runningEndsAtMs: actual && actual.status === StudySessionStatus.RUNNING
            ? actual.startedAtEpochMs + block.plannedMinutes * 60_000
            : null,
        });
      });
  }

  async _fitUi() {
    const f = await this.rollingPlanRepository.planFit({ withOptions: false });
    return f && !f.fits ? PlanFitUi({ shortByMinutes: f.shortByMinutes, finishBy: f.finishBy, calibrated: f.calibrated }) : null;
  }

  /** "Yes": skip the missed blocks, put their work back in the queue and plan the next days again. */
  rebalanceMissed() {
    const prompt = this._state.value.missedBlocks;
    if (!prompt) return;
    this._update((it) => it.copy({ missedBlocks: null }));
    (async () => {
      await this.rollingPlanRepository.rebalanceMissed({ since: prompt.since });
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      const fit = await this._fitUi();
      this._update((it) => this.rebuild(it.copy({ planFit: fit })));
    })();
  }

  /**
   * "Start fresh from today": every missed block becomes SKIPPED, the plan is made again from
   * today and the missed state is cleared. Nothing afterwards counts what was missed.
   */
  startFresh() {
    const prompt = this._state.value.freshStart;
    if (!prompt) return;
    this._update((it) => this.rebuild(it.copy({ freshStart: null, missedBlocks: null, startedFreshToday: true })));
    (async () => {
      await this.rollingPlanRepository.startFresh({ since: prompt.since });
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      const fit = await this._fitUi();
      this._update((it) => this.rebuild(it.copy({ planFit: fit })));
    })();
  }

  /** "Taught in class today": the batch is at [chapterId], lecture [lecture]. Backlog and the plan's backlog share follow at once. */
  logClassToday(chapterId, lecture) {
    const id = this.attemptId;
    if (!id) return;
    (async () => {
      await this.chapterStageRepository.setBatchPosition(id, chapterId, lecture, clockNowMs());
      await this.rollingPlanRepository.replenish();
      await this.reloadCanonical(id);
      const coaching = await getOrNull(() => this.coachingToday(id, localToday()));
      this._update((it) => this.rebuild(it.copy({ coaching })));
    })();
  }

  /** One tap: the batch moved on one lecture in the same chapter. */
  nextLectureToday() {
    const coaching = this._state.value.coaching;
    if (!coaching) return;
    const chapterId = coaching.batchChapterId;
    if (chapterId == null) return;
    this.logClassToday(chapterId, coaching.batchLecture + 1);
  }

  async coachingToday(attemptId, today) {
    const examPack = this.pack;
    if (!examPack) return null;
    const counts = await this.chapterStageRepository.coachingFor(attemptId);
    const position = await this.chapterStageRepository.batchPosition(attemptId);
    if (counts.length === 0 && position == null) return null;
    const preferences = await this.studyPreferenceRepository.forAttempt(attemptId);
    const split = await this.rollingPlanRepository.liveBacklogSplit();
    const metrics = coachingBacklogMetrics(
      counts,
      position,
      examPack,
      averageDailyMinutes(
        preferences?.weekdayTargetMinutes ?? DEFAULT_WEEKDAY_MINUTES,
        preferences?.weekendTargetMinutes ?? DEFAULT_WEEKEND_MINUTES,
      ),
      split,
      today,
    );
    const chapters = examPack.subjects.flatMap((subject) =>
      subject.nodes.map((it) => CoachingChapterOption({ chapterId: it.id, title: it.title, subjectId: subject.id, subjectName: subject.name })));
    return CoachingTodayUi({
      batchChapterId: position?.chapterId ?? null,
      batchChapterTitle: position ? (chapters.find((it) => it.chapterId === position.chapterId)?.title ?? null) : null,
      batchLecture: position?.lecture ?? 0,
      chapters,
      backlog: examPack.subjects.flatMap((subject) => {
        const it = metrics.bySubject.get(subject.id);
        return it ? [CoachingBacklogRowUi({ subjectName: subject.name, lectures: it.lectures, dpps: it.dpps })] : [];
      }),
      clearDate: metrics.clearDate ?? null,
      liveSplit: split,
    });
  }

  /** "Not now": hidden for the rest of today. */
  dismissMissedBlocks() {
    this._update((it) => it.copy({ missedBlocks: null, freshStart: null }));
    this.rollingPlanRepository.dismissMissedPrompt();
  }

  selectDate(date) {
    this._update((it) => this.rebuild(it.copy({ selectedDate: date, placementIssue: null })));
  }

  toggleCalendarExpanded() {
    this._update((it) => it.copy({ calendarExpanded: !it.calendarExpanded }));
  }

  openAddStudy() {
    this._update((it) => it.copy({
      sheet: HomeSheet.PickTopic,
      pickerLevel: StudyPickerLevel.Subjects,
      pickerSectionIndex: null,
      pickerTopicPath: [],
      pickerQuery: '',
      pendingTopic: null,
      customEndMinuteOfDay: null,
      placementIssue: null,
    }));
  }

  /** Same picker, seeded so the duration step defaults to fit inside [startMinuteOfDay, endMinuteOfDay). */
  openAddStudyInGap(startMinuteOfDay, endMinuteOfDay) {
    const gapMinutes = coerceAtLeast(endMinuteOfDay - startMinuteOfDay, 1);
    const defaultDuration = [45, 30, 15].find((it) => it <= gapMinutes) ?? gapMinutes;
    this._update((it) => it.copy({
      sheet: HomeSheet.PickTopic,
      pickerLevel: StudyPickerLevel.Subjects,
      pickerSectionIndex: null,
      pickerTopicPath: [],
      pickerQuery: '',
      pendingTopic: null,
      selectedDurationMinutes: defaultDuration,
      customEndMinuteOfDay: endMinuteOfDay,
      placementIssue: null,
    }));
  }

  selectPickerSection(index) {
    this._update((it) => it.copy({
      pickerLevel: StudyPickerLevel.Topics, pickerSectionIndex: index, pickerTopicPath: [], pickerQuery: '',
    }));
  }

  openPickerSubtopics(path) {
    this._update((it) => it.copy({ pickerLevel: StudyPickerLevel.Subtopics, pickerTopicPath: path, pickerQuery: '' }));
  }

  setPickerQuery(query) {
    this._update((it) => it.copy({ pickerQuery: query }));
  }

  backInStudyPicker() {
    this._update((current) => {
      if (current.sheet === HomeSheet.PickDuration) return current.copy({ sheet: HomeSheet.PickTopic });
      if (current.pickerQuery.trim() !== '') return current.copy({ pickerQuery: '' });
      if (current.pickerLevel === StudyPickerLevel.Subtopics) {
        return current.copy({ pickerLevel: StudyPickerLevel.Topics, pickerTopicPath: [] });
      }
      if (current.pickerLevel === StudyPickerLevel.Topics) {
        return current.copy({ pickerLevel: StudyPickerLevel.Subjects, pickerSectionIndex: null });
      }
      return current.copy({ sheet: HomeSheet.None, pendingTopic: null });
    });
  }

  dismissSheet() {
    this._update((it) => it.copy({
      sheet: HomeSheet.None,
      pickerLevel: StudyPickerLevel.Subjects,
      pickerSectionIndex: null,
      pickerTopicPath: [],
      pickerQuery: '',
      pendingTopic: null,
      customEndMinuteOfDay: null,
      placementIssue: null,
    }));
  }

  pickTopic(nodeKey, title, sectionName, subjectId, topicPath = '', isRevision = false) {
    const type = isRevision ? StudyActivityType.REVISION : StudyActivityType.LEARN;
    this._update((it) => it.copy({
      pendingTopic: PendingTopic({ nodeKey, title, sectionName, subjectId, topicPath, activityType: type }),
      pendingRevisionDue: this.nextRevisionDue(nodeKey, type),
      sheet: HomeSheet.PickDuration,
      placementIssue: null,
    }));
  }

  nextRevisionDue(nodeKey, type) {
    if (nodeKey.trim() === '') return null;
    return nextRevisionDueIfCompleted(this.revisionStates.get(nodeKey) ?? null, type, localToday());
  }

  setDurationMinutes(minutes) {
    this._update((it) => it.copy({ selectedDurationMinutes: minutes, placementIssue: null }));
  }

  setActivityType(type) {
    if (![StudyActivityType.LEARN, StudyActivityType.PRACTICE, StudyActivityType.REVISION, StudyActivityType.MOCK_TEST].includes(type)) return;
    this._update((current) => current.copy({
      pendingTopic: current.pendingTopic?.copy({ activityType: type }) ?? null,
      pendingRevisionDue: current.pendingTopic ? this.nextRevisionDue(current.pendingTopic.nodeKey, type) : null,
    }));
  }

  setScheduledEndMinuteOfDay(minuteOfDay) {
    this._update((it) => it.copy({ customEndMinuteOfDay: minuteOfDay, placementIssue: null }));
  }

  confirmStartSprint() {
    const current = this._state.value;
    const pending = current.pendingTopic;
    if (!pending) return;
    const minutes = current.selectedDurationMinutes;
    const studyDate = current.selectedDate;
    if (!current.selectedIsToday) {
      this._update((it) => it.copy({ placementIssue: StudyPlacementIssue.OUTSIDE_DAY }));
      return;
    }
    const startMinuteOfDay = currentMinuteOfDay();
    const issue = validateStudyPlacement(
      this.allSessions, studyDate, startMinuteOfDay, minutes,
      this.fixedBlocksFor(studyDate), this.wakeMinute, this.sleepMinute,
    );
    if (issue != null) {
      this._update((it) => it.copy({ placementIssue: issue }));
      return;
    }
    (async () => {
      const nowMs = clockNowMs();
      const block = this.createManualBlock(pending, studyDate, startMinuteOfDay, minutes, nowMs);
      if (!block) return;
      await this.planRepository.upsert(block);
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      const found = this.allSessions.find((it) => it.id === block.id);
      if (!found) throw new Error('NoSuchElementException: Collection contains no element matching the predicate.');
      const session = found.copy({ runningEndsAtMs: nowMs + minutes * 60_000 });
      this.allSessions = this.allSessions.map((it) => (it.id === session.id ? session : it));
      this._update((it) => this.rebuild(it.copy({
        sheet: HomeSheet.None,
        pendingTopic: null,
        customEndMinuteOfDay: null,
        placementIssue: null,
        activeSprint: ActiveSprintUi({ sessionId: session.id, title: session.title, remainingSec: minutes * 60, durationMinutes: minutes }),
      })));
      this._focusRequests.trySend(session);
    })();
  }

  confirmAddToPlan() {
    const current = this._state.value;
    const pending = current.pendingTopic;
    if (!pending) return;
    const minutes = current.selectedDurationMinutes;
    const studyDate = current.selectedDate;
    const endMinuteOfDay = current.customEndMinuteOfDay ?? coerceAtMost(currentMinuteOfDay() + minutes, 24 * 60);
    const startMinuteOfDay = endMinuteOfDay - minutes;
    const issue = validateStudyPlacement(
      this.allSessions, studyDate, startMinuteOfDay, minutes,
      this.fixedBlocksFor(studyDate), this.wakeMinute, this.sleepMinute,
    );
    if (issue != null) {
      this._update((it) => it.copy({ placementIssue: issue }));
      return;
    }
    (async () => {
      const block = this.createManualBlock(pending, studyDate, startMinuteOfDay, minutes, clockNowMs());
      if (!block) return;
      await this.planRepository.upsert(block);
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      this._update((it) => this.rebuild(it.copy({
        sheet: HomeSheet.None, pendingTopic: null, customEndMinuteOfDay: null, placementIssue: null,
      })));
    })();
  }

  startScheduledSession(sessionId) {
    (async () => {
      const session = this.allSessions.find((it) => it.id === sessionId);
      if (!session) return;
      if (session.completed) return;
      if (session.runningEndsAtMs != null) {
        // Already running (e.g. tapped again from Today) — just re-enter Focus for it.
        this._focusRequests.trySend(session);
        return;
      }
      await this.startSessionNow(session);
    })();
  }

  startMissedSessionNow(sessionId) {
    (async () => {
      const session = this.allSessions.find((it) => it.id === sessionId);
      if (!session) return;
      if (session.completed || session.runningEndsAtMs != null) return;
      await this.startSessionNow(session);
    })();
  }

  replanRestOfToday() {
    (async () => {
      const today = localToday();
      const missed = this.allSessions.filter((it) =>
        it.date === today && !it.completed && it.runningEndsAtMs == null &&
        it.startMinuteOfDay + it.durationMinutes <= currentMinuteOfDay());
      const fixedByDate = new Map();
      for (let offset = 0; offset <= RECOVERY_HORIZON_DAYS; offset++) {
        const date = plusDays(today, offset);
        fixedByDate.set(date, this.fixedBlocksFor(date));
      }
      const placements = planMissedDayRecovery(
        this.allSessions, today, currentMinuteOfDay(), fixedByDate, this.wakeMinute, this.sleepMinute, RECOVERY_HORIZON_DAYS,
      );
      for (const placement of placements) {
        const session = missed.find((it) => it.id === placement.sessionId);
        if (session) await this.persistReschedule(session, placement.date, placement.startMinuteOfDay);
      }
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      this._update((it) => this.rebuild(it.copy({
        placementIssue: placements.length < missed.length ? StudyPlacementIssue.NO_AVAILABLE_TIME : null,
      })));
    })();
  }

  async beginSession(session) {
    const updated = session.copy({ runningEndsAtMs: clockNowMs() + session.durationMinutes * 60_000 });
    this.allSessions = this.allSessions.map((it) => (it.id === updated.id ? updated : it));
    this._update((it) => this.rebuild(it.copy({
      placementIssue: null,
      activeSprint: ActiveSprintUi({
        sessionId: updated.id, title: updated.title, remainingSec: updated.durationMinutes * 60, durationMinutes: updated.durationMinutes,
      }),
    })));
    this._focusRequests.trySend(updated);
  }

  async startSessionNow(session) {
    const today = localToday();
    if (session.date !== today) {
      this._update((it) => it.copy({ placementIssue: StudyPlacementIssue.OUTSIDE_DAY }));
      return;
    }
    const nowMinute = currentMinuteOfDay();
    const issue = validateStudyPlacement(
      this.allSessions, today, nowMinute, session.durationMinutes,
      this.fixedBlocksFor(today), this.wakeMinute, this.sleepMinute, session.id,
    );
    if (issue != null) {
      this._update((it) => it.copy({ placementIssue: issue }));
      return;
    }
    const moved = session.startMinuteOfDay === nowMinute ? session : await this.persistReschedule(session, today, nowMinute);
    await this.beginSession(moved);
  }

  /** Reposition one block on the timeline without losing its canonical history. */
  rescheduleToNextSlot(sessionId) {
    (async () => {
      const session = this.allSessions.find((it) => it.id === sessionId);
      if (!session || session.completed) return;
      const slotToday = findNextFreeSlot(
        this.allSessions, session.date, session.durationMinutes, currentMinuteOfDay(),
        this.fixedBlocksFor(session.date), this.wakeMinute, this.sleepMinute,
      );
      if (slotToday != null) {
        await this.applyReschedule(session, session.date, slotToday);
        return;
      }
      const tomorrow = plusDays(session.date, 1);
      const slotTomorrow = findNextFreeSlot(
        this.allSessions, tomorrow, session.durationMinutes, this.wakeMinute,
        this.fixedBlocksFor(tomorrow), this.wakeMinute, this.sleepMinute,
      );
      if (slotTomorrow != null) await this.applyReschedule(session, tomorrow, slotTomorrow);
      else this._update((it) => it.copy({ placementIssue: StudyPlacementIssue.NO_AVAILABLE_TIME }));
    })();
  }

  rescheduleToTomorrowSameTime(sessionId) {
    (async () => {
      const session = this.allSessions.find((it) => it.id === sessionId);
      if (!session || session.completed) return;
      const date = plusDays(session.date, 1);
      const issue = validateStudyPlacement(
        this.allSessions, date, session.startMinuteOfDay, session.durationMinutes,
        this.fixedBlocksFor(date), this.wakeMinute, this.sleepMinute, session.id,
      );
      if (issue == null) await this.applyReschedule(session, date, session.startMinuteOfDay);
      else this._update((it) => it.copy({ placementIssue: issue }));
    })();
  }

  rescheduleToTime(sessionId, minuteOfDay) {
    (async () => {
      const session = this.allSessions.find((it) => it.id === sessionId);
      if (!session || session.completed) return;
      const issue = validateStudyPlacement(
        this.allSessions, session.date, minuteOfDay, session.durationMinutes,
        this.fixedBlocksFor(session.date), this.wakeMinute, this.sleepMinute, session.id,
      );
      if (issue == null) await this.applyReschedule(session, session.date, minuteOfDay);
      else this._update((it) => it.copy({ placementIssue: issue }));
    })();
  }

  skipSession(sessionId) {
    (async () => {
      await this.planRepository.skip(sessionId, clockNowMs());
      await this.rollingPlanRepository.replenish();
      if (this.attemptId) await this.reloadCanonical(this.attemptId);
      this._update((it) => this.rebuild(it.copy({ placementIssue: null })));
    })();
  }

  async applyReschedule(session, date, startMinuteOfDay) {
    await this.persistReschedule(session, date, startMinuteOfDay);
    await this.rollingPlanRepository.replenish();
    if (this.attemptId) await this.reloadCanonical(this.attemptId);
    this._update((it) => this.rebuild(it.copy({ placementIssue: null })));
  }

  async persistReschedule(session, date, startMinuteOfDay) {
    const normalized = await this.planRepository.byId(session.id);
    if (!normalized) return session;
    const replacementId = randomUUID();
    await this.planRepository.reschedule(normalized, date, startMinuteOfDay, replacementId, clockNowMs());
    const updated = session.copy({ id: replacementId, date, startMinuteOfDay, runningEndsAtMs: null, completed: false });
    this.allSessions = [...this.allSessions.filter((it) => it.id !== session.id), updated];
    return updated;
  }

  createManualBlock(pending, date, startMinuteOfDay, minutes, nowMs) {
    const id = this.attemptId;
    if (!id) return null;
    const blankKey = pending.nodeKey.trim() === '';
    return StudyPlanBlock({
      id: randomUUID(),
      attemptId: id,
      nodeId: blankKey ? null : pending.nodeKey,
      subjectId: pending.subjectId.trim() === '' ? null : pending.subjectId,
      customTitle: blankKey ? pending.title : null,
      activityType: pending.activityType,
      scheduledDate: date,
      startMinuteOfDay,
      plannedMinutes: minutes,
      status: PlanBlockStatus.PLANNED,
      source: pending.activityType === StudyActivityType.REVISION ? PlanBlockSource.REVISION_ENGINE : PlanBlockSource.MANUAL,
      rescheduledFromId: null,
      replacedById: null,
      createdAtEpochMs: nowMs,
      updatedAtEpochMs: nowMs,
    });
  }

  fixedBlocksFor(date) {
    const free = sortedBy(effectiveWindowsFor(date, this.weeklyAvailability, this.availabilityOverrides), (it) => it[0]);
    const fixed = [];
    let cursor = this.wakeMinute;
    free.forEach(([start, end], index) => {
      if (start > cursor) {
        fixed.push(FixedCommitmentBlock({ id: `fixed_${date}_${index}`, kind: 'other', startMinuteOfDay: cursor, endMinuteOfDay: start, customLabel: 'Fixed time' }));
      }
      cursor = Math.max(cursor, end);
    });
    if (cursor < this.sleepMinute) {
      fixed.push(FixedCommitmentBlock({ id: `fixed_${date}_end`, kind: 'other', startMinuteOfDay: cursor, endMinuteOfDay: this.sleepMinute, customLabel: 'Fixed time' }));
    }
    return fixed;
  }

  /** HomeUiState.rebuild() */
  rebuild(s) {
    if (!s.hasPlan) {
      return s.copy({
        weekDays: [], monthDays: [], monthTitle: '', dayTimeline: [], completedTodayMinutes: 0,
        plannedTodayMinutes: 0, completionPercent: 0, dayBudgetMinutes: 0, missedDayRecovery: null,
      });
    }
    const today = localToday();
    const selectedDate = s.selectedDate;
    const daySessions = this.allSessions.filter((it) => it.date === selectedDate);
    const budgetMinutes = todayBudgetMinutes(this.weekdayHours, this.weekendHours, selectedDate);
    const doneMins = this.studiedByDate.get(selectedDate) ?? 0;
    const percent = budgetMinutes > 0 ? Math.min(100, toInt((Math.fround(doneMins) / budgetMinutes) * 100)) : 0;
    const selectedIsToday = selectedDate === today;
    const nowMinute = currentMinuteOfDay();
    const missedSessions = selectedIsToday
      ? sortedBy(daySessions.filter((it) =>
        !it.completed && it.runningEndsAtMs == null && it.startMinuteOfDay + it.durationMinutes <= nowMinute), (it) => it.startMinuteOfDay)
      : [];
    const builtTimeline = buildDayTimeline(
      daySessions, this.sections, selectedDate, today, nowMinute,
      this.fixedBlocksFor(selectedDate), this.wakeMinute, this.sleepMinute,
    );
    const plannedMinutes = sumOf(builtTimeline, (entry) => (entry.type === 'Study' ? entry.block.durationMinutes : 0));
    // After "Start fresh" nothing on Today counts what was missed.
    const nextMissed = !s.startedFreshToday ? (missedSessions[0] ?? null) : null;
    const recovery = nextMissed ? MissedDayRecoveryUi({
      missedCount: missedSessions.length,
      availableTodayMinutes: sumOf(builtTimeline.filter((it) => it.type === 'Gap'),
        (gap) => coerceAtLeast(gap.endMinuteOfDay - Math.max(gap.startMinuteOfDay, nowMinute), 0)),
      nextSessionId: nextMissed.id,
      nextSessionTitle: nextMissed.title,
      canStartNow: validateStudyPlacement(
        this.allSessions, today, nowMinute, nextMissed.durationMinutes,
        this.fixedBlocksFor(today), this.wakeMinute, this.sleepMinute, nextMissed.id,
      ) == null,
    }) : null;

    const dayUi = (date) => {
      const dayBudget = todayBudgetMinutes(this.weekdayHours, this.weekendHours, date);
      const dayDone = this.studiedByDate.get(date) ?? 0;
      return WeekDayUi({
        date,
        dayOfMonth: dayOfMonth(date),
        selected: date === selectedDate,
        status: weekStatusForDayMinutes(date, today, dayDone, dayBudget),
      });
    };

    const monthAnchor = withDayOfMonth(selectedDate, 1);
    const leadingBlanks = dayOfWeek(monthAnchor).value - 1;
    const monthCells = [];
    for (let i = 0; i < leadingBlanks; i++) monthCells.push(null);
    for (let day = 0; day < lengthOfMonth(monthAnchor); day++) monthCells.push(dayUi(plusDays(monthAnchor, day)));

    return s.copy({
      weekDays: weekAround(selectedDate).map(dayUi),
      monthDays: monthCells,
      selectedIsToday,
      selectedDayLabel: '',
      dayBudgetMinutes: budgetMinutes,
      completionPercent: percent,
      dayTimeline: builtTimeline,
      completedTodayMinutes: doneMins,
      plannedTodayMinutes: plannedMinutes,
      missedDayRecovery: recovery,
      canOpenDayLog: selectedDate < today && doneMins > 0,
    });
  }

  /** HomeViewModel.Factory */
  static Factory(...repos) {
    return { create: () => new HomeViewModel(...repos) };
  }
}

/** ExamPack.toPickerSections() */
function toPickerSections(pack) {
  const convert = (value) => SyllabusTopicNode({
    name: value.title,
    hours: value.estimatedMinutes != null ? value.estimatedMinutes / 60 : null,
    children: value.children.map(convert),
    id: value.id,
  });
  return pack.subjects.map((subject) => SyllabusSection({
    name: subject.name,
    questions: subject.questions ?? 0,
    id: subject.id,
    topics: subject.nodes.map(convert),
  }));
}
