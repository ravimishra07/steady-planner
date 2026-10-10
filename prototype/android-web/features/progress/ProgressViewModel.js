// Port of feature/progress/ProgressViewModel.kt
// Insights holds no formulas: it loads raw state and hands it to computeInsights, built from Metrics.js.
// viewModelScope → async fns; the proRepository.unlocked collector is cancelled in onCleared().
import { MutableStateFlow } from '../../platform/flow.js';
import { Calibration } from '../../domain/Calibration.js';
import { InsightPeriod, InsightPlan, computeInsights } from '../../domain/StudyAnalytics.js';
import { countsAsStudy, syllabusMetrics, mostLostChapters, targetStates } from '../../domain/Metrics.js';
import { findNode } from '../../domain/ExamPack.js';
import { TargetNodeState } from '../../domain/TargetSyllabus.js';
import { currentMinuteOfDay } from '../../domain/StudySession.js';
import { today as localToday, minusDays, minDate, epochMsToLocalDate, systemZoneId, nowMs } from '../../domain/time.js';
import { InsightsUiState, isProPeriod, LostChapterUi, TargetSectionUi } from './ProgressUiState.js';

export class ProgressViewModel {
  constructor(
    examPackRepository, attemptRepository, planRepository, studySessionRepository, topicProgressRepository,
    targetSyllabusRepository, rollingPlanRepository, studyPreferenceRepository, revisionRepository,
    availabilityRepository, chapterStageRepository, mockTestRepository, proRepository,
  ) {
    Object.assign(this, {
      examPackRepository, attemptRepository, planRepository, studySessionRepository, topicProgressRepository,
      targetSyllabusRepository, rollingPlanRepository, studyPreferenceRepository, revisionRepository,
      availabilityRepository, chapterStageRepository, mockTestRepository, proRepository,
    });
    this._state = new MutableStateFlow(InsightsUiState());
    this.state = this._state.asStateFlow();

    this.pack = null;
    this.attemptId = null;
    this.cachedPlan = null;
    this.sessions = [];
    this.planBlocks = [];
    this.revisionStates = [];
    this.progress = [];
    this.targetOverrides = new Map();
    this.calibration = Calibration.NONE;
    this.fit = null;
    this.coaching = [];
    this.mistakes = [];

    this.refresh();
    this._unsubPro = proRepository.unlocked.subscribe((unlocked) => {
      const period = this._state.value.period;
      // Pro went away (refund, revoke): fall back to the free week rather than show a locked range.
      const keep = unlocked || !isProPeriod(period);
      this._state.update((it) => it.copy({ proUnlocked: unlocked, period: keep ? period : InsightPeriod.WEEK }));
      if (!keep) this.rebuild();
    });
  }

  get uiState() { return this.state; }

  onCleared() { this._unsubPro?.(); }

  async refresh() {
    try {
      const attempt = await this.attemptRepository.activeAttempt();
      if (attempt == null) {
        this._state.update((it) => it.copy({ loading: false, hasPlan: false, data: null, loadFailed: false }));
        return;
      }
      this.attemptId = attempt.id;
      const today = localToday();
      const preferences = await this.studyPreferenceRepository.forAttempt(attempt.id);
      this.pack = await this.examPackRepository.examPackFor(attempt.examId, attempt.id);
      const start = minDate((await this.studySessionRepository.earliestStudyDate(attempt.id)) ?? today, minusDays(today, 83));
      this.sessions = await this.studySessionRepository.between(attempt.id, start, today);
      this.planBlocks = await this.planRepository.planBetween(attempt.id, start, today);
      this.revisionStates = await this.revisionRepository.allFor(attempt.id);
      this.progress = await this.topicProgressRepository.allOnce(attempt.id);
      this.targetOverrides = targetStates(await this.targetSyllabusRepository.overridesFor(attempt.id));
      this.calibration = await this.rollingPlanRepository.calibration();
      this.fit = await this.rollingPlanRepository.planFit({ withOptions: false });
      this.coaching = await this.chapterStageRepository.coachingFor(attempt.id);
      this.mistakes = await this.mockTestRepository.mistakesForAttempt(attempt.id);
      this.cachedPlan = InsightPlan({
        startedOn: epochMsToLocalDate(attempt.createdAtEpochMs),
        targetDate: attempt.targetCompletionDate,
        weeklyAvailability: await this.availabilityRepository.weeklyFor(attempt.id),
        availabilityOverrides: await this.availabilityRepository.overridesFor(attempt.id),
        weekdayTargetMinutes: preferences?.weekdayTargetMinutes ?? (4 * 60),
        weekendTargetMinutes: preferences?.weekendTargetMinutes ?? (7 * 60),
        planMode: attempt.planMode,
      });
      this.rebuild();
      await this.maybeSoftPrompt();
    } catch (_) {
      this._state.update((it) => it.copy({ loading: false, loadFailed: true }));
    }
  }

  /** The one soft Pro prompt (§11.2), on Insights only. Marked shown before it is shown, so it never repeats. */
  async maybeSoftPrompt() {
    const activeDays = new Set(this.sessions.filter(countsAsStudy).map((it) => it.studyDate)).size;
    let show = false;
    try { show = await this.proRepository.shouldShowSoftPrompt(activeDays); } catch (_) { show = false; }
    if (show) {
      await this.proRepository.markSoftPromptShown();
      this._state.update((it) => it.copy({ showSoftPrompt: true }));
    }
  }

  onSoftPromptShown() { this._state.update((it) => it.copy({ showSoftPrompt: false })); }

  selectPeriod(period) {
    if (period === this._state.value.period) return;
    if (isProPeriod(period) && !this._state.value.proUnlocked) return;
    this._state.update((it) => it.copy({ period }));
    this.rebuild();
  }

  openTargetManager() { this._state.update((it) => it.copy({ showTargetManager: true })); }
  closeTargetManager() { this._state.update((it) => it.copy({ showTargetManager: false })); }

  async toggleSectionTarget(key) {
    const id = this.attemptId; if (id == null) return;
    const subject = this.pack?.subjects?.find((it) => it.id === key); if (subject == null) return;
    const shouldExclude = subject.nodes.some((it) => this.targetOverrides.get(it.id) !== TargetNodeState.EXCLUDED);
    const now = nowMs();
    for (const node of subject.nodes) {
      if (shouldExclude) await this.targetSyllabusRepository.setState(id, node.id, TargetNodeState.EXCLUDED, now);
      else await this.targetSyllabusRepository.clearOverride(id, node.id);
    }
    const next = new Map(this.targetOverrides);
    for (const node of subject.nodes) {
      if (shouldExclude) next.set(node.id, TargetNodeState.EXCLUDED); else next.delete(node.id);
    }
    this.targetOverrides = next;
    await this.rollingPlanRepository.replenish();
    this.rebuild();
  }

  rebuild() {
    const plan = this.cachedPlan; if (plan == null) return;
    const currentPack = this.pack; if (currentPack == null) return;
    const data = computeInsights({
      pack: currentPack,
      syllabus: syllabusMetrics(currentPack, this.targetOverrides, this.progress, this.calibration.factor),
      sessions: this.sessions,
      planBlocks: this.planBlocks,
      revisionStates: this.revisionStates,
      plan,
      period: this._state.value.period,
      today: localToday(),
      nowMinuteOfDay: currentMinuteOfDay(),
      calibration: this.calibration,
      fit: this.fit,
      progress: this.progress,
      coaching: this.coaching,
      zone: systemZoneId(),
    });
    this._state.update((it) => it.copy({
      loading: false,
      hasPlan: true,
      data,
      targetSections: currentPack.subjects.map((subject) => TargetSectionUi({
        key: subject.id,
        name: subject.name,
        excluded: subject.nodes.length > 0 && subject.nodes.every((node) => this.targetOverrides.get(node.id) === TargetNodeState.EXCLUDED),
      })),
      loadFailed: false,
      mostLost: mostLostChapters(this.mistakes).map((lost) =>
        LostChapterUi({ title: findNode(currentPack, lost.chapterId)?.title ?? lost.chapterId, count: lost.count })),
    }));
  }
}

