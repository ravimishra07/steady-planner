// Port of feature/settings/MoreRoute.kt: MoreUiState, ProRowState, MoreViewModel, MoreRoute.
// viewModelScope → subscriptions released in onCleared(); `launch {}` → un-awaited async calls.
import { MutableStateFlow, combine } from '../../platform/flow.js';
import { viewModel, observe, LaunchedEffect } from '../../platform/nav.js';
import { dataClass } from '../../domain/kt.js';
import { NEET_EXAM_ID } from '../../domain/ExamPack.js';
import { syllabusMetrics, targetStates } from '../../domain/Metrics.js';
import { ofEpochDay } from '../../domain/time.js';
import { ProStatus } from '../../data/ProRepository.js';
import { decodedCommitments } from '../../data/SavedPlan.js';
import { MoreScreen } from './MoreScreen.js';
import { stringResource, formatDMMMyyyy } from './_local.js';

/** Settings → Pro: status, buy, restore, and the debug-only override. */
export const ProRowState = dataClass({
  isPro: false,
  paywallEnabled: true,
  price: null,
  status: ProStatus.IDLE,
  debugForcePro: false,
  showDebugToggle: false,
});

export const MoreUiState = dataClass({
  loading: true,
  loadFailed: false,
  plan: null,
  coveragePercent: null,
  examDate: null,
  finishByDate: null,
  /** Live share of the day for coaching students (§5.4); null hides the row for everyone else. */
  liveBacklogSplit: null,
  pro: ProRowState(),
});

export class MoreViewModel {
  constructor(planStore, examPackRepository, attemptRepository, topicProgressRepository, targetSyllabusRepository,
    rollingPlanRepository, chapterStageRepository, proRepository = null, isDebugBuild = false) {
    this.planStore = planStore;
    this.examPackRepository = examPackRepository;
    this.attemptRepository = attemptRepository;
    this.topicProgressRepository = topicProgressRepository;
    this.targetSyllabusRepository = targetSyllabusRepository;
    this.rollingPlanRepository = rollingPlanRepository;
    this.chapterStageRepository = chapterStageRepository;
    this.proRepository = proRepository;
    this._state = new MutableStateFlow(MoreUiState({
      pro: ProRowState({ paywallEnabled: proRepository?.paywallEnabled ?? true, showDebugToggle: isDebugBuild }),
    }));
    this.state = this._state.asStateFlow();
    this._split = null;
    this._unsubs = [];
    const pro = proRepository;
    if (pro) {
      const row = combine([pro.isPro, pro.formattedPrice, pro.status, pro.debugForcePro],
        (isPro, price, status, force) => this._state.value.pro.copy({ isPro, price, status, debugForcePro: force }));
      this._unsubs.push(row.subscribe((r) => this._state.update((it) => it.copy({ pro: r }))));
      pro.loadProduct();
    }
  }

  /** Settings → Restore purchase. */
  restorePurchase() {
    const pro = this.proRepository; if (!pro) return;
    pro.refreshPurchases(true);
  }

  setDebugForcePro(enabled) {
    const pro = this.proRepository; if (!pro) return;
    pro.setDebugForcePro(enabled);
  }

  clearProStatus() { this.proRepository?.clearStatus(); }

  /** Settings → Plan: live/backlog split. Saving replans from today. */
  setLiveBacklogSplit(percent) {
    this._state.update((it) => it.copy({ liveBacklogSplit: percent }));
    this.rollingPlanRepository.setLiveBacklogSplit(percent);
  }

  refresh() {
    this._state.update((it) => it.copy({ loading: true, loadFailed: false }));
    (async () => {
      let result;
      try {
        const plan = await this.planStore.load();
        const active = await this.attemptRepository.activeAttempt();
        const attempt = active?.examId === NEET_EXAM_ID ? active : null;
        let coverage = null;
        if (plan?.examId === NEET_EXAM_ID && attempt != null) {
          const pack = await this.examPackRepository.examPackFor(NEET_EXAM_ID);
          const overall = syllabusMetrics(
            pack,
            targetStates(await this.targetSyllabusRepository.overridesFor(attempt.id)),
            await this.topicProgressRepository.allOnce(attempt.id),
          ).overall;
          coverage = overall.total > 0 ? overall.percent : null;
        }
        const usesCoaching = attempt != null && (
          (await this.chapterStageRepository.coachingFor(attempt.id)).length > 0 ||
          (await this.chapterStageRepository.batchPosition(attempt.id)) != null
        );
        this._split = usesCoaching ? await this.rollingPlanRepository.liveBacklogSplit() : null;
        result = { plan, coverage, attempt };
      } catch (e) {
        console.warn(e);
        this._state.update((it) => it.copy({ loading: false, loadFailed: true }));
        return;
      }
      const { plan, coverage, attempt } = result;
      this._state.update((it) => it.copy({
        loading: false,
        loadFailed: false,
        plan,
        coveragePercent: coverage,
        examDate: attempt?.examDate ?? null,
        finishByDate: attempt?.targetCompletionDate ?? null,
        liveBacklogSplit: this._split,
      }));
    })();
  }

  onCleared() { this._unsubs.forEach((u) => u()); this._unsubs = []; }
}

/**
 * MoreRoute({ planStore, examPackRepository, attemptRepository, topicProgressRepository, targetSyllabusRepository,
 *   rollingPlanRepository, chapterStageRepository, examDateOfficial, appVersion, background, onOpenAppearance,
 *   onOpenSettings, onRedoOnboarding, onOpenPolicy, modifier, proRepository, isDebugBuild, onOpenPaywall, onLogOut })
 */
export function MoreRoute(p) {
  const vm = p.viewModel || viewModel('MoreViewModel', () => new MoreViewModel(
    p.planStore, p.examPackRepository, p.attemptRepository, p.topicProgressRepository, p.targetSyllabusRepository,
    p.rollingPlanRepository, p.chapterStageRepository, p.proRepository ?? null, p.isDebugBuild ?? false,
  ));
  LaunchedEffect([], () => { vm.refresh(); });
  return observe(vm.state, (state) => {
    const plan = state.plan;
    const finishBy = state.finishByDate ?? (plan?.targetDateEpochDay != null ? ofEpochDay(plan.targetDateEpochDay) : null);
    return MoreScreen({
      loading: state.loading,
      loadFailed: state.loadFailed,
      planExamLabel: plan?.examId === NEET_EXAM_ID ? stringResource('settings_exam_neet') : null,
      examDateLabel: state.examDate != null ? formatDMMMyyyy(state.examDate) : null,
      examDateOfficial: p.examDateOfficial,
      finishByLabel: finishBy != null ? formatDMMMyyyy(finishBy) : null,
      weekdayHours: plan?.weekdayHours ?? null,
      weekendHours: plan?.weekendHours ?? null,
      fixedBlockCount: plan ? decodedCommitments(plan).length : 0,
      coveragePercent: state.coveragePercent,
      liveBacklogSplit: state.liveBacklogSplit,
      onLiveBacklogSplit: (v) => vm.setLiveBacklogSplit(v),
      appVersion: p.appVersion,
      background: p.background,
      onRetry: () => vm.refresh(),
      onOpenAppearance: p.onOpenAppearance,
      onOpenSettings: p.onOpenSettings,
      onRedoOnboarding: p.onRedoOnboarding,
      onOpenPolicy: p.onOpenPolicy,
      pro: state.pro,
      onOpenPaywall: p.onOpenPaywall ?? (() => {}),
      onRestorePurchase: () => vm.restorePurchase(),
      onDismissProStatus: () => vm.clearProStatus(),
      onDebugForcePro: (b) => vm.setDebugForcePro(b),
      onLogOut: p.onLogOut ?? (() => {}),
      modifier: p.modifier,
    });
  }, { class: 'col fill' });
}
