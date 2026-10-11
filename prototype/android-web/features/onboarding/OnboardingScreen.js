// Port of feature/onboarding/OnboardingScreen.kt — OnboardingRoute, OnboardingScreen, CapacityIssuesCard.
import { combine } from '../../platform/flow.js';
import { viewModel, observe, BackHandler, onDispose, remember } from '../../platform/nav.js';
import { Box, Column, Text, Surface, TextButton, AlertDialog, CircularProgressIndicator, C } from '../../design/components.js';
import { appColorsFlow } from '../../design/colors.js';
import { Analytics } from '../../data/common.js';
import { stringResource } from './res/values/strings.js';
import { OnboardingViewModel } from './OnboardingViewModel.js';
import { OnboardingShell } from './components/OnboardingShell.js';
import { OnboardingStep } from './model/OnboardingStep.js';
import {
  OnboardingAccentPicker, OnboardingAppearanceStep, OnboardingCoachingStep, OnboardingCommitmentsStep, OnboardingHoursParityStep,
  OnboardingModeStep, OnboardingPlanStep, OnboardingStartStep, OnboardingSyllabusStep,
} from './steps/OnboardingParitySteps.js';
import { SavedStateHandle, createLocalStore, withLocal } from './_local.js';

/**
 * OnboardingRoute({ planStore, examPackRepository, attemptRepository, availabilityRepository, studyPreferenceRepository,
 *   targetSyllabusRepository, topicProgressRepository, planRepository, rollingPlanRepository, dispatchers, examDate,
 *   examDateOfficial, startAtPlan, background, onBackground, accentPalette, onAccentPalette, onFinished, modifier?, analytics? })
 */
export function OnboardingRoute(p) {
  const vm = p.viewModel ?? viewModel('OnboardingViewModel', () => new OnboardingViewModel(
    p.planStore, p.examPackRepository, p.attemptRepository, p.availabilityRepository, p.studyPreferenceRepository,
    p.targetSyllabusRepository, p.topicProgressRepository, p.planRepository, p.rollingPlanRepository, p.dispatchers,
    new SavedStateHandle(), p.examDate, p.examDateOfficial, p.startAtPlan, p.analytics ?? Analytics.NoOp,
  ));

  // LaunchedEffect(finished) { if (finished) onFinished(openOrganiseAfterFinish) }
  const unsubFinished = vm.finished.subscribe((finished) => {
    if (finished) queueMicrotask(() => p.onFinished(vm.openOrganiseAfterFinish.value));
  });
  onDispose(unsubFinished);

  // BackHandler(enabled = state.canGoBack, onBack = viewModel::back)
  const back = BackHandler(vm.state.value.canGoBack, () => vm.back());
  const unsubBack = vm.state.subscribe((s) => back.setEnabled(s.canGoBack));
  onDispose(unsubBack);

  const local = remember('ob:local', () => createLocalStore()).value;
  const flow = combine([vm.state, local.tick, appColorsFlow], (state, tick, colors) => ({ state, tick, colors }));
  return observe(flow, ({ state }) => withLocal(local, () => OnboardingScreen({
    state,
    background: p.background,
    accentPalette: p.accentPalette,
    canContinue: vm.canContinue(state),
    commitmentIssues: vm.commitmentIssues(state),
    capacityIssues: vm.capacityIssues(state),
    onBack: () => vm.back(),
    onContinue: () => vm.continueFromCurrent(),
    onSkip: vm.canSkip(state) ? () => vm.skipCurrent() : null,
    onBackground: p.onBackground,
    onAccentPalette: p.onAccentPalette,
    onSelectCoaching: (id) => vm.selectCoaching(id),
    onAddCommitment: (kind) => vm.addCommitment(kind),
    onRemoveCommitment: (id) => vm.removeCommitment(id),
    onRenameCommitment: (id, label) => vm.renameCommitment(id, label),
    onToggleCommitmentDay: (id, day) => vm.toggleCommitmentDay(id, day),
    onCommitmentStart: (id, m) => vm.setCommitmentStart(id, m),
    onCommitmentEnd: (id, m) => vm.setCommitmentEnd(id, m),
    onWake: (m) => vm.setWakeMinute(m),
    onSleep: (m) => vm.setSleepMinute(m),
    onDate: (d) => vm.setTargetDate(d),
    onMode: (m) => vm.setPlanMode(m),
    onExamDate: (d) => vm.setExamDate(d),
    onSelectWork: (id) => vm.selectWork(id),
    onWeekdayChange: (h) => vm.setWeekdayHours(h),
    onWeekendChange: (h) => vm.setWeekendHours(h),
    onToggleTargetSubject: (id) => vm.toggleTargetSubject(id),
    onToggleTargetChapter: (id) => vm.toggleTargetChapter(id),
    onSelectStartingPoint: (sp) => vm.selectStartingPoint(sp),
    onToggleFinishedSubject: (id) => vm.toggleFinishedSubject(id),
    onToggleFinishedChapter: (id) => vm.toggleFinishedChapter(id),
    onToggleFinishedClass: (n) => vm.toggleFinishedClass(n),
    onApplyFitOption: (o) => vm.applyFitOption(o),
    onDismissFinishError: () => vm.dismissFinishError(),
    modifier: p.modifier,
  })), { class: 'fill', style: { display: 'flex', flexDirection: 'column' } });
}

const TITLES = {
  Appearance: 'onboarding_appearance_title', Coaching: 'onboarding_coaching_title', Commitments: 'onboarding_commitments_title',
  Date: 'onboarding_date_title', Hours: 'onboarding_hours_title', Start: 'onboarding_start_title', Plan: 'onboarding_plan_title',
};

export function OnboardingScreen(p) {
  const { state } = p;
  const m = p.modifier || {};
  if (state.loading) {
    return Box({ class: ['fill', m.class], css: m.css, contentAlignment: 'Center' }, CircularProgressIndicator());
  }
  const title = stringResource(state.step === OnboardingStep.Syllabus
    ? (state.useProvidedSyllabus ? 'onboarding_syllabus_title' : 'onboarding_syllabus_custom_title')
    : TITLES[state.step.name]);
  const cta = stringResource(
    state.step === OnboardingStep.Syllabus ? 'onboarding_build_plan'
      : state.step === OnboardingStep.Plan ? (state.useProvidedSyllabus ? 'onboarding_start_day_one' : 'onboarding_plan_add_chapters')
        : 'onboarding_continue',
  );

  let content;
  switch (state.step) {
    case OnboardingStep.Appearance: content = OnboardingAppearanceStep(p.background, p.accentPalette, p.onBackground); break;
    case OnboardingStep.Coaching: content = OnboardingCoachingStep(state.coachingId, p.onSelectCoaching); break;
    case OnboardingStep.Commitments: content = OnboardingCommitmentsStep(
      state, p.commitmentIssues, p.onAddCommitment, p.onRemoveCommitment, p.onRenameCommitment, p.onToggleCommitmentDay,
      p.onCommitmentStart, p.onCommitmentEnd, p.onWake, p.onSleep,
    ); break;
    case OnboardingStep.Date: content = OnboardingModeStep(state, p.onMode, p.onDate, p.onExamDate); break;
    case OnboardingStep.Hours: content = OnboardingHoursParityStep({
      state, capacityIssues: p.capacityIssues, onSelectWork: p.onSelectWork, onWeekdayChange: p.onWeekdayChange, onWeekendChange: p.onWeekendChange,
    }); break;
    case OnboardingStep.Start: content = OnboardingStartStep({ state, onSelectStartingPoint: p.onSelectStartingPoint }); break;
    case OnboardingStep.Syllabus: content = OnboardingSyllabusStep({ state, onToggleSubject: p.onToggleTargetSubject, onToggleChapter: p.onToggleTargetChapter }); break;
    default: content = OnboardingPlanStep({
      state, onWeekdayChange: p.onWeekdayChange, onWeekendChange: p.onWeekendChange, onDate: p.onDate, onExamDate: p.onExamDate, onApplyFitOption: p.onApplyFitOption,
    });
  }

  const shell = OnboardingShell({
    title,
    progressIndex: state.step.progressIndex,
    canGoBack: state.canGoBack,
    ctaLabel: cta,
    continueEnabled: p.canContinue,
    onBack: p.onBack,
    onContinue: p.onContinue,
    onSkip: p.onSkip,
    modifier: m,
    bottomContent: state.step === OnboardingStep.Appearance ? () => OnboardingAccentPicker(p.accentPalette, p.onAccentPalette) : null,
    content,
  });

  if (!state.finishFailed) return shell;
  return [shell, AlertDialog({
    onDismissRequest: p.onDismissFinishError,
    confirmButton: TextButton({ onClick: p.onDismissFinishError, children: Text({ text: stringResource('onboarding_dismiss') }) }),
    text: Text({ text: stringResource('onboarding_finish_failed') }),
  })];
}

export function CapacityIssuesCard(issues) {
  return Surface({
    css: { marginTop: 'var(--space-md)', alignSelf: 'flex-start' },
    shape: 'var(--radius-md)',
    color: C.dangerContainer,
    children: Column({ css: { padding: 'var(--space-lg)' } },
      Text({ text: stringResource('onboarding_hours_do_not_fit'), style: 'titleSmall' }),
      issues.map((issue) => Text({
        text: issue.type === 'Weekday'
          ? stringResource('onboarding_hours_weekday_free', formatHours(issue.freeHours))
          : stringResource('onboarding_hours_weekend_free', formatHours(issue.freeHours)),
      })),
      Text({ text: stringResource('onboarding_hours_fix_hint') })),
  });
}

const formatHours = (value) => (value === Math.trunc(value) ? String(Math.trunc(value)) : value.toFixed(1));
