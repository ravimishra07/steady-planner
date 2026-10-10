// Port of feature/settings/SettingsDetailScreen.kt: SettingsDetailRoute, SettingsDetailScreen (+ private
// SettingsEditor, PlanSection, DateRow, ReminderSection, SectionTitle).
// Web stand-ins:
//  - CreateDocument(application/json) → BackupRepository.exportTo(fileName): a browser download (no picker, so no
//    "cancelled" path).
//  - OpenDocument(mimes) → a hidden <input type=file>; the File goes to requestImport like a content Uri.
//  - POST_NOTIFICATIONS: the reminder is an in-page notification (data/app/DailyReminder.js), so the runtime
//    permission is treated as granted and onReminderToggle calls setReminderEnabled directly.
//  - is24HourFormat / localized short time / "d MMM yyyy" come from the browser locale.
import { viewModel, observe, remember, BackHandler } from '../../platform/nav.js';
import {
  Column, Row, Text, Icon, IconButton, Button, OutlinedButton, TextButton, ButtonDefaults, FilterChip, OutlinedTextField,
  Switch, AlertDialog, DatePicker, DatePickerDialog, rememberDatePickerState, TimePicker, rememberTimePickerState,
  AppValueStepper, LengthPicker, LengthPickerLabels, clickable,
} from '../../design/components.js';
import {
  FOCUS_LENGTH_MAX_MINUTES, FOCUS_LENGTH_MIN_MINUTES, FOCUS_LENGTH_PRESETS_MINUTES, focusLengthMinutesOrNull,
} from '../../domain/FocusSession.js';
import { MIN_FINISH_BY_DAYS_AHEAD } from '../../domain/ExamDates.js';
import { PlanMode } from '../../domain/PlanMode.js';
import { today as localToday, plusDays, plusMonths, maxDate } from '../../domain/time.js';
import { SettingsDetailViewModel, BackupMessage } from './SettingsDetailViewModel.js';
import {
  stringResource, ScrollColumn, formatDMMMyyyy, formatShortTime, formatOneDecimal, is24HourFormat,
  dateToUtcMillis, utcMillisToDate, openDocument,
} from './_local.js';

const BACKUP_MIME = 'application/json';
/** Some file managers label .json as octet-stream or plain text. */
const BACKUP_OPEN_MIMES = [BACKUP_MIME, 'application/octet-stream', 'text/plain'];
const backupFileName = (today) => `neet-planner-backup-${today}.json`;

/**
 * SettingsDetailRoute({ planStore, settingsStore, focusStore, syllabusStore, studySessionStore, examPackRepository,
 *   attemptRepository, studyPreferenceRepository, rollingPlanRepository, configExamDate, examDateOfficial, onBack,
 *   onCleared, onReminderChanged, backupRepository, onImported, modifier })
 */
export function SettingsDetailRoute(p) {
  const vm = p.viewModel || viewModel('SettingsDetailViewModel', () => new SettingsDetailViewModel(
    p.planStore, p.settingsStore, p.focusStore, p.syllabusStore, p.studySessionStore, p.examPackRepository,
    p.attemptRepository, p.studyPreferenceRepository, p.rollingPlanRepository, p.configExamDate, p.examDateOfficial,
    p.onReminderChanged, p.backupRepository,
  ));
  // Asked in context (§10.1) on Android; see header: granted on the web.
  const onReminderToggle = (enabled) => vm.setReminderEnabled(enabled);
  const requestBack = () => { if (vm.requestBack()) p.onBack(); };
  BackHandler(true, requestBack);
  return observe(vm.state, (state) => SettingsDetailScreen({
    state,
    onBack: requestBack,
    onRetry: () => vm.refresh(),
    onWeekdayChange: (v) => vm.setWeekdayHours(v),
    onWeekendChange: (v) => vm.setWeekendHours(v),
    onStudyPlaceChange: (v) => vm.setStudyPlace(v),
    onFocusDuration: (m) => vm.setFocusDurationMinutes(m),
    onFocusEndAlert: (b) => vm.setFocusEndAlert(b),
    onReminderToggle,
    onReminderTime: (m) => vm.setReminderTime(m),
    onShareUsageData: (b) => vm.setShareUsageData(b),
    onPlanMode: (m) => vm.setPlanMode(m),
    onExamDate: (d) => vm.setExamDate(d),
    onFinishBy: (d) => vm.setFinishBy(d),
    onSave: () => vm.save(),
    onDismissSaveResult: () => vm.dismissSaveResult(),
    onDismissDiscard: () => vm.dismissDiscard(),
    onConfirmDiscard: () => { vm.confirmDiscard(); p.onBack(); },
    onExportBackup: () => vm.exportBackup(backupFileName(localToday())),
    onImportBackup: () => openDocument(BACKUP_OPEN_MIMES, (file) => { if (file) vm.requestImport(file); }),
    onConfirmImport: () => vm.confirmImport(p.onImported),
    onDismissImport: () => vm.dismissImport(),
    onDismissBackupMessage: () => vm.dismissBackupMessage(),
    onRequestClear: () => vm.requestClear(),
    onConfirmClear: () => vm.confirmClear(p.onCleared),
    onDismissClear: () => vm.dismissClear(),
    onDismissClearError: () => vm.dismissClearError(),
    modifier: p.modifier,
  }), { class: 'col fill' });
}

export function SettingsDetailScreen(p) {
  const { state } = p;
  const dialogs = [];
  if (state.showClearDialog) {
    dialogs.push(AlertDialog({
      key: 'settings:clear',
      onDismissRequest: p.onDismissClear,
      title: Text(stringResource('settings_clear_plan')),
      text: Text(stringResource('settings_clear_confirm')),
      confirmButton: TextButton({ onClick: p.onConfirmClear, enabled: !state.clearing,
        children: Text(state.clearing ? stringResource('settings_clearing') : stringResource('settings_clear_yes')) }),
      dismissButton: TextButton({ onClick: p.onDismissClear, enabled: !state.clearing, children: Text(stringResource('settings_clear_no')) }),
    }));
  }
  if (state.clearFailed) {
    dialogs.push(AlertDialog({
      key: 'settings:clearFailed',
      onDismissRequest: p.onDismissClearError,
      title: Text(stringResource('settings_clear_failed_title')),
      text: Text(stringResource('settings_clear_failed_body')),
      confirmButton: TextButton({ onClick: p.onDismissClearError, children: Text(stringResource('settings_ok')) }),
    }));
  }
  if (state.pendingImport != null) {
    dialogs.push(AlertDialog({
      key: 'settings:import',
      onDismissRequest: p.onDismissImport,
      title: Text(stringResource('settings_import_confirm_title')),
      text: Text(stringResource('settings_import_confirm_body')),
      confirmButton: TextButton({ onClick: p.onConfirmImport, enabled: !state.backupBusy,
        children: Text(state.backupBusy ? stringResource('settings_importing') : stringResource('settings_import_yes')) }),
      dismissButton: TextButton({ onClick: p.onDismissImport, enabled: !state.backupBusy, children: Text(stringResource('settings_cancel')) }),
    }));
  }
  if (state.showDiscardDialog) {
    dialogs.push(AlertDialog({
      key: 'settings:discard',
      onDismissRequest: p.onDismissDiscard,
      title: Text(stringResource('settings_discard_title')),
      text: Text(stringResource('settings_discard_body')),
      confirmButton: TextButton({ onClick: p.onConfirmDiscard, children: Text(stringResource('settings_discard_yes')) }),
      dismissButton: TextButton({ onClick: p.onDismissDiscard, children: Text(stringResource('settings_discard_no')) }),
    }));
  }
  let body;
  if (state.loading) {
    body = [Text({ text: stringResource('settings_loading'), style: 'bodyLarge', color: 'var(--c-textSecondary)', css: { paddingTop: 'var(--space-lg)' } })];
  } else if (state.loadFailed) {
    body = [
      Text({ text: stringResource('settings_load_failed'), style: 'bodyLarge', color: 'var(--c-textSecondary)', css: { padding: 'var(--space-lg) 0 var(--space-md)' } }),
      Button({ onClick: p.onRetry, children: Text(stringResource('settings_retry')) }),
    ];
  } else {
    body = SettingsEditor(p);
  }
  const screen = ScrollColumn({
    modifier: p.modifier,
    css: { padding: '0 var(--space-screen)', color: 'var(--c-onSurface)' },
    scrollKey: 'settings-detail-scroll',
    children: [
      Row({ verticalAlignment: 'CenterVertically' },
        IconButton({ onClick: p.onBack, css: { width: 'var(--size-touchTarget)', height: 'var(--size-touchTarget)' },
          children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: stringResource('settings_back') }) }),
        Text({ text: stringResource('settings_detail_title'), style: 'headlineSmall', color: 'var(--c-text)' })),
      ...body,
    ],
  });
  screen.append(...dialogs);
  return screen;
}

function SettingsEditor(p) {
  const { state } = p;
  const weekdays = stringResource('settings_weekdays');
  const weekends = stringResource('settings_weekends');
  const out = [
    ...PlanSection(state, p.onPlanMode, p.onExamDate, p.onFinishBy),
    SectionTitle(stringResource('settings_hours')),
    AppValueStepper({
      label: weekdays,
      value: stringResource('settings_hours_value', settingsHour(state.weekdayHours)),
      decreaseContentDescription: stringResource('settings_decrease_hours', weekdays),
      increaseContentDescription: stringResource('settings_increase_hours', weekdays),
      onDecrease: () => p.onWeekdayChange(Math.max(state.weekdayHours - 0.5, 1)),
      onIncrease: () => p.onWeekdayChange(Math.min(state.weekdayHours + 0.5, 14)),
      decreaseEnabled: !state.saving && state.weekdayHours > 1,
      increaseEnabled: !state.saving && state.weekdayHours < 14,
    }),
    AppValueStepper({
      label: weekends,
      value: stringResource('settings_hours_value', settingsHour(state.weekendHours)),
      decreaseContentDescription: stringResource('settings_decrease_hours', weekends),
      increaseContentDescription: stringResource('settings_increase_hours', weekends),
      onDecrease: () => p.onWeekendChange(Math.max(state.weekendHours - 0.5, 1)),
      onIncrease: () => p.onWeekendChange(Math.min(state.weekendHours + 0.5, 16)),
      decreaseEnabled: !state.saving && state.weekendHours > 1,
      increaseEnabled: !state.saving && state.weekendHours < 16,
    }),
    OutlinedTextField({
      key: 'settings:studyPlace',
      value: state.studyPlace,
      onValueChange: p.onStudyPlaceChange,
      enabled: !state.saving,
      singleLine: true,
      label: Text(stringResource('settings_study_spot')),
      placeholder: Text(stringResource('settings_study_spot_hint')),
      class: 'fill-w',
    }),
    SectionTitle(stringResource('settings_focus')),
    Text({ text: stringResource('settings_focus_length'), style: 'bodyMedium' }),
    LengthPicker({
      key: 'settings',
      presets: FOCUS_LENGTH_PRESETS_MINUTES,
      selectedMinutes: state.focusDurationMinutes,
      onSelect: p.onFocusDuration,
      parseCustom: focusLengthMinutesOrNull,
      enabled: !state.saving,
      labels: LengthPickerLabels({
        minutes: (it) => stringResource('settings_focus_minutes', it),
        custom: stringResource('settings_focus_custom'),
        dialogTitle: stringResource('settings_focus_custom_title'),
        fieldLabel: stringResource('settings_focus_custom_field', FOCUS_LENGTH_MIN_MINUTES, FOCUS_LENGTH_MAX_MINUTES),
        ok: stringResource('settings_ok'),
        cancel: stringResource('settings_cancel'),
      }),
    }),
    SwitchRow({
      testTag: 'focus-end-alert', checked: state.focusEndAlert, onChange: p.onFocusEndAlert,
      content: Text({ text: stringResource('settings_focus_end_alert'), style: 'bodyMedium', color: 'var(--c-text)', class: 'weight' }),
    }),
    Button({
      onClick: p.onSave,
      enabled: state.hasUnsavedChanges && !state.saving,
      class: 'fill-w', css: { marginTop: 'var(--space-lg)' },
      shape: 'var(--radius-lg)',
      colors: ButtonDefaults.buttonColors({ containerColor: 'var(--c-brandDeep)', contentColor: 'var(--c-onBrand)' }),
      children: Text(state.saving ? stringResource('settings_saving') : stringResource('settings_save')),
    }),
    state.saveDone || state.saveFailed
      ? TextButton({ onClick: p.onDismissSaveResult, class: 'fill-w', children: Text({
        text: stringResource(state.saveDone ? 'settings_saved' : 'settings_save_failed'),
        color: state.saveDone ? 'var(--c-success)' : 'var(--c-danger)',
      }) })
      : null,
    ...ReminderSection(state, p.onReminderToggle, p.onReminderTime),
    SectionTitle(stringResource('settings_privacy')),
    SwitchRow({
      testTag: 'share-usage', checked: state.shareUsageData, onChange: p.onShareUsageData,
      content: Column({ class: 'weight' },
        Text({ text: stringResource('settings_share_usage'), style: 'bodyMedium', color: 'var(--c-text)' }),
        Text({ text: stringResource('settings_share_usage_detail'), style: 'bodySmall', color: 'var(--c-textSecondary)' })),
    }),
    SectionTitle(stringResource('settings_data')),
    Text({ text: stringResource('settings_backup_detail'), style: 'bodySmall', color: 'var(--c-textSecondary)', css: { paddingBottom: 'var(--space-sm)' } }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' }, class: 'fill-w' },
      OutlinedButton({ onClick: p.onExportBackup, enabled: !state.backupBusy && !state.saving, class: 'weight',
        css: { minHeight: 'var(--size-touchTarget)' }, shape: 'var(--radius-lg)', children: Text(stringResource('settings_export_backup')) }),
      OutlinedButton({ onClick: p.onImportBackup, enabled: !state.backupBusy && !state.saving, class: 'weight',
        css: { minHeight: 'var(--size-touchTarget)' }, shape: 'var(--radius-lg)', children: Text(stringResource('settings_import_backup')) })),
    state.backupMessage != null
      ? TextButton({ onClick: p.onDismissBackupMessage, class: 'fill-w', children: Text({
        text: stringResource({
          [BackupMessage.EXPORTED.name]: 'settings_backup_exported',
          [BackupMessage.EXPORT_FAILED.name]: 'settings_backup_export_failed',
          [BackupMessage.IMPORT_TOO_NEW.name]: 'settings_backup_too_new',
          [BackupMessage.IMPORT_INCOMPATIBLE.name]: 'settings_backup_incompatible',
          [BackupMessage.IMPORT_FAILED.name]: 'settings_backup_import_failed',
        }[state.backupMessage.name]),
        color: state.backupMessage === BackupMessage.EXPORTED ? 'var(--c-success)' : 'var(--c-danger)',
      }) })
      : null,
    Button({
      onClick: p.onRequestClear,
      enabled: !state.saving && !state.clearing,
      class: 'fill-w', css: { marginBottom: 'var(--space-xl)' },
      shape: 'var(--radius-lg)',
      colors: ButtonDefaults.buttonColors({ containerColor: 'var(--c-danger)', contentColor: 'var(--c-onBrand)' }),
      children: Text(stringResource('settings_clear_plan')),
    }),
  ];
  return out.filter(Boolean);
}

/** Row(fillMaxWidth, heightIn(min = touchTarget), toggleable(role = Switch)) { content; Switch(onCheckedChange = null) } */
function SwitchRow({ checked, onChange, content, testTag }) {
  const row = Row({ verticalAlignment: 'CenterVertically', class: 'fill-w', css: { minHeight: 'var(--size-touchTarget)' } },
    content, Switch({ checked, onCheckedChange: null }));
  if (testTag) row.setAttribute('data-testid', testTag);
  return clickable(row, { role: 'switch', checked, onClick: () => onChange(!checked) });
}

/** Settings → Plan: switch mode, edit the exam date and the finish-by date. Saving regenerates future planner-made blocks only. */
function PlanSection(state, onPlanMode, onExamDate, onFinishBy) {
  const today = localToday();
  const minDate = plusDays(today, MIN_FINISH_BY_DAYS_AHEAD);
  const out = [
    SectionTitle(stringResource('settings_plan')),
    Text({ text: stringResource('settings_plan_question'), style: 'bodyMedium', color: 'var(--c-textSecondary)' }),
    Row({ horizontalArrangement: { spacedBy: 'var(--space-sm)' }, css: { paddingTop: 'var(--space-sm)' } },
      FilterChip({ selected: state.planMode === PlanMode.DEADLINE, onClick: () => onPlanMode(PlanMode.DEADLINE), enabled: !state.saving, label: Text(stringResource('settings_plan_yes')) }),
      FilterChip({ selected: state.planMode === PlanMode.STEADY, onClick: () => onPlanMode(PlanMode.STEADY), enabled: !state.saving, label: Text(stringResource('settings_plan_not_yet')) })),
    Text({
      text: stringResource(state.planMode === PlanMode.DEADLINE ? 'settings_plan_deadline_detail' : 'settings_plan_steady_detail'),
      style: 'bodySmall', color: 'var(--c-textSecondary)', css: { paddingTop: 'var(--space-sm)' },
    }),
  ];
  const exam = state.examDate;
  if (exam != null) {
    out.push(DateRow({
      id: 'exam',
      title: stringResource('settings_plan_exam_date'),
      date: exam,
      note: state.examDateIsConfigured && !state.examDateOfficial ? stringResource('settings_plan_exam_expected') : null,
      minDate,
      maxDate: plusMonths(today, 36),
      enabled: !state.saving,
      onDate: onExamDate,
    }));
  }
  const finishBy = state.finishBy;
  if (state.planMode === PlanMode.DEADLINE && finishBy != null) {
    out.push(DateRow({
      id: 'finishBy',
      title: stringResource('settings_plan_finish_by'),
      date: finishBy,
      note: null,
      minDate,
      maxDate: maxDate(exam ?? finishBy, minDate),
      enabled: !state.saving,
      onDate: onFinishBy,
    }));
  }
  return out;
}

function DateRow({ id, title, date, note, minDate, maxDate: max, enabled, onDate }) {
  const editing = remember('settings:dateEditing:' + id, false);
  const openSeq = remember('settings:dateSeq:' + id, 0);
  return observe(editing, (isEditing) => {
    const row = Row({ class: 'fill-w', css: { minHeight: 'var(--size-touchTarget)' }, verticalAlignment: 'CenterVertically' },
      Column({ class: 'weight' },
        Text({ text: title, style: 'bodyLarge', color: 'var(--c-text)' }),
        Text({ text: formatDMMMyyyy(date) + (note != null ? ` · ${note}` : ''), style: 'bodyMedium', color: 'var(--c-textSecondary)' })),
      TextButton({ onClick: () => { openSeq.value += 1; editing.value = true; }, enabled, css: { minHeight: 'var(--size-touchTarget)' },
        children: Text(stringResource('settings_plan_change')) }));
    row.setAttribute('data-testid', 'date-row-' + id);
    if (!isEditing) return row;
    const minMillis = dateToUtcMillis(minDate);
    const maxMillis = dateToUtcMillis(max);
    const pickerState = rememberDatePickerState({
      key: `settings:datePicker:${id}:${openSeq.value}`,
      initialSelectedDateMillis: dateToUtcMillis(date),
      selectableDates: { isSelectableDate: (ms) => ms >= minMillis && ms <= maxMillis },
    });
    const dialog = DatePickerDialog({
      key: 'settings:dateDialog:' + id,
      onDismissRequest: () => { editing.value = false; },
      confirmButton: TextButton({ onClick: () => {
        if (pickerState.selectedDateMillis != null) onDate(utcMillisToDate(pickerState.selectedDateMillis));
        editing.value = false;
      }, children: Text(stringResource('settings_ok')) }),
      dismissButton: TextButton({ onClick: () => { editing.value = false; }, children: Text(stringResource('settings_cancel')) }),
      children: DatePicker({ state: pickerState, showModeToggle: false }),
    });
    return [row, dialog];
  }, { style: { display: 'contents' } });
}

/** Settings → Reminders: the one optional daily "Today's plan" note and its time. Saved immediately. */
function ReminderSection(state, onReminderToggle, onReminderTime) {
  const pickingTime = remember('settings:pickingTime', false);
  const openSeq = remember('settings:timeSeq', 0);
  const minute = state.reminderMinuteOfDay;
  const timeLabel = formatShortTime(minute);
  const out = [
    SectionTitle(stringResource('settings_reminders')),
    SwitchRow({
      testTag: 'daily-reminder', checked: state.reminderEnabled, onChange: onReminderToggle,
      content: Column({ class: 'weight' },
        Text({ text: stringResource('settings_reminder_daily'), style: 'bodyMedium', color: 'var(--c-text)' }),
        Text({ text: stringResource('settings_reminder_daily_detail'), style: 'bodySmall', color: 'var(--c-textSecondary)' })),
    }),
  ];
  if (state.reminderPermissionDenied) {
    out.push(Text({ text: stringResource('settings_reminder_denied'), style: 'bodySmall', color: 'var(--c-textSecondary)', css: { paddingBottom: 'var(--space-sm)' } }));
  }
  out.push(observe(pickingTime, (picking) => {
    const nodes = [];
    if (state.reminderEnabled) {
      const row = Row({ class: 'fill-w', css: { minHeight: 'var(--size-touchTarget)' }, verticalAlignment: 'CenterVertically' },
        Column({ class: 'weight' },
          Text({ text: stringResource('settings_reminder_time'), style: 'bodyLarge', color: 'var(--c-text)' }),
          Text({ text: timeLabel, style: 'bodyMedium', color: 'var(--c-textSecondary)' })),
        TextButton({ onClick: () => { openSeq.value += 1; pickingTime.value = true; }, css: { minHeight: 'var(--size-touchTarget)' },
          children: Text(stringResource('settings_plan_change')) }));
      row.setAttribute('data-testid', 'reminder-time-row');
      nodes.push(row);
    }
    if (picking) {
      const pickerState = rememberTimePickerState({
        key: 'settings:timePicker:' + openSeq.value,
        initialHour: Math.floor(minute / 60), initialMinute: minute % 60, is24Hour: is24HourFormat(),
      });
      nodes.push(AlertDialog({
        key: 'settings:timeDialog',
        onDismissRequest: () => { pickingTime.value = false; },
        title: Text(stringResource('settings_reminder_time')),
        text: TimePicker({ state: pickerState }),
        confirmButton: TextButton({ onClick: () => { onReminderTime(pickerState.hour * 60 + pickerState.minute); pickingTime.value = false; },
          children: Text(stringResource('settings_ok')) }),
        dismissButton: TextButton({ onClick: () => { pickingTime.value = false; }, children: Text(stringResource('settings_cancel')) }),
      }));
    }
    return nodes;
  }, { style: { display: 'contents' } }));
  return out;
}

function SectionTitle(text) {
  return Text({ text, style: 'titleSmall', color: 'var(--c-text)', css: { padding: 'var(--space-lg) 0 var(--space-sm)' } });
}

/** NumberFormat (max 1 fraction digit) of the hours value. */
const settingsHour = (value) => formatOneDecimal(value);

