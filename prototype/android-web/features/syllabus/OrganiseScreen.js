// Port of feature/syllabus/OrganiseScreen.kt — OrganiseRoute + OrganiseScreen.
// Dates: DateTimeFormatter with Locale.getDefault() → Intl 'en-US' (E = "Sat", EEEE d MMM, h:mm a).
// Scaffold: the caller's modifier already consumed the window insets, so InsetlessScaffold (_local.js) is used.
import { h } from '../../platform/h.js';
import { combine } from '../../platform/flow.js';
import { observe, viewModel, LaunchedEffect, remember } from '../../platform/nav.js';
import { dayOfMonth } from '../../domain/time.js';
import { PlanningOrder } from '../../domain/RollingPlan.js';
import { PriorityBand, yieldRank } from '../../domain/ChapterPriority.js';
import {
  C, Column, Row, Box, Spacer, Text, Icon, IconButton, Surface, LazyColumn, LazyRow, Button, TextButton,
  FilterChip, AlertDialog, OutlinedTextField, AppValueStepper, AppCard, AppCardTone, HorizontalDivider,
  CircularProgressIndicator, CircleShape,
} from '../../design/components.js';
import { OrganiseViewModel } from './OrganiseViewModel.js';
import { RenameChapterDialog } from './ChapterActions.js';
import { PriorityDetail, PriorityDetailDialog, PriorityLabel } from './PriorityUi.js';
import { R, str, plural, dp, InsetlessScaffold } from './_local.js';

const OrganiseView = Object.freeze({ PLAN: 'PLAN', SYLLABUS: 'SYLLABUS' });

export function OrganiseRoute({
  examPackRepository, attemptRepository, targetSyllabusRepository, planRepository,
  studyPreferenceRepository, rollingPlanRepository, topicProgressRepository, onBack, modifier,
}) {
  const vm = viewModel('OrganiseViewModel', () => new OrganiseViewModel(
    examPackRepository, attemptRepository, targetSyllabusRepository, planRepository,
    studyPreferenceRepository, rollingPlanRepository, topicProgressRepository,
  ));
  return observe(vm.state, (state) => {
    LaunchedEffect([state.saved], () => {
      // onBack deferred one task: platform/nav.js observe() still runs a re-render already queued by
      // consumeSaved() after the destination is torn down, which would leave the preview dialog mounted.
      if (state.saved) { vm.consumeSaved(); setTimeout(onBack, 0); }
    });
    return OrganiseScreen({
      state,
      onBack,
      onSelectSubject: (id) => vm.selectSubject(id),
      onToggleIncluded: (id) => vm.toggleIncluded(id),
      onAddChapter: (t, hrs, s) => vm.addChapter(t, hrs, s),
      onRemoveChapter: (id) => vm.removeChapter(id),
      onRenameChapter: (id, t) => vm.renameChapter(id, t),
      onOrder: (o) => vm.setOrder(o),
      onDiscard: () => vm.discard(),
      onApply: () => vm.apply(),
      modifier,
    });
  }, { style: { display: 'flex', flexDirection: 'column', minHeight: '0' } });
}

export function OrganiseScreen(p) {
  const ui = remember('organise:ui', () => ({ renameRow: null, view: OrganiseView.PLAN, addOpen: false, previewOpen: false }));
  const newTitle = remember('organise:newTitle', '');
  const newHours = remember('organise:newHours', 2);
  return observe(ui, (u) => {
    const set = (patch) => { ui.value = { ...ui.value, ...patch }; };
    return renderOrganise(p, u, set, newTitle, newHours);
  }, { style: { display: 'flex', flexDirection: 'column', flex: '1 1 auto', minHeight: '0' } });
}

function renderOrganise(p, u, set, newTitle, newHours) {
  const { state } = p;
  const defaultSubjectName = R.string.organise_my_syllabus;

  const topBar = Row({
    class: 'fill-w', verticalAlignment: 'CenterVertically',
    css: { height: 'var(--size-tabBarHeight)', padding: '0 var(--space-xs)' },
  },
  IconButton({ onClick: p.onBack, children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.organise_back }) }),
  Text({ text: R.string.organise_title, style: 'titleLarge', class: 'weight' }),
  state.dirty ? Text({
    text: plural(R.plurals.organise_changes, state.exactChangeCount, state.exactChangeCount),
    style: 'labelMedium', color: C.textSecondary, css: { paddingRight: 'var(--space-md)', flex: 'none' },
  }) : null);

  const bottomBar = state.dirty ? Surface({
    color: C.surfaceCard, shadowElevation: dp('elev-floatingShadow'),
    children: Row({ class: 'fill-w', css: { padding: 'var(--space-sm) var(--space-screen)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      TextButton({ onClick: p.onDiscard, class: 'weight', css: { flex: '1 1 0' }, children: Text(R.string.organise_discard) }),
      Button({
        onClick: () => set({ previewOpen: true }),
        css: { flex: '1.5 1 0', minWidth: '0', height: 'var(--size-touchTarget)' },
        shape: 'var(--radius-pill)',
        children: Text(R.string.organise_preview),
      })),
  }) : null;

  let content;
  if (state.loading) {
    content = Box({ class: 'fill', contentAlignment: 'Center' }, CircularProgressIndicator());
  } else {
    content = Column({ class: 'fill' },
      Row({ class: 'fill-w', css: { padding: 'var(--space-sm) var(--space-screen)', flex: 'none' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
        SegmentedChoice(R.string.organise_plan, u.view === OrganiseView.PLAN, () => set({ view: OrganiseView.PLAN })),
        SegmentedChoice(R.string.organise_syllabus, u.view === OrganiseView.SYLLABUS, () => set({ view: OrganiseView.SYLLABUS }))),
      u.view === OrganiseView.PLAN
        ? PlanPreview(state)
        : SyllabusDraft({
          state,
          order: state.planningOrder,
          onOrder: p.onOrder,
          onSelectSubject: p.onSelectSubject,
          onToggleIncluded: p.onToggleIncluded,
          onAdd: () => set({ addOpen: true }),
          onRemove: p.onRemoveChapter,
          onRename: (row) => set({ renameRow: { row } }),
        }));
  }

  const scaffold = InsetlessScaffold({ containerColor: C.bg, topBar, bottomBar, content, css: { flex: '1 1 auto', minHeight: '0' } });
  // modifier.fillMaxSize(): the shell's inset padding goes on the Scaffold.
  const m = p.modifier?.css || {};
  for (const k in m) scaffold.style[k] = m[k];

  const out = [scaffold];
  if (u.renameRow) {
    const row = u.renameRow.row;
    out.push(RenameChapterDialog({
      openId: u.renameRow, currentName: row.title, originalName: row.originalTitle,
      onSave: (name) => p.onRenameChapter(row.id, name), onDismiss: () => set({ renameRow: null }),
    }));
  }
  if (u.addOpen) out.push(AddChapterDialog({ newTitle, newHours, defaultSubjectName, onAddChapter: p.onAddChapter, close: () => set({ addOpen: false }) }));
  if (u.previewOpen) {
    out.push(AlertDialog({
      key: 'organise:preview',
      onDismissRequest: () => set({ previewOpen: false }),
      title: Text(R.string.organise_what_changes),
      text: Column({ class: 'fill-w', verticalArrangement: { spacedBy: 'var(--space-md)' } },
        DeltaRow(R.string.organise_chapters_in_play, String(state.beforeIncludedCount), String(state.includedCount)),
        DeltaRow(R.string.organise_hours_left,
          str(R.string.organise_hours_estimate, state.beforeRemainingMinutes / 60),
          str(R.string.organise_hours_estimate, state.remainingMinutes / 60)),
        state.saveFailed ? Text({ text: R.string.organise_save_failed, color: C.error }) : null),
      confirmButton: Button({
        onClick: p.onApply, enabled: !state.saving,
        children: state.saving
          ? CircularProgressIndicator({ css: { width: 'var(--space-xl)', height: 'var(--space-xl)' }, strokeWidth: dp('stroke-selected') })
          : Text(R.string.organise_apply),
      }),
      dismissButton: TextButton({ onClick: () => set({ previewOpen: false }), children: Text(R.string.organise_keep_editing) }),
    }));
  }
  return out;
}

function AddChapterDialog({ newTitle, newHours, defaultSubjectName, onAddChapter, close }) {
  return observe(combine([newTitle, newHours], (t, hrs) => ({ t, hrs })), ({ t, hrs }) => AlertDialog({
    key: 'organise:add',
    onDismissRequest: close,
    title: Text(R.string.organise_add_chapter),
    text: Column({ verticalArrangement: { spacedBy: 'var(--space-md)' } },
      OutlinedTextField({
        key: 'organise:newTitle',
        value: t,
        onValueChange: (v) => { newTitle.value = v; },
        label: Text(R.string.organise_chapter_name),
        singleLine: true,
      }),
      AppValueStepper({
        label: R.string.organise_estimated_hours,
        value: str(R.string.organise_hours_estimate, hrs),
        decreaseContentDescription: R.string.organise_decrease_hours,
        increaseContentDescription: R.string.organise_increase_hours,
        onDecrease: () => { newHours.value = Math.max(hrs - 0.5, 0.5); },
        onIncrease: () => { newHours.value = Math.min(hrs + 0.5, 20); },
        decreaseEnabled: hrs > 0.5,
        increaseEnabled: hrs < 20,
      }),
      Text({ text: R.string.organise_hours_hint, style: 'bodySmall', color: C.textSecondary })),
    confirmButton: TextButton({
      enabled: t.trim() !== '',
      onClick: () => {
        onAddChapter(t, hrs, defaultSubjectName);
        newTitle.value = '';
        newHours.value = 2;
        close();
      },
      children: Text(R.string.organise_add),
    }),
    dismissButton: TextButton({ onClick: close, children: Text(R.string.organise_cancel) }),
  }), { style: { display: 'contents' } });
}

const fmtTime = (minuteOfDay) => {
  const d = new Date(Date.UTC(1970, 0, 1, Math.trunc(minuteOfDay / 60), minuteOfDay % 60));
  return new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', hour: 'numeric', minute: '2-digit', hour12: true }).format(d);
};
const dateUtc = (iso) => new Date(iso + 'T00:00:00Z');
const fmtE = (iso) => new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', weekday: 'short' }).format(dateUtc(iso));
const fmtLong = (iso) => {
  const d = dateUtc(iso);
  const wd = new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', weekday: 'long' }).format(d);
  const mon = new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', month: 'short' }).format(d);
  return `${wd} ${dayOfMonth(iso)} ${mon}`;
};
const isPractice = (title) => title.toLowerCase().includes('practice');

function PlanPreview(state) {
  const sessions = state.todaySessions;
  const card = AppCard({
    tone: AppCardTone.Raised, class: 'fill-w',
    children: Column({ class: 'fill-w' },
      ...sessions.flatMap((session, index) => {
        const taskRes = session.isRevision ? R.string.organise_task_revise
          : isPractice(session.title) ? R.string.organise_task_practice : R.string.organise_task_learn;
        const row = Row({
          class: 'fill-w', verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: 'var(--space-md)' },
          css: { height: 'var(--size-topAppBarHeight)', padding: '0 var(--space-md)' },
        },
        Surface({
          shape: CircleShape, color: C.selectionContainer, contentColor: C.onSelectionContainer,
          css: { width: 'var(--size-timeField)', height: 'var(--size-timeField)', flex: 'none' },
          children: Box({ class: 'fill', contentAlignment: 'Center' },
            Icon({ imageVector: isPractice(session.title) ? 'Filled.Quiz' : 'Filled.MenuBook', contentDescription: null, size: 'var(--size-standardIcon)' })),
        }),
        Column({ class: 'weight' },
          Text({ text: session.title, style: 'bodyLarge' }),
          Text({ text: str(R.string.organise_session_meta, taskRes, session.durationMinutes, fmtTime(session.startMinuteOfDay)), style: 'labelSmall', color: C.textSecondary })));
        return index < sessions.length - 1
          ? [row, HorizontalDivider({ color: C.outlineVariant, thickness: 'var(--stroke-hairline)' })] : [row];
      }),
      sessions.length === 0
        ? Text({ text: R.string.organise_no_sessions_today, style: 'bodyMedium', color: C.textSecondary, css: { padding: 'var(--space-lg)' } })
        : null),
  });
  const first = state.week[0];
  return lazyItems(LazyColumn({
    class: 'fill-w', scrollKey: 'organise-plan', css: { flex: '0 1 auto', minHeight: '0' },
    contentPadding: 'var(--space-md) var(--space-screen)',
    verticalArrangement: { spacedBy: 'var(--space-md)' },
  },
  Column({ class: 'fill-w' },
    Text({ text: R.string.organise_today, style: 'labelLarge', color: C.textSecondary }),
    Text({ text: str(R.string.organise_hours_planned, (first?.plannedMinutes ?? 0) / 60), style: 'headlineSmall', css: { paddingTop: 'var(--space-xs)' } })),
  card,
  Column({ class: 'fill-w' },
    Text({ text: R.string.organise_next_seven, style: 'labelLarge', color: C.textSecondary, css: { paddingTop: 'var(--space-lg)' } }),
    LazyRow({ class: 'fill-w', scrollKey: 'organise-week', css: { marginTop: 'var(--space-md)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      ...state.week.map((day) => {
        const selected = first != null && day.equals(first);
        return Surface({
          shape: 'var(--radius-large)',
          color: selected ? C.primary : C.surfaceContainerLow,
          contentColor: selected ? C.onPrimary : C.text,
          css: { width: 'var(--size-organiseDayWidth)', height: 'var(--size-organiseDayHeight)', flex: 'none' },
          children: Column({ class: 'fill', horizontalAlignment: 'CenterHorizontally', verticalArrangement: 'Center' },
            Text({ text: fmtE(day.date), style: 'labelSmall' }),
            Text({ text: String(dayOfMonth(day.date)), style: 'titleMedium' }),
            Text({ text: str(R.string.organise_hours_short, day.plannedMinutes / 60), style: 'labelSmall' })),
        });
      }))),
  AppCard({
    tone: AppCardTone.Standard, class: 'fill-w',
    children: Column({ class: 'fill-w', css: { padding: 'var(--space-lg)' } },
      Text({ text: first ? fmtLong(first.date) : '', style: 'titleMedium' }),
      Text({
        text: plural(R.plurals.organise_timeline_body, state.includedCount, state.includedCount, state.remainingMinutes / 60),
        style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: 'var(--space-sm)' },
      }),
      HorizontalDivider({ css: { margin: 'var(--space-md) 0' } }),
      Text({ text: R.string.organise_projection_note, style: 'bodySmall', color: C.textMuted })),
  })));
}

/** LazyColumn items keep their measured height (flex items would otherwise shrink to fit). */
function lazyItems(list) {
  for (const c of list.children) c.style.flexShrink = '0';
  return list;
}

function SyllabusDraft({ state, order, onOrder, onSelectSubject, onToggleIncluded, onAdd, onRemove, onRename }) {
  const pyqFirstYear = state.pyqFirstYear;
  const pyqLastYear = state.pyqLastYear;
  const priorityDetail = remember('organise:priorityDetail', null);
  let selectedRows = state.chapters.filter((it) => it.subjectId === state.selectedSubjectId);
  if (order === PlanningOrder.SHORTEST_FIRST) {
    selectedRows = selectedRows.slice().sort((a, b) => a.estimatedMinutes - b.estimatedMinutes);
  } else if (order === PlanningOrder.HIGH_YIELD_FIRST) {
    selectedRows = selectedRows.slice().sort((a, b) =>
      (yieldRank(a.priority?.band ?? PriorityBand.NO_DATA) - yieldRank(b.priority?.band ?? PriorityBand.NO_DATA))
      || (-(a.priority?.meanQuestionsPerYear ?? 0) - -(b.priority?.meanQuestionsPerYear ?? 0)));
  }
  const chip = (selected, onClick, label) => FilterChip({ selected, onClick, label: Text(label) });
  const list = lazyItems(LazyColumn({
    class: 'fill-w', scrollKey: 'organise-syllabus', css: { flex: '0 1 auto', minHeight: '0' },
    contentPadding: 'var(--space-sm) var(--space-screen)',
    verticalArrangement: { spacedBy: 'var(--space-md)' },
  },
  LazyRow({ class: 'fill-w', scrollKey: 'organise-subjects', horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
    ...state.subjects.map((subject) => chip(state.selectedSubjectId === subject[0], () => onSelectSubject(subject[0]), subject[1]))),
  Column({ class: 'fill-w' },
    Text({ text: R.string.organise_order, style: 'titleMedium' }),
    LazyRow({ class: 'fill-w', scrollKey: 'organise-order', css: { paddingTop: 'var(--space-sm)' }, horizontalArrangement: { spacedBy: 'var(--space-sm)' } },
      chip(order === PlanningOrder.DEFAULT, () => onOrder(PlanningOrder.DEFAULT), R.string.organise_order_default),
      chip(order === PlanningOrder.HIGH_YIELD_FIRST, () => onOrder(PlanningOrder.HIGH_YIELD_FIRST), R.string.organise_order_high_yield),
      chip(order === PlanningOrder.SHORTEST_FIRST, () => onOrder(PlanningOrder.SHORTEST_FIRST), R.string.organise_order_shortest)),
    Text({ text: R.string.organise_order_hint, style: 'bodySmall', color: C.textSecondary, css: { paddingTop: 'var(--space-sm)' } })),
  Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
    Text({ text: R.string.organise_chapters, style: 'titleMedium', class: 'weight' }),
    Text({ text: str(R.string.organise_in_play, selectedRows.filter((it) => !it.excluded).length), style: 'labelMedium', color: C.textSecondary, css: { flex: 'none' } }),
    TextButton({ onClick: onAdd, children: [Icon({ imageVector: 'Filled.Add', contentDescription: null }), Text(R.string.organise_add)] })),
  ...selectedRows.map((row) => Surface({
    onClick: () => onToggleIncluded(row.id),
    color: row.excluded ? C.surfaceControl : C.surfaceCard,
    shape: 'var(--radius-lg)',
    class: 'fill-w',
    key: 'organise-row:' + row.id,
    children: Row({ class: 'fill-w', verticalAlignment: 'CenterVertically', css: { padding: 'var(--space-md)' } },
      Icon({ imageVector: row.excluded ? 'Filled.CheckBoxOutlineBlank' : 'Filled.CheckBox', contentDescription: null, tint: row.excluded ? C.textMuted : C.brandDeep, css: { flex: 'none' } }),
      Column({ class: 'weight', css: { padding: '0 var(--space-md)' } },
        Text({ text: row.title, style: 'bodyLarge', color: row.excluded ? C.textMuted : C.text }),
        Text({
          text: row.excluded ? R.string.syllabus_parked : str(R.string.organise_hours_estimate, row.estimatedMinutes / 60),
          style: 'bodySmall', color: C.textSecondary,
        }),
        row.priority ? PriorityLabel({
          priority: row.priority, firstYear: pyqFirstYear, lastYear: pyqLastYear,
          onClick: () => { priorityDetail.value = PriorityDetail(row.title, row.priority); },
        }) : null),
      IconButton({ onClick: () => onRename(row), children: Icon({ imageVector: 'Filled.Edit', contentDescription: R.string.syllabus_chapter_rename, tint: C.textSecondary }) }),
      row.custom ? IconButton({ onClick: () => onRemove(row.id), children: Icon({ imageVector: 'Filled.Delete', contentDescription: R.string.organise_delete_chapter, tint: C.error }) }) : null),
  })),
  selectedRows.length === 0 ? Text({ text: R.string.organise_no_chapters, style: 'bodyLarge', color: C.textSecondary }) : null,
  Spacer({ height: 'var(--size-bottomClearance)' })));
  const dialogHost = observe(priorityDetail, (detail) => (detail
    ? PriorityDetailDialog({ detail, firstYear: pyqFirstYear, lastYear: pyqLastYear, onDismiss: () => { priorityDetail.value = null; } })
    : null), { style: { display: 'contents' } });
  return [dialogHost, list];
}

function SegmentedChoice(label, selected, onClick) {
  return Column({ class: 'weight' },
    Surface({
      onClick, color: C.surface, class: 'fill-w',
      children: Box({ contentAlignment: 'Center', class: 'fill-w', css: { height: 'var(--size-touchTarget)' } },
        Text({ text: label, style: 'labelLarge', color: selected ? C.primary : C.text })),
    }),
    h('div', { style: { width: '100%', flex: 'none', height: selected ? 'var(--stroke-selected)' : 'var(--stroke-hairline)', background: selected ? 'var(--c-primary)' : 'var(--c-outlineVariant)' } }));
}

function DeltaRow(label, before, after) {
  return Row({ class: 'fill-w', verticalAlignment: 'CenterVertically' },
    Text({ text: label, class: 'weight', style: 'bodyMedium' }),
    Text({ text: str(R.string.organise_change_value, before, after), style: 'titleSmall', fontWeight: 'SemiBold', css: { flex: 'none' } }));
}
