// Port of feature/progress/LogSessionScreen.kt — LogSessionRoute, LogSessionScreen, SessionDatePicker,
// DeleteSessionDialog. Picker dialogs leave composition when closed in Compose (fresh picker state on the next
// open); here each open gets a new sequence number that keys its remembered picker state.
import { viewModel, LaunchedEffect, remember, observe } from '../../platform/nav.js';
import { combine } from '../../platform/flow.js';
import {
  C, Column, Row, Box, Spacer, Text, Icon, IconButton, Button, OutlinedButton, TextButton, FilterChip, Surface,
  AlertDialog, Dialog, LazyColumn, DatePicker, DatePickerDialog, rememberDatePickerState, TimePicker,
  rememberTimePickerState, AppValueStepper, clickable,
} from '../../design/components.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { formatMinuteOfDay } from '../../domain/StudySession.js';
import { today as localToday } from '../../domain/time.js';
import { LOGGABLE_ACTIVITY_TYPES, MANUAL_SESSION_MIN_MINUTES, MANUAL_SESSION_MAX_MINUTES } from '../../domain/ManualSession.js';
import { LogSessionViewModel } from './LogSessionViewModel.js';
import { insightDuration } from './ProgressScreen.js';
import { activityLabel } from './DayLogScreen.js';
import { R } from './strings.js';
import { RouteHost, noShrink, dateToUtcMillis, utcMillisToDate } from './_local.js';

const sp = (k) => `var(--space-${k})`;
const sz = (k) => `var(--size-${k})`;
const rad = (k) => `var(--radius-${k})`;

export function LogSessionRoute({
  sessionId, date, examPackRepository, attemptRepository, studySessionRepository, onDone, modifier,
}) {
  const vm = viewModel(`log-session-${sessionId ?? 'new'}-${date}`, () =>
    new LogSessionViewModel(sessionId, date, examPackRepository, attemptRepository, studySessionRepository));
  return RouteHost(vm.state, (state) => {
    LaunchedEffect([state.finished], () => { if (state.finished) onDone(); });
    return LogSessionScreen({
      state,
      onBack: onDone,
      onSubject: (id) => vm.selectSubject(id),
      onChapter: (id) => vm.selectChapter(id),
      onType: (t) => vm.selectType(t),
      onStepDuration: (up) => vm.stepDuration(up),
      onDate: (d) => vm.setDate(d),
      onStartMinute: (m) => vm.setStartMinute(m),
      onSave: () => vm.save(),
      onDelete: () => vm.delete(),
      modifier,
    });
  });
}

export function LogSessionScreen({
  state, onBack, onSubject, onChapter, onType, onStepDuration, onDate, onStartMinute, onSave, onDelete, modifier,
}) {
  const confirmDelete = remember('logSession:confirmDelete', false);
  const header = Row({ class: 'fill-w', css: { padding: `${sp('sm')} ${sp('xs')}` }, verticalAlignment: 'CenterVertically' },
    IconButton({ onClick: onBack, children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.day_log_back, tint: C.textSecondary }) }),
    Text({ text: state.isEdit ? R.string.log_session_edit_title : R.string.log_session_title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold', class: 'weight' }),
    state.isEdit && !state.unavailable
      ? IconButton({ onClick: () => { confirmDelete.value = true; }, enabled: !state.saving, children: Icon({ imageVector: 'Outlined.Delete', contentDescription: R.string.log_session_delete, tint: C.danger }) })
      : null,
  );
  let body;
  if (state.loading) body = FormNote(R.string.insights_loading);
  else if (state.unavailable) body = FormNote(R.string.log_session_unavailable);
  else body = LogSessionForm({ state, onSubject, onChapter, onType, onStepDuration, onDate, onStartMinute, onSave });
  return Column({ modifier, class: 'weight fill-w' },
    header,
    body,
    observe(confirmDelete, (open) => (open ? DeleteSessionDialog({
      onConfirm: () => { confirmDelete.value = false; onDelete(); },
      onDismiss: () => { confirmDelete.value = false; },
    }) : null), { style: { display: 'contents' } }),
  );
}

let pickerSeq = 0;

function LogSessionForm({ state, onSubject, onChapter, onType, onStepDuration, onDate, onStartMinute, onSave }) {
  const pickChapter = remember('logSession:pickChapter', false);
  const pickDate = remember('logSession:pickDate', 0);
  const pickTime = remember('logSession:pickTime', 0);
  const chips = (items, key) => {
    const row = Row({ class: 'scroll-x', css: { maxWidth: '100%' }, horizontalArrangement: { spacedBy: sp('sm') } }, ...items);
    row.setAttribute('data-scroll-key', key);
    return row;
  };
  const form = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('screen')}` }, verticalArrangement: { spacedBy: sp('sm') } },
    FieldLabel(R.string.log_session_subject),
    chips(state.subjects.map((subject) => FilterChip({
      selected: subject.id === state.subjectId, onClick: () => onSubject(subject.id), label: Text(subject.name),
    })), 'log-subjects'),
    FieldLabel(R.string.log_session_chapter),
    OutlinedButton({
      onClick: () => { pickChapter.value = true; },
      enabled: state.chapters.length > 0,
      class: 'fill-w', css: { minHeight: sz('touchTarget') },
      children: Text({ text: state.chapterTitle ?? R.string.log_session_chapter_none, css: { width: '100%' }, maxLines: 1 }),
    }),
    FieldLabel(R.string.log_session_type),
    chips(LOGGABLE_ACTIVITY_TYPES.map((type) => FilterChip({
      selected: type === state.activityType, onClick: () => onType(type), label: Text(activityLabel(type)),
    })), 'log-types'),
    AppValueStepper({
      label: R.string.log_session_duration,
      value: insightDuration(state.durationMinutes),
      decreaseContentDescription: R.string.log_session_shorter,
      increaseContentDescription: R.string.log_session_longer,
      onDecrease: () => onStepDuration(false),
      onIncrease: () => onStepDuration(true),
      decreaseEnabled: state.durationMinutes > MANUAL_SESSION_MIN_MINUTES,
      increaseEnabled: state.durationMinutes < MANUAL_SESSION_MAX_MINUTES,
      css: { paddingTop: sp('md') },
    }),
    Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') }, css: { paddingTop: sp('md') } },
      Column({ class: 'weight' },
        FieldLabel(R.string.log_session_date),
        OutlinedButton({ onClick: () => { pickDate.value = ++pickerSeq; }, class: 'fill-w', css: { minHeight: sz('touchTarget') }, children: Text(formatInsightDate(state.date)) }),
      ),
      Column({ class: 'weight' },
        FieldLabel(R.string.log_session_start),
        OutlinedButton({ onClick: () => { pickTime.value = ++pickerSeq; }, class: 'fill-w', css: { minHeight: sz('touchTarget') }, children: Text(formatMinuteOfDay(state.startMinuteOfDay)) }),
      ),
    ),
    state.saveFailed ? Text({ text: R.string.log_session_save_failed, style: 'bodySmall', color: C.danger }) : null,
    Button({
      onClick: onSave, enabled: state.canSave, class: 'fill-w', css: { height: sz('ctaHeight'), paddingTop: sp('xs') },
      children: Text(state.isEdit ? R.string.log_session_save_changes : R.string.log_session_save),
    }),
    Spacer({ height: sp('xl') }),
  );
  form.setAttribute('data-scroll-key', 'log-session');
  noShrink(form);
  const dialogs = observe(
    combine([pickChapter, pickDate, pickTime], (a, b, c) => [a, b, c]),
    ([chapter, dateSeq, timeSeq]) => [
      chapter ? ChapterPickerDialog({
        chapters: state.chapters, selectedId: state.chapterId,
        onPick: (id) => { onChapter(id); pickChapter.value = false; },
        onDismiss: () => { pickChapter.value = false; },
      }) : null,
      dateSeq ? SessionDatePicker({ date: state.date, onDate: (d) => { onDate(d); pickDate.value = 0; }, onDismiss: () => { pickDate.value = 0; }, key: `sessionDate:${dateSeq}` }) : null,
      timeSeq ? SessionTimePicker({ minuteOfDay: state.startMinuteOfDay, onTime: (m) => { onStartMinute(m); pickTime.value = 0; }, onDismiss: () => { pickTime.value = 0; }, key: `sessionTime:${timeSeq}` }) : null,
    ],
    { style: { display: 'contents' } },
  );
  return Column({ class: 'weight fill-w' }, form, dialogs);
}

function ChapterPickerDialog({ chapters, selectedId, onPick, onDismiss }) {
  const list = LazyColumn({ class: 'fill-w', css: { maxHeight: sz('dialogListMaxHeight') }, scrollKey: 'chapter-picker' },
    PickerRow(R.string.log_session_chapter_none, selectedId == null, () => onPick(null)),
    ...chapters.map((chapter) => PickerRow(chapter.title, chapter.id === selectedId, () => onPick(chapter.id))),
  );
  noShrink(list);
  return AlertDialog({
    key: 'chapterPicker',
    onDismissRequest: onDismiss,
    title: Text(R.string.log_session_chapter),
    text: list,
    confirmButton: TextButton({ onClick: onDismiss, children: Text(R.string.log_session_cancel) }),
    containerColor: C.surface,
  });
}

function PickerRow(text, selected, onClick) {
  return clickable(Text({
    text, style: 'bodyMedium', color: selected ? C.brandSoft : C.text, fontWeight: selected ? 'SemiBold' : 'Normal',
    class: 'fill-w', css: { padding: `${sp('md')} 0` },
  }), { onClick });
}

/** internal fun SessionDatePicker(date, onDate, onDismiss) */
export function SessionDatePicker({ date, onDate, onDismiss, key = 'sessionDate' }) {
  const maxMillis = dateToUtcMillis(localToday());
  const pickerState = rememberDatePickerState({
    key,
    initialSelectedDateMillis: dateToUtcMillis(date),
    selectableDates: { isSelectableDate: (utcTimeMillis) => utcTimeMillis <= maxMillis, isSelectableYear: () => true },
  });
  return DatePickerDialog({
    key,
    onDismissRequest: onDismiss,
    confirmButton: TextButton({
      onClick: () => (pickerState.selectedDateMillis != null ? onDate(utcMillisToDate(pickerState.selectedDateMillis)) : onDismiss()),
      children: Text(R.string.log_session_ok),
    }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(R.string.log_session_cancel) }),
    children: DatePicker({ state: pickerState, showModeToggle: false }),
  });
}

function SessionTimePicker({ minuteOfDay, onTime, onDismiss, key }) {
  const pickerState = rememberTimePickerState({ key, initialHour: Math.trunc(minuteOfDay / 60), initialMinute: minuteOfDay % 60 });
  return Dialog({
    key,
    onDismissRequest: onDismiss,
    children: Surface({ shape: rad('lg'), color: C.surface,
      children: Column({ css: { padding: sp('lg') }, horizontalAlignment: 'CenterHorizontally' },
        TimePicker({ state: pickerState }),
        Row({ class: 'fill-w', css: { paddingTop: sp('md') }, horizontalArrangement: 'End' },
          TextButton({ onClick: onDismiss, children: Text(R.string.log_session_cancel) }),
          TextButton({ onClick: () => onTime(pickerState.hour * 60 + pickerState.minute), children: Text(R.string.log_session_ok) }),
        ),
      ) }),
  });
}

/** Confirms deleting one past session. Shared by the edit form and the day view's row action. */
export function DeleteSessionDialog({ onConfirm, onDismiss }) {
  return AlertDialog({
    key: 'deleteSession',
    onDismissRequest: onDismiss,
    title: Text(R.string.log_session_delete_title),
    text: Text(R.string.log_session_delete_body),
    confirmButton: TextButton({ onClick: onConfirm, children: Text({ text: R.string.log_session_delete, color: C.danger }) }),
    dismissButton: TextButton({ onClick: onDismiss, children: Text(R.string.log_session_cancel) }),
    containerColor: C.surface,
  });
}

function FieldLabel(text) {
  return Text({ text, style: 'titleSmall', color: C.textSecondary, css: { paddingTop: sp('md') } });
}

function FormNote(text) {
  return Box({ class: 'weight fill-w', css: { padding: sp('screen') }, contentAlignment: 'Center' },
    Text({ text, style: 'bodyLarge', color: C.textMuted }));
}
