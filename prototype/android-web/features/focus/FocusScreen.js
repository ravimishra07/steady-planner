// Port of feature/focus/FocusScreen.kt (FocusRoute, FocusScreen, FocusIdleScreen, FocusQueueRow, TagChip)
// plus feature/focus/res/values/strings.xml as R.
//
// Web stand-ins:
//  - LifecycleEventEffect(ON_RESUME) → refresh() once on entry and on every document visibilitychange → visible.
//  - POST_NOTIFICATIONS: the web has no runtime permission. Behaves like API 33+ with the permission not granted
//    and the system dialog answered at once: first start marks the prompt shown, then starts the session.
//  - remainingSeconds: FocusScreen takes the Int (static) or the VM's remainingSeconds StateFlow. With the flow only
//    the dial (arc + clock text) is patched on a tick; the screen never re-renders for it (ARCHITECTURE §7).
import { combine } from '../../platform/flow.js';
import { viewModel, observe, remember, LaunchedEffect, onDispose } from '../../platform/nav.js';
import {
  C, alpha, Column, Row, Box, Text, Icon, IconButton, Button, FilledTonalButton, TextButton, FilterChip, Surface,
  CircularProgressIndicator, AlertDialog, OutlinedTextField, LazyColumn, HorizontalDivider, ButtonDefaults,
  BorderStroke, CircleShape, LengthPicker, LengthPickerLabels,
} from '../../design/components.js';
import { formatAndroid } from '../../data/common.js';
import {
  FocusStatus, FOCUS_LENGTH_MAX_MINUTES, FOCUS_LENGTH_MIN_MINUTES, FOCUS_LENGTH_PRESETS_MINUTES,
  focusLengthMinutesOrNull, formatFocusClock,
} from '../../domain/FocusSession.js';
import { StudyOutcome } from '../../domain/StudyHistory.js';
import { BlockTag } from '../../domain/TodaySchedule.js';
import { FocusViewModel } from './FocusViewModel.js';
import { FocusQueueItem } from './FocusUiState.js';
import { DeterminateCircularProgress } from './_local.js';

// feature/focus/src/main/res/values/strings.xml
const R = {
  string: {
    focus_title: 'Focus',
    focus_running: 'Focus · running',
    focus_paused: 'Focus · paused',
    focus_complete: 'Focus · complete',
    focus_close: 'Close',
    focus_of_minutes: 'of %1$d min',
    focus_logged_minutes: '%1$d min logged',
    focus_done: 'Done',
    focus_start_session: 'Start %1$d-minute session',
    focus_pause: 'Pause',
    focus_resume: 'Resume',
    focus_add_five: '+5 min',
    focus_end: 'End',
    focus_start_another: 'Start another',
    focus_outcome_prompt: 'How did this topic feel?',
    focus_outcome_struggled: 'Struggled',
    focus_outcome_okay: 'Okay',
    focus_outcome_strong: 'Strong',
    focus_back_today: 'Back to today',
    focus_empty_title: 'Nothing left today',
    focus_empty_body: 'Every block is checked off. Start one anyway if you want extra time.',
    focus_stop_title: 'End this session?',
    focus_stop_confirm: 'Save the time you studied and keep this topic unfinished, or discard the session.',
    focus_stop_save: 'Save & end',
    focus_stop_discard: 'Discard session',
    focus_stop_keep_going: 'Keep studying',
    focus_tag_learn: 'Learn',
    focus_tag_practice: 'Practice',
    focus_tag_revise: 'Revise',
    focus_back: 'Back',
    focus_search_topics: 'Search today\'s topics',
    focus_browse_topics: 'Browse topics',
    focus_no_topics_match: 'Nothing matches “%1$s”.',
    focus_up_next: 'up next',
    focus_start_minutes: 'Start %1$d min',
    focus_pick_topic: 'Pick a topic',
    focus_learned_prompt: 'Mark chapter learned?',
    focus_learned_yes: 'Yes',
    focus_learned_not_yet: 'Not yet',
    focus_length: 'Session length',
    focus_length_minutes: '%1$d min',
    focus_length_custom: 'Custom',
    focus_length_custom_title: 'Custom session length',
    focus_length_custom_field: 'Minutes (%1$d–%2$d)',
    focus_length_ok: 'OK',
    focus_length_cancel: 'Cancel',
  },
  plurals: {
    focus_left_today: { one: '%1$d left today', other: '%1$d left today' },
  },
};
const stringResource = (id, ...args) => (args.length ? formatAndroid(R.string[id], ...args) : R.string[id]);
const pluralStringResource = (id, quantity, ...args) => formatAndroid(R.plurals[id][quantity === 1 ? 'one' : 'other'], ...args);

/** Modifier chain `modifier.fillMaxSize().padding(…)`: caller modifier outside, own padding inside. */
function withModifier(modifier, inner) {
  return Box({ class: 'fill', modifier }, inner);
}

export function FocusRoute({
  focusController,
  settingsStore,
  examPackRepository,
  attemptRepository,
  planRepository,
  studySessionRepository,
  rollingPlanRepository,
  studyPreferenceRepository,
  topicProgressRepository,
  onClose,
  modifier,
}) {
  const vm = viewModel('FocusViewModel', () => new FocusViewModel.Factory(
    focusController,
    settingsStore,
    examPackRepository,
    attemptRepository,
    planRepository,
    studySessionRepository,
    rollingPlanRepository,
    studyPreferenceRepository,
    topicProgressRepository,
  ).create());
  // Coming back to the app re-reads the clock from ends_at.
  LaunchedEffect([], () => {
    vm.refresh();
    const onVisible = () => { if (document.visibilityState === 'visible') vm.refresh(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  });

  // Asked once, at the first Focus start. The timer starts whatever the answer is.
  const onStart = () => {
    const needsAsk = vm.state.value.askNotificationPermission;
    if (needsAsk) {
      vm.markNotificationPromptShown();
      vm.startSession(); // permission launcher result
    } else {
      vm.startSession();
    }
  };

  return observe(vm.state, (state) => FocusScreen({
    state,
    remainingSeconds: vm.remainingSeconds,
    onClose,
    onStart,
    onPause: () => vm.pause(),
    onResume: () => vm.resume(),
    onExtend: () => vm.extendFiveMinutes(),
    onSelectQueueItem: (id) => vm.selectQueueItem(id),
    onDuration: (m) => vm.setDuration(m),
    onRequestStop: () => vm.requestStop(),
    onSavePartialAndStop: () => vm.savePartialAndStop(),
    onDiscardAndStop: () => vm.discardAndStop(),
    onDismissStop: () => vm.dismissStopDialog(),
    onStartAnother: () => vm.startAnother(),
    onOutcome: (o) => vm.recordOutcome(o),
    onMarkLearned: () => vm.markChapterLearned(),
    onDismissLearnPrompt: () => vm.dismissLearnPrompt(),
    onBackToday: onClose,
    modifier,
  }), { class: 'fill' });
}

export function FocusScreen({
  state,
  remainingSeconds,
  onClose,
  onStart,
  onPause,
  onResume,
  onExtend,
  onSelectQueueItem,
  onRequestStop,
  onDuration,
  onSavePartialAndStop,
  onDiscardAndStop,
  onDismissStop,
  onStartAnother,
  onOutcome,
  onMarkLearned,
  onDismissLearnPrompt,
  onBackToday,
  modifier,
}) {
  if (state.loading) {
    return Box({ class: 'fill', modifier, contentAlignment: 'Center' },
      CircularProgressIndicator({ color: C.brandSoft }));
  }

  const out = [];
  if (state.showStopDialog) {
    out.push(AlertDialog({
      key: 'focus-stop',
      onDismissRequest: onDismissStop,
      title: Text(stringResource('focus_stop_title')),
      text: Column({},
        Text(stringResource('focus_stop_confirm')),
        TextButton({
          onClick: onDiscardAndStop,
          css: { alignSelf: 'flex-end' },
          children: Text({ text: stringResource('focus_stop_discard'), color: C.danger }),
        })),
      confirmButton: TextButton({ onClick: onSavePartialAndStop, children: Text(stringResource('focus_stop_save')) }),
      dismissButton: TextButton({ onClick: onDismissStop, children: Text(stringResource('focus_stop_keep_going')) }),
    }));
  }

  if (state.status === FocusStatus.IDLE) {
    out.push(FocusIdleScreen({ state, onStart, onSelectQueueItem, onDuration, modifier }));
    return out;
  }
  // FocusIdleScreen left the composition: its remember {} state is gone.
  resetIdleLocalState();

  const header = Row({
    class: 'fill-w',
    css: { paddingTop: 'var(--space-sm)', paddingBottom: 'var(--space-lg)' },
    horizontalArrangement: 'SpaceBetween',
    verticalAlignment: 'CenterVertically',
  },
  Text({
    text: state.status === FocusStatus.RUNNING ? stringResource('focus_running')
      : state.status === FocusStatus.PAUSED ? stringResource('focus_paused')
        : state.status === FocusStatus.DONE ? stringResource('focus_complete')
          : stringResource('focus_title'),
    style: 'titleMedium',
    color: C.text,
  }),
  IconButton({
    onClick: onClose,
    css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
    children: Icon({ imageVector: 'Filled.Close', contentDescription: stringResource('focus_close') }),
  }));

  const blockInfo = Column({ class: 'fill-w', css: { paddingBottom: 'var(--space-lg)' } },
    ...(state.hasBlock ? [
      state.blockTag != null ? TagChip({ tag: state.blockTag }) : null,
      Text({ text: state.blockTitle, style: 'titleLarge', color: C.text, css: { paddingTop: 'var(--space-sm)' } }),
      state.blockSubtitle.trim() !== ''
        ? Text({ text: state.blockSubtitle, style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-xs)' } })
        : null,
    ] : [
      Text({ text: stringResource('focus_empty_title'), style: 'titleLarge', color: C.text }),
      Text({ text: stringResource('focus_empty_body'), style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-xs)' } }),
    ]).filter(Boolean));

  const dial = Box({ class: ['weight', 'fill-w'], contentAlignment: 'Center' }, FocusDial({ state, remainingSeconds }));

  let controls = null;
  if (state.status === FocusStatus.RUNNING || state.status === FocusStatus.PAUSED) {
    const running = state.status === FocusStatus.RUNNING;
    controls = Row({
      class: 'fill-w',
      css: { paddingBottom: 'var(--space-lg)' },
      horizontalArrangement: { spacedBy: 'var(--space-sm)' },
    },
    FilledTonalButton({
      onClick: onExtend,
      class: 'weight', css: weightCss,
      children: [Icon({ imageVector: 'Filled.MoreTime' }), Text(stringResource('focus_add_five'))],
    }),
    Button({
      onClick: running ? onPause : onResume,
      class: 'weight', css: weightCss,
      colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
      children: Icon({
        imageVector: running ? 'Filled.Pause' : 'Filled.PlayArrow',
        contentDescription: running ? stringResource('focus_pause') : stringResource('focus_resume'),
      }),
    }),
    FilledTonalButton({
      onClick: onRequestStop,
      class: 'weight', css: weightCss,
      children: [Icon({ imageVector: 'Filled.Stop' }), Text(stringResource('focus_end'))],
    }));
  } else if (state.status === FocusStatus.DONE) {
    const prompt = state.learnPrompt;
    controls = Column({
      class: 'fill-w',
      css: { paddingBottom: 'var(--space-lg)' },
      verticalArrangement: { spacedBy: 'var(--space-sm)' },
    },
    ...(prompt ? [
      Text({ text: stringResource('focus_learned_prompt'), style: 'titleSmall' }),
      Text({ text: prompt.chapterTitle, style: 'bodyMedium', color: C.textSecondary }),
      Row({ class: 'fill-w', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        FilledTonalButton({ onClick: onMarkLearned, class: 'weight', css: weightCss, children: Text(stringResource('focus_learned_yes')) }),
        TextButton({ onClick: onDismissLearnPrompt, class: 'weight', css: weightCss, children: Text(stringResource('focus_learned_not_yet')) })),
    ] : []),
    Text({ text: stringResource('focus_outcome_prompt'), style: 'titleSmall' }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      ...[
        [StudyOutcome.STRUGGLED, 'focus_outcome_struggled'],
        [StudyOutcome.OKAY, 'focus_outcome_okay'],
        [StudyOutcome.STRONG, 'focus_outcome_strong'],
      ].map(([outcome, label]) => FilterChip({
        selected: state.outcome === outcome,
        onClick: () => onOutcome(outcome),
        label: Text(stringResource(label)),
      }))),
    Button({
      onClick: onStartAnother,
      class: 'fill-w',
      shape: 'var(--radius-lg)',
      colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
      children: Text(stringResource('focus_start_another')),
    }),
    TextButton({
      onClick: onBackToday,
      class: 'fill-w',
      children: Text({ text: stringResource('focus_back_today'), textAlign: 'Center' }),
    }));
  } else {
    // FocusStatus.IDLE (unreachable here: the idle screen returned above, as in Kotlin)
    controls = Button({
      onClick: onStart,
      class: 'fill-w',
      css: { paddingBottom: 'var(--space-lg)' },
      shape: 'var(--radius-lg)',
      colors: ButtonDefaults.buttonColors({ containerColor: C.brandDeep, contentColor: C.onBrand }),
      children: Text(stringResource('focus_start_session', state.durationMinutes)),
    });
  }

  out.push(withModifier(modifier, Column({ class: 'fill', css: { paddingInline: 'var(--space-screen)' } },
    header, blockInfo, dial, controls)));
  return out;
}

/**
 * The dial: CircularProgressIndicator(focusDial, summaryTrack stroke) + clock. The only part that reads
 * remainingSeconds; a tick patches the arc and the clock text in place.
 */
function FocusDial({ state, remainingSeconds }) {
  const isFlow = remainingSeconds != null && typeof remainingSeconds.subscribe === 'function';
  const progressOf = (remaining) => {
    if (state.status === FocusStatus.IDLE) return 0;
    if (state.status === FocusStatus.DONE) return 1;
    const total = Math.max(1, state.durationMinutes * 60);
    return Math.min(1, Math.max(0, (total - remaining) / total));
  };
  const clockOf = (remaining) => (state.status === FocusStatus.DONE ? stringResource('focus_done') : formatFocusClock(remaining));
  const arcColor = state.status === FocusStatus.PAUSED ? C.warning : state.status === FocusStatus.DONE ? C.success : C.brandSoft;
  const initial = isFlow ? remainingSeconds.value : remainingSeconds;

  const arc = DeterminateCircularProgress({
    progress: progressOf(initial),
    css: { width: 'var(--size-focusDial)', height: 'var(--size-focusDial)' },
    color: arcColor,
    trackColor: alpha(C.text, 0.12),
    strokeWidth: 'var(--size-summaryTrack)',
  });
  arc.setAttribute('data-testid', 'focus-dial');
  const clock = Text({ text: clockOf(initial), style: 'displayMedium', fontWeight: 'SemiBold', color: C.text, testTag: 'focus-clock' });
  const sub = Text({
    text: state.status === FocusStatus.DONE
      ? stringResource('focus_logged_minutes', state.durationMinutes)
      : stringResource('focus_of_minutes', state.durationMinutes),
    style: 'bodyMedium',
    color: C.textSecondary,
    css: { paddingTop: 'var(--space-xs)' },
  });
  if (isFlow) {
    const unsub = remainingSeconds.subscribe((r) => {
      const t = clockOf(r);
      if (clock.textContent !== t) clock.textContent = t;
      arc.setProgress(progressOf(r));
    });
    onDispose(unsub);
  }
  return Box({ contentAlignment: 'Center' },
    arc,
    Column({ horizontalAlignment: 'CenterHorizontally' }, clock, sub));
}

// FocusIdleScreen's `var browsing by remember {}` / `var query by remember {}`: kept in the destination memo,
// reset when the idle screen leaves the composition.
let idleLocal = null;
function resetIdleLocalState() {
  if (!idleLocal) return;
  const { browsing, query } = idleLocal;
  queueMicrotask(() => { browsing.value = false; query.value = ''; });
}

function FocusIdleScreen({ state, onStart, onSelectQueueItem, onDuration, modifier }) {
  const browsing = remember('focus:browsing', false);
  const query = remember('focus:query', '');
  idleLocal = { browsing, query };

  return withModifier(modifier, observe(combine([browsing, query], (b, q) => ({ b, q })), ({ b: isBrowsing, q }) => {
    const options = [
      ...(state.hasBlock ? [FocusQueueItem({
        id: '__current__',
        title: state.blockTitle,
        subtitle: state.blockSubtitle,
        tag: state.blockTag ?? BlockTag.READ,
        durationMinutes: state.durationMinutes,
      })] : []),
      ...state.queue,
    ].filter((row) => q.trim() === '' || row.title.toLowerCase().includes(q.toLowerCase())
      || row.subtitle.toLowerCase().includes(q.toLowerCase()));

    const top = Row({
      class: 'fill-w',
      css: { height: 'var(--size-tabBarHeight)', flex: 'none' },
      verticalAlignment: 'CenterVertically',
      horizontalArrangement: { spacedBy: 'var(--space-sm)' },
    }, ...(isBrowsing ? [
      IconButton({
        onClick: () => { browsing.value = false; },
        css: timeFieldSize,
        children: Icon({ imageVector: 'Filled.ArrowBack', contentDescription: stringResource('focus_back') }),
      }),
      OutlinedTextField({
        key: 'focus-search',
        value: q,
        onValueChange: (v) => { query.value = v; },
        class: 'weight',
        singleLine: true,
        placeholder: Text(stringResource('focus_search_topics')),
        shape: CircleShape,
        textStyle: 'bodyMedium',
      }),
    ] : [
      Text({ text: stringResource('focus_title'), class: 'weight', style: 'titleLarge', color: C.text }),
      IconButton({
        onClick: () => { query.value = ''; browsing.value = true; },
        css: timeFieldSize,
        children: Icon({ imageVector: 'Filled.Search', contentDescription: stringResource('focus_browse_topics') }),
      }),
    ]));

    const col = (...kids) => Column({
      class: 'fill',
      css: { paddingInline: 'var(--space-lg)', paddingBlock: 'var(--space-sm)' },
    }, ...kids);

    if (isBrowsing) {
      return col(top, LazyColumn({ class: ['weight', 'fill-w'], scrollKey: 'focus-browse' },
        ...options.flatMap((row) => FocusQueueRow({
          item: row,
          onClick: () => {
            if (row.id !== '__current__') onSelectQueueItem(row.id);
            browsing.value = false;
          },
        })),
        options.length === 0 ? Text({
          text: stringResource('focus_no_topics_match', q),
          style: 'bodyMedium',
          color: C.textSecondary,
          css: { paddingBlock: 'var(--space-xxl)' },
        }) : null));
    }

    const card = Surface({
      onClick: state.hasBlock ? onStart : () => { browsing.value = true; },
      class: 'fill-w',
      css: { paddingTop: 'var(--space-sm)' },
      shape: 'var(--radius-extraLarge)',
      color: state.hasBlock ? C.selectionContainer : C.surfaceContainerHigh,
      contentColor: state.hasBlock ? C.onSelectionContainer : C.text,
      testTag: 'focus-up-next',
      children: Column({ css: { padding: 'var(--space-xxl)' }, verticalArrangement: { spacedBy: 'var(--space-xs)' } },
        ...(state.hasBlock ? [
          Text({ text: stringResource('focus_up_next'), style: 'labelLarge' }),
          Text({ text: state.blockTitle, style: 'headlineSmall' }),
          state.blockSubtitle.trim() !== '' ? Text({ text: state.blockSubtitle, style: 'bodyMedium' }) : null,
          Row({ css: { paddingTop: 'var(--space-md)' }, verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
            Icon({ imageVector: 'Filled.PlayArrow', size: 'var(--size-selectionIndicator)' }),
            Text({ text: stringResource('focus_start_minutes', state.durationMinutes), style: 'titleMedium' })),
        ] : [
          Text({ text: stringResource('focus_empty_title'), style: 'headlineSmall' }),
          Row({ css: { paddingTop: 'var(--space-md)' }, verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
            Icon({ imageVector: 'Filled.Search', size: 'var(--size-selectionIndicator)' }),
            Text({ text: stringResource('focus_pick_topic'), style: 'titleMedium' })),
        ]).filter(Boolean)),
    });

    const lengthTitle = Text({
      text: stringResource('focus_length'),
      style: 'titleSmall',
      color: C.textSecondary,
      css: { paddingTop: 'var(--space-lg)', paddingBottom: 'var(--space-xs)' },
    });
    const picker = LengthPicker({
      key: 'focus',
      presets: FOCUS_LENGTH_PRESETS_MINUTES,
      selectedMinutes: state.durationMinutes,
      onSelect: onDuration,
      parseCustom: focusLengthMinutesOrNull,
      labels: LengthPickerLabels({
        minutes: (it) => formatAndroid(R.string.focus_length_minutes, it),
        custom: stringResource('focus_length_custom'),
        dialogTitle: stringResource('focus_length_custom_title'),
        fieldLabel: stringResource('focus_length_custom_field', FOCUS_LENGTH_MIN_MINUTES, FOCUS_LENGTH_MAX_MINUTES),
        ok: stringResource('focus_length_ok'),
        cancel: stringResource('focus_length_cancel'),
      }),
    });

    let leftTitle = null;
    if (state.queue.length > 0) {
      const left = state.queue.filter((it) => !it.completed).length + (state.hasBlock ? 1 : 0);
      leftTitle = Text({
        text: pluralStringResource('focus_left_today', left, left),
        style: 'titleSmall',
        color: C.textSecondary,
        css: { paddingTop: 'var(--space-xxl)', paddingBottom: 'var(--space-xs)' },
      });
    }
    const list = LazyColumn({ class: ['weight', 'fill-w'], scrollKey: 'focus-queue' },
      ...state.queue.flatMap((row) => FocusQueueRow({ item: row, onClick: () => onSelectQueueItem(row.id) })));

    return col(top, card, lengthTitle, picker, leftTitle, list);
  }, { class: 'fill' }));
}
const timeFieldSize = {
  width: 'var(--size-timeField)', height: 'var(--size-timeField)',
  minWidth: 'var(--size-timeField)', minHeight: 'var(--size-timeField)',
};

/** Row + hairline divider (two siblings, as in the LazyColumn item). */
function FocusQueueRow({ item, onClick }) {
  const row = Surface({
    onClick,
    color: C.surface,
    class: 'fill-w',
    key: 'q:' + item.id,
    children: Row({
      class: 'fill-w',
      css: { height: 'var(--size-ctaHeight)' },
      verticalAlignment: 'CenterVertically',
      horizontalArrangement: { spacedBy: 'var(--space-md)' },
    },
    Column({ class: 'weight', verticalArrangement: { spacedBy: 'var(--size-focusTagVertical)' } },
      Text({ text: item.title, style: 'bodyLarge', color: item.completed ? C.textSecondary : C.text }),
      Text({ text: item.subtitle, style: 'labelSmall', color: C.textSecondary })),
    item.completed
      ? Icon({ imageVector: 'Filled.Check', contentDescription: stringResource('focus_done'), tint: C.primary })
      : TagChip({ tag: item.tag })),
  });
  return [row, HorizontalDivider({ color: C.outlineVariant, thickness: 'var(--stroke-hairline)' })];
}

function TagChip({ tag }) {
  const background = tag === BlockTag.READ ? C.selectionContainer : tag === BlockTag.PRACTICE ? C.primary : C.surface;
  const foreground = tag === BlockTag.READ ? C.onSelectionContainer : tag === BlockTag.PRACTICE ? C.onPrimary : C.primary;
  return Surface({
    color: background,
    shape: CircleShape,
    border: tag === BlockTag.REVISE ? BorderStroke('var(--stroke-hairline)', C.outline) : null,
    css: { flex: 'none' },
    children: Text({
      text: stringResource(labelRes(tag)),
      style: 'labelSmall',
      color: foreground,
      css: { paddingInline: 'var(--size-focusTagHorizontal)', paddingBlock: 'var(--size-focusTagVertical)' },
    }),
  });
}

function labelRes(tag) {
  switch (tag) {
    case BlockTag.READ: return 'focus_tag_learn';
    case BlockTag.PRACTICE: return 'focus_tag_practice';
    default: return 'focus_tag_revise';
  }
}


// Modifier.weight(1f) on a button: its 48dp touch-target wrapper is `flex: none`, so the weight goes inline.
const weightCss = { flex: '1 1 0' };
