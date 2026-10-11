// Port of feature/onboarding/OnboardingViewModel.kt
//
// Web stand-ins:
//  - viewModelScope.launch → async fns; onCleared() stops late updates. fitJob.cancel() → a job token.
//  - SavedStateHandle → _local.js (sessionStorage; survives reload like process death).
//  - The draft collector (state.drop(1).collect) runs on a microtask, like a dispatched coroutine,
//    so hydrate's "no draft yet" check sees the handle before the collector writes it.
//  - Float hours are JS numbers. LocalDate = 'YYYY-MM-DD'. Sets are JS Sets.
import { Analytics, AnalyticsEvent, AnalyticsParam, randomUUID, mapOf } from '../../data/common.js';
import { SavedPlan } from '../../data/SavedPlan.js';
import { PlanDraft } from '../../data/repo/RollingPlanRepository.js';
import { MutableStateFlow } from '../../platform/flow.js';
import { ExamAttempt, ExamAttemptStatus, StartingPoint } from '../../domain/ExamAttempt.js';
import { PlanMode } from '../../domain/PlanMode.js';
import { NEET_EXAM_ID, leafIds } from '../../domain/ExamPack.js';
import { MIN_FINISH_BY_DAYS_AHEAD, coerceFinishBy, defaultFinishByDate } from '../../domain/ExamDates.js';
import { StudyPreferences } from '../../domain/StudyPreferences.js';
import { TargetNodeState } from '../../domain/TargetSyllabus.js';
import { TopicProgressStatus } from '../../domain/TopicProgress.js';
import { WeeklyAvailability } from '../../domain/Availability.js';
import { DayOfWeek, today as localToday, nowMs, plusDays, plusMonths, maxDate, toEpochDay, ofEpochDay } from '../../domain/time.js';
import { roundToInt, coerceIn, coerceAtMost, toIntOrNull, deepEquals } from '../../domain/kt.js';
import { DAY_SHAPES, DEFAULT_SLEEP_MINUTE, DEFAULT_WAKE_MINUTE, OnboardingCommitment, OnboardingSyllabusChapter, OnboardingSyllabusSubject, OnboardingUiState, commitmentsForStudyMode, defaultCommitments } from './model/OnboardingUiState.js';
import { OnboardingStep } from './model/OnboardingStep.js';
import { ExamCatalog } from './model/ExamCatalog.js';
import { finishedAfterStartingPointChange, toggleChapterFinished, toggleClassFinished, toggleSubjectFinished } from './model/FinishedChapters.js';

const DRAFT_KEY = 'onboarding_draft_v1';
const DRAFT_ATTEMPT_ID = 'draft';
const MAX_WEEKDAY_HOURS = 14;
const MAX_WEEKEND_HOURS = 16;

const setMinus = (a, b) => { const bs = b instanceof Set ? b : new Set(b); return new Set([...a].filter((x) => !bs.has(x))); };
const setPlus = (a, b) => new Set([...a, ...b]);
const minusYears = (d, n) => plusMonths(d, -12 * n);

export class OnboardingViewModel {
  constructor(planStore, examPackRepository, attemptRepository, availabilityRepository, studyPreferenceRepository,
    targetSyllabusRepository, topicProgressRepository, planRepository, rollingPlanRepository, dispatchers,
    savedStateHandle, examDate, examDateOfficial, startAtPlan, analytics = Analytics.NoOp) {
    Object.assign(this, {
      planStore, examPackRepository, attemptRepository, availabilityRepository, studyPreferenceRepository,
      targetSyllabusRepository, topicProgressRepository, planRepository, rollingPlanRepository, dispatchers,
      savedStateHandle, startAtPlan, analytics,
    });
    this.currentPack = null;
    this.fitJob = null;
    /** Chapters that were fully finished when this screen opened, so only real changes are written back. */
    this.initialCoveredChapterIds = new Set();
    this.cleared = false;
    this._state = new MutableStateFlow(
      OnboardingUiState({ examDate, examDateOfficial, configExamDate: examDate, targetDate: defaultFinishByDate(examDate, localToday()) }),
      { equals: deepEquals },
    );
    this.state = this._state.asStateFlow();
    this._finished = new MutableStateFlow(false);
    this.finished = this._finished.asStateFlow();
    this._openOrganiseAfterFinish = new MutableStateFlow(false);
    this.openOrganiseAfterFinish = this._openOrganiseAfterFinish.asStateFlow();

    this.analytics.log(AnalyticsEvent.ONBOARDING_STARTED);
    this.hydrate();
    let first = true, scheduled = false;
    this._unsubDraft = this.state.subscribe(() => {
      if (first) { first = false; return; } // drop(1)
      if (scheduled) return;
      scheduled = true;
      queueMicrotask(() => {
        scheduled = false;
        if (this.cleared) return;
        const current = this._state.value;
        if (!current.loading && !current.finishing) this.savedStateHandle.set(DRAFT_KEY, encodeDraft(current));
      });
    });
  }

  _update(fn) { if (!this.cleared) this._state.update(fn); }

  onCleared() {
    this.cleared = true;
    this._unsubDraft?.();
    if (this.fitJob) this.fitJob.cancelled = true;
    // The back-stack entry is gone: its SavedStateHandle goes with it.
    this.savedStateHandle.remove(DRAFT_KEY);
  }

  back() {
    this._update((current) => { const p = current.step.previous(); return p ? current.copy({ step: p, finishFailed: false }) : current; });
  }

  continueFromCurrent() {
    const current = this._state.value;
    if (!this.canContinue(current)) return;
    if (current.step === OnboardingStep.Plan) this.finish(current);
    else if (current.step === OnboardingStep.Syllabus) {
      this._update((it) => it.copy({ step: OnboardingStep.Plan, fit: null }));
      this.refreshFit();
    } else this.advance();
  }

  canContinue(state = this._state.value) {
    switch (state.step) {
      case OnboardingStep.Coaching: return state.coachingId.trim() !== '';
      case OnboardingStep.Commitments: return this.commitmentIssues(state).length === 0;
      case OnboardingStep.Hours: return this.capacityIssues(state).length === 0;
      case OnboardingStep.Start: return state.startingPoint != null;
      case OnboardingStep.Syllabus: return !state.useProvidedSyllabus || state.selectedChapterIds.size > 0;
      case OnboardingStep.Plan: return !state.finishing;
      default: return true;
    }
  }

  selectExam(examId) {
    if (!ExamCatalog.contains(examId)) return;
    this._update((it) => it.copy({
      examId, useProvidedSyllabus: ExamCatalog.hasBundledSyllabus(examId), selectedChapterIds: new Set(), syllabusSubjects: [],
    }));
    this.loadPackFor(examId);
  }

  selectCoaching(id) {
    if (!['self', 'school', 'coaching'].includes(id)) return;
    this._update((it) => it.copy({ coachingId: id, commitments: commitmentsForStudyMode(it.commitments, id) }));
  }

  /** "Do you have a date you want to finish the syllabus by?" Yes is deadline mode, Not yet is steady mode. */
  setPlanMode(mode) {
    this._update((it) => it.copy({ planMode: mode }));
    this.refreshFitIfOnPlanStep();
  }

  /** The exam date is editable. The finish-by date follows if the exam moves in front of it. */
  setExamDate(date) {
    const today = localToday();
    const exam = maxDate(date, plusDays(today, MIN_FINISH_BY_DAYS_AHEAD));
    this._update((it) => it.copy({
      examDate: exam,
      targetDate: coerceFinishBy(it.targetDate > exam ? exam : it.targetDate, exam, today),
    }));
    this.refreshFitIfOnPlanStep();
  }

  /** "Which class / attempt?" A dropper starts with most chapters finished once; the student unticks exceptions. */
  selectStartingPoint(point) {
    const allIds = topLevelNodeIds(this.currentPack);
    this._update((current) => {
      const [covered, auto] = finishedAfterStartingPointChange({
        point, current: current.coveredChapterIds, autoFilled: current.coveredIsAutoFilled,
        keepExisting: current.editMode, allChapterIds: allIds,
      });
      return current.copy({ startingPoint: point, coveredChapterIds: covered, coveredIsAutoFilled: auto });
    });
  }

  toggleFinishedChapter(chapterId) { this.updateFinished((it) => toggleChapterFinished(it, chapterId)); }

  toggleFinishedSubject(subjectId) {
    const subject = this._state.value.syllabusSubjects.find((it) => it.id === subjectId);
    if (!subject) return;
    this.updateFinished((it) => toggleSubjectFinished(it, subject));
  }

  toggleFinishedClass(classNumber) {
    this.updateFinished((it) => toggleClassFinished(it, this._state.value.syllabusSubjects, classNumber));
  }

  updateFinished(change) {
    this._update((it) => it.copy({ coveredChapterIds: change(it.coveredChapterIds), coveredIsAutoFilled: false }));
    this.refreshFitIfOnPlanStep();
  }

  setTargetDate(date) {
    const today = localToday();
    this._update((it) => it.copy({ targetDate: coerceFinishBy(date, it.examDate, today) }));
    this.refreshFitIfOnPlanStep();
  }

  addCommitment(kind) {
    const preset = commitmentPreset(kind);
    if (!preset) return;
    this._update((it) => it.copy({ commitments: [...it.commitments, preset.copy({ id: randomUUID() })] }));
  }

  removeCommitment(id) {
    this._update((it) => it.copy({ commitments: it.commitments.filter((item) => item.id !== id) }));
  }

  renameCommitment(id, label) {
    this.updateCommitment(id, (c) => {
      const t = label.trim().slice(0, 40);
      return c.copy({ customLabel: t.trim() === '' ? null : t });
    });
  }

  toggleCommitmentDay(id, day) {
    if (!(day >= 0 && day <= 6)) return;
    this.updateCommitment(id, (c) => c.copy({ days: c.days.has(day) ? setMinus(c.days, [day]) : setPlus(c.days, [day]) }));
  }

  setCommitmentStart(id, minute) {
    this.updateCommitment(id, (c) => {
      const start = coerceIn(minute, 0, 23 * 60 + 30);
      return c.copy({ startMinute: start, endMinute: coerceAtMost(Math.max(start + 30, c.endMinute), 24 * 60) });
    });
  }

  setCommitmentEnd(id, minute) {
    this.updateCommitment(id, (c) => c.copy({ endMinute: coerceIn(minute, c.startMinute + 30, 24 * 60) }));
  }

  setWakeMinute(minute) { this._update((it) => it.copy({ wakeMinute: coerceIn(minute, 0, 23 * 60) })); }

  setSleepMinute(minute) { this._update((it) => it.copy({ sleepMinute: coerceIn(minute, 60, 24 * 60) })); }

  selectWork(id) {
    const shape = DAY_SHAPES.find((it) => it.id === id);
    if (!shape) return;
    this._update((it) => it.copy({ workId: id, weekdayHours: shape.weekdayHours, weekendHours: shape.weekendHours }));
  }

  setWeekdayHours(hours) {
    this._update((it) => it.copy({ weekdayHours: halfHourStep(hours) }));
    this.refreshFitIfOnPlanStep();
  }

  setWeekendHours(hours) {
    this._update((it) => it.copy({ weekendHours: halfHourStep(hours) }));
    this.refreshFitIfOnPlanStep();
  }

  setStudyPlace(place) { this._update((it) => it.copy({ studyPlace: place })); }

  setUseProvidedSyllabus(useProvided) {
    if (useProvided && !ExamCatalog.hasBundledSyllabus(this._state.value.examId)) return;
    this._update((it) => it.copy({
      useProvidedSyllabus: useProvided,
      selectedChapterIds: useProvided ? topLevelNodeIds(this.currentPack) : new Set(),
    }));
  }

  toggleTargetChapter(chapterId) {
    this._update((it) => it.copy({
      selectedChapterIds: it.selectedChapterIds.has(chapterId) ? setMinus(it.selectedChapterIds, [chapterId]) : setPlus(it.selectedChapterIds, [chapterId]),
    }));
  }

  toggleTargetSubject(subjectId) {
    const subjectChapterIds = new Set(this._state.value.syllabusSubjects.find((it) => it.id === subjectId)?.chapters?.map((it) => it.id) ?? []);
    if (subjectChapterIds.size === 0) return;
    this._update((current) => {
      const allSelected = [...subjectChapterIds].every((it) => current.selectedChapterIds.has(it));
      return current.copy({
        selectedChapterIds: allSelected ? setMinus(current.selectedChapterIds, subjectChapterIds) : setPlus(current.selectedChapterIds, subjectChapterIds),
      });
    });
  }

  dismissFinishError() { this._update((it) => it.copy({ finishFailed: false })); }

  commitmentIssues(state = this._state.value) { return commitmentIssuesFor(state); }

  capacityIssues(state = this._state.value) { return capacityIssuesFor(state); }

  loadPackFor(examId) {
    (async () => {
      const pack = await this.examPackRepository.examPackFor(examId);
      if (this._state.value.examId !== examId) return;
      this.currentPack = pack;
      const subjects = subjectsOf(pack);
      this._update((current) => (current.examId !== examId ? current : current.copy({
        loading: false,
        syllabusSubjects: subjects,
        selectedChapterIds: current.useProvidedSyllabus
          ? (current.selectedChapterIds.size === 0 ? topLevelNodeIds(pack) : current.selectedChapterIds)
          : new Set(),
      })));
    })().catch((e) => console.error(e));
  }

  hydrate() {
    (async () => {
      const pack = await this.examPackRepository.examPackFor(NEET_EXAM_ID);
      let attempt = await this.attemptRepository.activeAttempt();
      if (attempt && attempt.examId !== NEET_EXAM_ID) attempt = null;
      let saved = await this.planStore.load();
      if (saved && saved.examId !== NEET_EXAM_ID) saved = null;
      const preferences = attempt ? await this.studyPreferenceRepository.forAttempt(attempt.id) : null;
      const overrides = attempt ? await this.targetSyllabusRepository.overridesFor(attempt.id) : [];
      const coveredLeafIds = attempt
        ? new Set((await this.topicProgressRepository.allOnce(attempt.id)).filter((it) => it.status === TopicProgressStatus.COVERED).map((it) => it.nodeId))
        : new Set();
      const excludedIds = new Set(overrides.filter((it) => it.state === TargetNodeState.EXCLUDED).map((it) => it.nodeId));
      const subjects = subjectsOf(pack);
      this.currentPack = pack;
      if (this.cleared) return;
      this._update((current) => {
        const savedCommitments = saved ? saved.commitments ? [...saved.commitments].map(decodeCommitment).filter(Boolean) : null : null;
        const hydrated = current.copy({
          loading: false,
          editMode: attempt != null,
          examId: NEET_EXAM_ID,
          coachingId: saved ? (saved.coachingId.trim() === '' ? 'self' : saved.coachingId) : '',
          commitments: savedCommitments && savedCommitments.length ? savedCommitments : defaultCommitments(),
          wakeMinute: saved?.wakeMinute ?? current.wakeMinute,
          sleepMinute: saved?.sleepMinute ?? current.sleepMinute,
          planMode: attempt?.planMode ?? current.planMode,
          examDate: attempt?.examDate ?? current.examDate,
          targetDate: coerceFinishBy(
            attempt?.targetCompletionDate
              ?? (saved?.targetDateEpochDay != null ? ofEpochDay(saved.targetDateEpochDay) : null)
              ?? current.targetDate,
            attempt?.examDate ?? current.examDate,
            localToday(),
          ),
          workId: saved?.workId ?? current.workId,
          weekdayHours: preferences?.weekdayTargetMinutes != null ? preferences.weekdayTargetMinutes / 60 : saved?.weekdayHours ?? current.weekdayHours,
          weekendHours: preferences?.weekendTargetMinutes != null ? preferences.weekendTargetMinutes / 60 : saved?.weekendHours ?? current.weekendHours,
          studyPlace: preferences?.defaultStudyPlace ?? saved?.studyPlace ?? current.studyPlace,
          useProvidedSyllabus: saved?.useProvidedSyllabus ?? true,
          selectedChapterIds: setMinus(topLevelNodeIds(pack), excludedIds),
          coveredChapterIds: new Set(pack.subjects.flatMap((it) => it.nodes)
            .filter((chapter) => leafIds(chapter).length > 0 && leafIds(chapter).every((it) => coveredLeafIds.has(it)))
            .map((it) => it.id)),
          syllabusSubjects: subjects,
          startingPoint: attempt?.startingPoint ?? null,
        });
        this.initialCoveredChapterIds = hydrated.coveredChapterIds;
        const draft = this.savedStateHandle.get(DRAFT_KEY);
        return draft != null ? decodeDraft(draft, hydrated) : hydrated;
      });
      // "Adjust plan" from Today lands on the plan, where the fit and its choices are.
      if (this.startAtPlan && attempt != null && this.savedStateHandle.get(DRAFT_KEY) == null) {
        this._update((it) => it.copy({ step: OnboardingStep.Plan, fit: null }));
        this.refreshFit();
      }
    })().catch((e) => console.error(e));
  }

  updateCommitment(id, transform) {
    this._update((current) => current.copy({ commitments: current.commitments.map((it) => (it.id === id ? transform(it) : it)) }));
  }

  /** Optional steps only: Theme keeps its defaults; Fixed times drops whatever was added. */
  canSkip(state = this._state.value) {
    return state.step === OnboardingStep.Appearance || state.step === OnboardingStep.Commitments
      || (state.step === OnboardingStep.Syllabus && state.useProvidedSyllabus);
  }

  skipCurrent() {
    const current = this._state.value;
    if (!this.canSkip(current)) return;
    if (current.step === OnboardingStep.Commitments) {
      this._update((it) => it.copy({
        commitments: [],
        wakeMinute: it.wakeMinute < it.sleepMinute ? it.wakeMinute : DEFAULT_WAKE_MINUTE,
        sleepMinute: it.wakeMinute < it.sleepMinute ? it.sleepMinute : DEFAULT_SLEEP_MINUTE,
      }));
    }
    if (current.step === OnboardingStep.Syllabus) {
      // Skip keeps the whole syllabus; chapters can be removed later.
      const all = new Set(current.syllabusSubjects.flatMap((it) => it.chapters.map((c) => c.id)));
      this._update((it) => it.copy({ selectedChapterIds: all, step: OnboardingStep.Plan, fit: null }));
      this.refreshFit();
      return;
    }
    this.advance();
  }

  advance() {
    this._update((current) => { const n = current.step.next(); return n ? current.copy({ step: n }) : current; });
  }

  refreshFitIfOnPlanStep() {
    if (this._state.value.step === OnboardingStep.Plan) this.refreshFit();
  }

  /**
   * Asks the planner whether the remaining work fits before finish-by. It is the same
   * simulation that builds the plan, fed the same history, so the answer is what will be scheduled.
   */
  refreshFit() {
    if (this.fitJob) this.fitJob.cancelled = true;
    const draft = this._state.value;
    if (!draft.useProvidedSyllabus) {
      this._update((it) => it.copy({ fit: null, fitLoading: false, projectedFinish: null, projectionLoading: false }));
      return;
    }
    this._update((it) => it.copy({ fitLoading: true, projectionLoading: it.planMode === PlanMode.STEADY }));
    const job = { cancelled: false };
    this.fitJob = job;
    (async () => {
      const planDraft = PlanDraft({
        mode: draft.planMode,
        examDate: draft.examDate,
        finishBy: draft.targetDate,
        weekdayTargetMinutes: roundToInt(draft.weekdayHours * 60),
        weekendTargetMinutes: roundToInt(draft.weekendHours * 60),
        weeklyAvailability: freeWindowsFor(DRAFT_ATTEMPT_ID, draft),
        includedChapterIds: draft.selectedChapterIds,
        coveredChapterIds: draft.coveredChapterIds,
        startingPoint: draft.startingPoint,
      });
      const fit = draft.planMode === PlanMode.DEADLINE ? await this.rollingPlanRepository.draftFit(planDraft) : null;
      if (job.cancelled) return;
      const projected = draft.planMode === PlanMode.STEADY ? await this.rollingPlanRepository.draftProjection(planDraft) : null;
      if (job.cancelled) return;
      const targets = await this.rollingPlanRepository.draftTargets(planDraft);
      if (job.cancelled) return;
      this._update((current) => {
        // A newer change started its own request; only the latest answer may land.
        if (current.planMode === draft.planMode && current.examDate === draft.examDate &&
          current.targetDate === draft.targetDate && current.weekdayHours === draft.weekdayHours &&
          current.weekendHours === draft.weekendHours && deepEquals(current.selectedChapterIds, draft.selectedChapterIds) &&
          deepEquals(current.coveredChapterIds, draft.coveredChapterIds)) {
          return current.copy({ fit, targets, fitLoading: false, projectedFinish: projected, projectionLoading: false });
        }
        return current;
      });
    })().catch((e) => console.error(e));
  }

  /** Applies one of the choices shown when the plan is short. Each was verified by planning again. */
  applyFitOption(option) {
    switch (option.type) {
      case 'AddMinutesPerDay':
        this._update((it) => it.copy({
          weekdayHours: coerceAtMost(it.weekdayHours + option.minutes / 60, MAX_WEEKDAY_HOURS),
          weekendHours: coerceAtMost(it.weekendHours + option.minutes / 60, MAX_WEEKEND_HOURS),
        }));
        break;
      case 'ParkChapters':
        this._update((it) => it.copy({ selectedChapterIds: setMinus(it.selectedChapterIds, option.chapterIds) }));
        break;
      case 'MoveFinishBy': {
        const today = localToday();
        this._update((it) => it.copy({ targetDate: coerceFinishBy(option.date, it.examDate, today) }));
        break;
      }
      case 'AcceptCoverage':
        this.continueFromCurrent();
        return;
      default: return;
    }
    this.refreshFitIfOnPlanStep();
  }

  finish(state) {
    if (state.finishing) return;
    this._update((it) => it.copy({ finishing: true, finishFailed: false }));
    (async () => {
      const existingAttempt = await this.attemptRepository.activeAttempt();
      const attemptId = existingAttempt?.id ?? randomUUID();
      const today = localToday();
      const previousPlan = existingAttempt ? await this.planRepository.planBetween(attemptId, minusYears(today, 2), minusYears(today, -2)) : [];
      const previousAvailability = existingAttempt ? await this.availabilityRepository.weeklyFor(attemptId) : [];
      const previousPreferences = existingAttempt ? await this.studyPreferenceRepository.forAttempt(attemptId) : null;
      const previousOverrides = existingAttempt ? await this.targetSyllabusRepository.overridesFor(attemptId) : [];
      try {
        const now = nowMs();
        const pack = (this.currentPack && this.currentPack.examId === state.examId)
          ? this.currentPack : await this.examPackRepository.examPackFor(state.examId);
        const attempt = ExamAttempt({
          id: attemptId,
          examId: state.examId,
          syllabusVersion: pack.syllabusVersion,
          examDate: state.examDate,
          targetCompletionDate: state.targetDate,
          createdAtEpochMs: existingAttempt?.createdAtEpochMs ?? now,
          updatedAtEpochMs: now,
          status: ExamAttemptStatus.ACTIVE,
          planMode: state.planMode,
          startingPoint: state.startingPoint,
        });

        await this.attemptRepository.upsert(attempt);
        await this.saveFinishedChapters(attemptId, pack, state.coveredChapterIds, now);

        const availability = freeWindowsFor(attemptId, state);
        await this.availabilityRepository.replaceWeekly(attemptId, availability);
        await this.studyPreferenceRepository.upsert(
          (previousPreferences ?? StudyPreferences({ attemptId })).copy({
            weekdayTargetMinutes: roundToInt(state.weekdayHours * 60),
            weekendTargetMinutes: roundToInt(state.weekendHours * 60),
            autoScheduleRevision: true,
            defaultStudyPlace: state.studyPlace.trim() === '' ? null : state.studyPlace.trim(),
          }),
        );

        for (const override of await this.targetSyllabusRepository.overridesFor(attemptId)) {
          await this.targetSyllabusRepository.clearOverride(attemptId, override.nodeId);
        }
        const excludedNodes = pack.subjects.flatMap((it) => it.nodes)
          .filter((it) => !state.useProvidedSyllabus || !state.selectedChapterIds.has(it.id));
        for (const node of excludedNodes) {
          await this.targetSyllabusRepository.setState(attemptId, node.id, TargetNodeState.EXCLUDED, now);
        }
        await this.planStore.save(SavedPlan({
          examId: state.examId,
          daysUntilExam: state.daysUntilTarget,
          workId: state.workId,
          weekdayHours: state.weekdayHours,
          weekendHours: state.weekendHours,
          studyPlace: state.studyPlace.trim(),
          targetDateEpochDay: toEpochDay(state.targetDate),
          coachingId: state.coachingId,
          wakeMinute: state.wakeMinute,
          sleepMinute: state.sleepMinute,
          useProvidedSyllabus: state.useProvidedSyllabus,
          coveredSubjectIds: new Set(),
          commitments: new Set(state.commitments.map(encodeCommitment)),
        }));
        await this.rollingPlanRepository.replenish({ fromDate: localToday(), nowMs: now });
        this.analytics.log(AnalyticsEvent.ONBOARDING_COMPLETED, mapOf(
          [AnalyticsParam.MODE, state.planMode.name],
          [AnalyticsParam.STARTING_POINT, state.startingPoint?.name ?? 'none'],
        ));
        this.analytics.log(AnalyticsEvent.PLAN_GENERATED, mapOf([AnalyticsParam.MODE, state.planMode.name]));
        this.savedStateHandle.remove(DRAFT_KEY);
        this._update((it) => it.copy({ finishing: false }));
        this._openOrganiseAfterFinish.value = !state.useProvidedSyllabus || state.selectedChapterIds.size === 0;
        this._finished.value = true;
      } catch (e) {
        console.warn('onboarding finish failed', e);
        const quiet = async (f) => { try { await f(); } catch {} };
        await quiet(() => this.restoreFinishedChapters(attemptId, this.currentPack, state.coveredChapterIds));
        await quiet(() => this.planRepository.replaceAllForAttempt(attemptId, previousPlan));
        if (existingAttempt == null) {
          await quiet(() => this.attemptRepository.deleteAttemptAndAllData(attemptId));
        } else {
          await quiet(() => this.availabilityRepository.replaceWeekly(attemptId, previousAvailability));
          await quiet(async () => { if (previousPreferences) await this.studyPreferenceRepository.upsert(previousPreferences); });
          await quiet(async () => {
            for (const override of await this.targetSyllabusRepository.overridesFor(attemptId)) {
              await this.targetSyllabusRepository.clearOverride(attemptId, override.nodeId);
            }
            for (const override of previousOverrides) {
              await this.targetSyllabusRepository.setState(attemptId, override.nodeId, override.state, override.updatedAtEpochMs);
            }
          });
          await quiet(() => this.attemptRepository.upsert(existingAttempt));
        }
        this._update((it) => it.copy({ finishing: false, finishFailed: true }));
      }
    })();
  }

  /** Writes only what changed since the screen opened: newly finished chapters on, unticked ones off. */
  async saveFinishedChapters(attemptId, pack, covered, now) {
    const chapters = new Map(pack.subjects.flatMap((it) => it.nodes).map((it) => [it.id, it]));
    const added = [...setMinus(covered, this.initialCoveredChapterIds)].map((it) => chapters.get(it)).filter(Boolean);
    const removed = [...setMinus(this.initialCoveredChapterIds, covered)].map((it) => chapters.get(it)).filter(Boolean);
    await this.topicProgressRepository.setCovered(attemptId, added.flatMap((it) => leafIds(it)), true, now);
    await this.topicProgressRepository.setCovered(attemptId, removed.flatMap((it) => leafIds(it)), false, now);
  }

  async restoreFinishedChapters(attemptId, pack, attempted) {
    if (!pack) return;
    const chapters = new Map(pack.subjects.flatMap((it) => it.nodes).map((it) => [it.id, it]));
    const now = nowMs();
    const added = [...setMinus(attempted, this.initialCoveredChapterIds)].map((it) => chapters.get(it)).filter(Boolean);
    const removed = [...setMinus(this.initialCoveredChapterIds, attempted)].map((it) => chapters.get(it)).filter(Boolean);
    await this.topicProgressRepository.setCovered(attemptId, added.flatMap((it) => leafIds(it)), false, now);
    await this.topicProgressRepository.setCovered(attemptId, removed.flatMap((it) => leafIds(it)), true, now);
  }
}

// ---- CommitmentIssue / CapacityIssue (sealed) ----------------------------------------------------
/** Tagged: {type:'WakeAfterSleep', commitmentId:null} | {type:'NoDays'|'InvalidRange'|'OutsideWakingHours', commitmentId, labelRes} */
export const CommitmentIssue = Object.freeze({
  WakeAfterSleep: Object.freeze({ type: 'WakeAfterSleep', commitmentId: null }),
  NoDays: (commitmentId, labelRes) => Object.freeze({ type: 'NoDays', commitmentId, labelRes }),
  InvalidRange: (commitmentId, labelRes) => Object.freeze({ type: 'InvalidRange', commitmentId, labelRes }),
  OutsideWakingHours: (commitmentId, labelRes) => Object.freeze({ type: 'OutsideWakingHours', commitmentId, labelRes }),
});
/** Tagged: {type:'Weekday'|'Weekend', freeHours} */
export const CapacityIssue = Object.freeze({
  Weekday: (freeHours) => Object.freeze({ type: 'Weekday', freeHours }),
  Weekend: (freeHours) => Object.freeze({ type: 'Weekend', freeHours }),
});

export function capacityIssuesFor(state) {
  const out = [];
  const weekdayFree = Math.min(...[1, 2, 3, 4, 5].map((day) => freeMinutesOnDay(day, state))) / 60;
  const weekendFree = Math.min(...[0, 6].map((day) => freeMinutesOnDay(day, state))) / 60;
  if (state.weekdayHours > weekdayFree) out.push(CapacityIssue.Weekday(weekdayFree));
  if (state.weekendHours > weekendFree) out.push(CapacityIssue.Weekend(weekendFree));
  return out;
}

const halfHourStep = (x) => roundToInt(x * 2) / 2;

const preset = (kind, labelRes, start, end) =>
  OnboardingCommitment({ id: '', kind, labelRes, startMinute: start, endMinute: end, days: new Set([1, 2, 3, 4, 5, 6]) });
function commitmentPreset(kind) {
  switch (kind) {
    case 'school': return preset(kind, 'onboarding_commitment_school', 8 * 60, 14 * 60);
    case 'coaching': return preset(kind, 'onboarding_commitment_coaching', 16 * 60, 19 * 60);
    case 'lecture': return preset(kind, 'onboarding_commitment_lecture', 19 * 60, 21 * 60);
    case 'tuition': return preset(kind, 'onboarding_commitment_tuition', 18 * 60, 19 * 60 + 30);
    case 'work': return preset(kind, 'onboarding_commitment_job', 10 * 60, 18 * 60);
    case 'meal': return preset(kind, 'onboarding_commitment_meal', 13 * 60, 14 * 60);
    case 'commute': return preset(kind, 'onboarding_commitment_commute', 7 * 60 + 30, 8 * 60 + 30);
    case 'other': return preset(kind, 'onboarding_commitment_other', 17 * 60, 18 * 60);
    default: return null;
  }
}

function mergedOccupiedWindows(commitments) {
  const merged = [];
  [...commitments].sort((a, b) => a.startMinute - b.startMinute).forEach((c) => {
    const last = merged[merged.length - 1];
    if (!last || c.startMinute > last[1]) merged.push([c.startMinute, c.endMinute]);
    else merged[merged.length - 1] = [last[0], Math.max(last[1], c.endMinute)];
  });
  return merged;
}

export function freeMinutesOnDay(day, state) {
  const occupied = mergedOccupiedWindows(state.commitments.filter((it) => it.days.has(day)))
    .reduce((s, [start, end]) => s + (end - start), 0);
  return Math.max(state.sleepMinute - state.wakeMinute - occupied, 0);
}

export function commitmentIssuesFor(state) {
  const issues = [];
  if (state.sleepMinute <= state.wakeMinute) issues.push(CommitmentIssue.WakeAfterSleep);
  state.commitments.forEach((c) => {
    if (c.days.size === 0) issues.push(CommitmentIssue.NoDays(c.id, c.labelRes));
    if (c.endMinute <= c.startMinute) issues.push(CommitmentIssue.InvalidRange(c.id, c.labelRes));
    if (c.startMinute < state.wakeMinute || c.endMinute > state.sleepMinute) issues.push(CommitmentIssue.OutsideWakingHours(c.id, c.labelRes));
  });
  // issues.distinct()
  return issues.filter((it, i) => issues.findIndex((o) => deepEquals(o, it)) === i);
}

export function freeWindowsFor(attemptId, state) {
  return DayOfWeek.entries.flatMap((dayOfWeek) => {
    const prototypeDay = dayOfWeek.value % 7;
    const occupied = mergedOccupiedWindows(
      state.commitments
        .filter((it) => it.days.has(prototypeDay))
        .map((it) => it.copy({ startMinute: Math.max(it.startMinute, state.wakeMinute), endMinute: Math.min(it.endMinute, state.sleepMinute) }))
        .filter((it) => it.endMinute > it.startMinute),
    );
    const gaps = [];
    let cursor = state.wakeMinute;
    occupied.forEach(([start, end]) => {
      if (start > cursor) gaps.push([cursor, start]);
      cursor = Math.max(cursor, end);
    });
    if (cursor < state.sleepMinute) gaps.push([cursor, state.sleepMinute]);
    return gaps.map(([start, end], index) => WeeklyAvailability({
      id: `onboarding_${attemptId}_${dayOfWeek.name}_${index}`,
      attemptId,
      dayOfWeek,
      startMinuteOfDay: start,
      endMinuteOfDay: end,
    }));
  });
}

/** java.net.URLEncoder.encode(s, UTF-8): keeps a-zA-Z0-9 . - * _, space → '+', rest %XX. */
const urlEncode = (s) => encodeURIComponent(s).replace(/[!'()~]/g, (c) => '%' + c.charCodeAt(0).toString(16).toUpperCase()).replace(/%20/g, '+');
const urlDecode = (s) => decodeURIComponent(s.replace(/\+/g, ' '));

function encodeCommitment(c) {
  return [c.id, c.kind, c.startMinute, c.endMinute, [...c.days].sort((a, b) => a - b).join(','), urlEncode(c.customLabel ?? '')].join('|');
}

function decodeCommitment(encoded) {
  const parts = encoded.split('|');
  if (parts.length < 5) return null;
  const kind = parts[1];
  const label = commitmentPreset(kind)?.labelRes ?? 'onboarding_commitment_other';
  const start = toIntOrNull(parts[2]); if (start == null) return null;
  const end = toIntOrNull(parts[3]); if (end == null) return null;
  const days = new Set(parts[4].split(',').map(toIntOrNull).filter((it) => it != null && it >= 0 && it <= 6));
  let custom = null;
  if (parts[5] != null) { try { custom = urlDecode(parts[5]); } catch { custom = null; } }
  custom = custom?.trim() || null;
  return OnboardingCommitment({ id: parts[0], kind, labelRes: label, startMinute: start, endMinute: end, days, customLabel: custom });
}

function topLevelNodeIds(pack) {
  return new Set(pack ? pack.subjects.flatMap((it) => it.nodes).map((it) => it.id) : []);
}

function subjectsOf(pack) {
  return pack.subjects.map((subject) => OnboardingSyllabusSubject({
    id: subject.id,
    name: subject.name,
    chapters: subject.nodes.map((node) => OnboardingSyllabusChapter({
      id: node.id, name: node.title, classNumber: toIntOrNull(node.id.split('.')[2] ?? null),
    })),
  }));
}

function encodeDraft(state) {
  return JSON.stringify({
    step: state.step.name,
    coaching: state.coachingId,
    wake: state.wakeMinute,
    sleep: state.sleepMinute,
    date: toEpochDay(state.targetDate),
    exam: toEpochDay(state.examDate),
    mode: state.planMode.name,
    work: state.workId,
    weekday: state.weekdayHours,
    weekend: state.weekendHours,
    place: state.studyPlace,
    provided: state.useProvidedSyllabus,
    selected: [...state.selectedChapterIds],
    covered: [...state.coveredChapterIds],
    coveredAuto: state.coveredIsAutoFilled,
    start: state.startingPoint?.name ?? '',
    commitments: state.commitments.map(encodeCommitment),
  });
}

function decodeDraft(raw, base) {
  try {
    const json = JSON.parse(raw);
    const has = (k) => Object.prototype.hasOwnProperty.call(json, k) && json[k] != null;
    const optString = (k, d) => (has(k) ? String(json[k]) : d);
    const optInt = (k, d) => (has(k) && Number.isFinite(Number(json[k])) ? Math.trunc(Number(json[k])) : d);
    const optNum = (k, d) => (has(k) && Number.isFinite(Number(json[k])) ? Number(json[k]) : d);
    const optBool = (k, d) => (has(k) && typeof json[k] === 'boolean' ? json[k] : d);
    const strings = (name) => new Set(Array.isArray(json[name]) ? json[name].map((it) => (it == null ? '' : String(it))).filter((it) => it.trim() !== '') : []);
    const restoredCommitments = [...strings('commitments')].map(decodeCommitment).filter(Boolean);
    const selected = strings('selected');
    return base.copy({
      step: OnboardingStep.valueOfOrNull(optString('step', '')) ?? base.step,
      coachingId: optString('coaching', base.coachingId),
      wakeMinute: optInt('wake', base.wakeMinute),
      sleepMinute: optInt('sleep', base.sleepMinute),
      examDate: ofEpochDay(optInt('exam', toEpochDay(base.examDate))),
      planMode: PlanMode.valueOfOrNull(optString('mode', '')) ?? base.planMode,
      targetDate: ofEpochDay(optInt('date', toEpochDay(base.targetDate))),
      workId: optString('work', base.workId),
      weekdayHours: optNum('weekday', base.weekdayHours),
      weekendHours: optNum('weekend', base.weekendHours),
      studyPlace: optString('place', base.studyPlace),
      useProvidedSyllabus: optBool('provided', base.useProvidedSyllabus),
      selectedChapterIds: selected.size ? selected : base.selectedChapterIds,
      coveredChapterIds: Object.prototype.hasOwnProperty.call(json, 'covered') ? strings('covered') : base.coveredChapterIds,
      coveredIsAutoFilled: optBool('coveredAuto', false),
      startingPoint: StartingPoint.valueOfOrNull(optString('start', '')) ?? base.startingPoint,
      commitments: restoredCommitments.length ? restoredCommitments : base.commitments,
    });
  } catch {
    return base;
  }
}
