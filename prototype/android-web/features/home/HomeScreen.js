// Port of feature/home HomeScreen.kt (HomeRoute + HomeScreen).
// HomeRoute re-renders on the ViewModel state and on any remembered local state (rememberMutable in _local.js).
import { combine } from '../../platform/flow.js';
import { viewModel, observe, LaunchedEffect, onDispose } from '../../platform/nav.js';
import { Box } from '../../design/components.js';
import { HomeViewModel } from './HomeViewModel.js';
import { HomeStudyContent } from './HomeStudyContent.js';
import { HomeEmptyContent } from './HomeEmptyContent.js';
import { recomposeTick, beginComposition, endComposition } from './_local.js';

export function HomeRoute({
  examPackRepository, attemptRepository, availabilityRepository, planRepository, studySessionRepository,
  topicProgressRepository, studyPreferenceRepository, revisionRepository, rollingPlanRepository,
  targetSyllabusRepository, chapterStageRepository,
  onSetupPlan, onAdjustPlan, onEditPlan, onOpenDay, onStartFocus, pendingSyllabusPick, onConsumedSyllabusPick, modifier,
}) {
  const vm = viewModel('HomeViewModel', () => HomeViewModel.Factory(
    examPackRepository, attemptRepository, availabilityRepository, planRepository, studySessionRepository,
    topicProgressRepository, studyPreferenceRepository, revisionRepository, rollingPlanRepository,
    targetSyllabusRepository, chapterStageRepository,
  ).create());

  LaunchedEffect([], () => { vm.refresh(); });
  LaunchedEffect(['focusRequests'], () => vm.focusRequests.collect((session) => { onStartFocus(session); }));

  // LaunchedEffect(pendingPick) { pendingPick?.let { viewModel.pickTopic(...); onConsumedSyllabusPick() } }
  const unsub = pendingSyllabusPick.subscribe((pick) => {
    if (!pick) return;
    queueMicrotask(() => {
      if (pendingSyllabusPick.value !== pick) return;
      vm.pickTopic(pick.nodeKey, pick.title, pick.sectionName, pick.subjectId, pick.topicPath);
      onConsumedSyllabusPick();
    });
  });
  onDispose(unsub);

  const tick = recomposeTick();
  return observe(combine([vm.state, tick], (state, t) => ({ state, t })), ({ state }) => {
    beginComposition();
    const node = HomeScreen({
      state,
      onSetupPlan,
      onAdjustPlan,
      onRebalanceMissed: () => vm.rebalanceMissed(),
      onStartFresh: () => vm.startFresh(),
      onDismissMissed: () => vm.dismissMissedBlocks(),
      onLogClass: (chapterId, lecture) => vm.logClassToday(chapterId, lecture),
      onNextLecture: () => vm.nextLectureToday(),
      onEditPlan,
      onOpenDay,
      onSelectDate: (date) => vm.selectDate(date),
      onToggleCalendarExpanded: () => vm.toggleCalendarExpanded(),
      onOpenAdd: () => vm.openAddStudy(),
      onDismissSheet: () => vm.dismissSheet(),
      onBackInStudyPicker: () => vm.backInStudyPicker(),
      onSelectPickerSection: (i) => vm.selectPickerSection(i),
      onOpenPickerSubtopics: (path) => vm.openPickerSubtopics(path),
      onSetPickerQuery: (q) => vm.setPickerQuery(q),
      onPickTopic: (nodeKey, title, sectionName, subjectId, topicPath) => vm.pickTopic(nodeKey, title, sectionName, subjectId, topicPath),
      onSetDuration: (m) => vm.setDurationMinutes(m),
      onSetActivityType: (t) => vm.setActivityType(t),
      onSetScheduledMinute: (m) => vm.setScheduledEndMinuteOfDay(m),
      onConfirmStart: () => vm.confirmStartSprint(),
      onConfirmAddToPlan: () => vm.confirmAddToPlan(),
      onStartScheduledSession: (id) => vm.startScheduledSession(id),
      onStartMissedSession: (id) => vm.startMissedSessionNow(id),
      onReplanRestOfToday: () => vm.replanRestOfToday(),
      onOpenAddInGap: (s, e) => vm.openAddStudyInGap(s, e),
      onRescheduleToNextSlot: (id) => vm.rescheduleToNextSlot(id),
      onRescheduleToTomorrow: (id) => vm.rescheduleToTomorrowSameTime(id),
      onRescheduleToTime: (id, minute) => vm.rescheduleToTime(id, minute),
      onSkipSession: (id) => vm.skipSession(id),
      modifier,
    });
    endComposition();
    return node;
  }, { style: { height: '100%', position: 'relative' } });
}

/** HomeScreen({ state, on…, modifier }) */
export function HomeScreen(p) {
  const { state } = p;
  const root = Box({ modifier: p.modifier, class: 'fill', css: { height: '100%', boxSizing: 'border-box', alignItems: 'stretch', justifyItems: 'stretch' } });
  if (state.loading) return root;
  if (state.hasPlan) {
    root.append(HomeStudyContent({
      state,
      onAdjustPlan: p.onAdjustPlan,
      onRebalanceMissed: p.onRebalanceMissed,
      onStartFresh: p.onStartFresh,
      onDismissMissed: p.onDismissMissed,
      onLogClass: p.onLogClass,
      onNextLecture: p.onNextLecture,
      onEditPlan: p.onEditPlan,
      onOpenDay: p.onOpenDay,
      onSelectDate: p.onSelectDate,
      onToggleCalendarExpanded: p.onToggleCalendarExpanded,
      onOpenAdd: p.onOpenAdd,
      onDismissSheet: p.onDismissSheet,
      onBackInStudyPicker: p.onBackInStudyPicker,
      onSelectPickerSection: p.onSelectPickerSection,
      onOpenPickerSubtopics: p.onOpenPickerSubtopics,
      onSetPickerQuery: p.onSetPickerQuery,
      onPickTopic: p.onPickTopic,
      onSetDuration: p.onSetDuration,
      onSetActivityType: p.onSetActivityType,
      onSetScheduledMinute: p.onSetScheduledMinute,
      onConfirmStart: p.onConfirmStart,
      onConfirmAddToPlan: p.onConfirmAddToPlan,
      onStartScheduledSession: p.onStartScheduledSession,
      onStartMissedSession: p.onStartMissedSession,
      onReplanRestOfToday: p.onReplanRestOfToday,
      onOpenAddInGap: p.onOpenAddInGap,
      onRescheduleToNextSlot: p.onRescheduleToNextSlot,
      onRescheduleToTomorrow: p.onRescheduleToTomorrow,
      onRescheduleToTime: p.onRescheduleToTime,
      onSkipSession: p.onSkipSession,
    }));
  } else {
    root.append(HomeEmptyContent({ onSetupPlan: p.onSetupPlan }));
  }
  return root;
}
