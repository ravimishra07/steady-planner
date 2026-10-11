// Port of feature/progress/DayLogScreen.kt — DayLogRoute, DayLogScreen, LogStudyButton, DaySessionList, activityLabel.
// LifecycleEventEffect(ON_RESUME) → LaunchedEffect([]) on the destination: NavHost recomposes the destination each
// time it comes back to the top of the stack (and on first show, like ON_RESUME).
import { viewModel, LaunchedEffect, remember, observe } from '../../platform/nav.js';
import {
  C, Column, Row, Box, Spacer, Text, Icon, IconButton, OutlinedButton, HorizontalDivider, AppCard, SessionListItem,
} from '../../design/components.js';
import { StudyActivityType } from '../../domain/StudyPlan.js';
import { formatInsightDate } from '../../domain/StudyAnalytics.js';
import { formatMinuteOfDay } from '../../domain/StudySession.js';
import { DayLogViewModel } from './DayLogViewModel.js';
import { insightDuration } from './ProgressScreen.js';
import { DeleteSessionDialog } from './LogSessionScreen.js';
import { R, stringResource, pluralStringResource } from './strings.js';
import { RouteHost, noShrink } from './_local.js';

const sp = (k) => `var(--space-${k})`;
const sz = (k) => `var(--size-${k})`;

export function DayLogRoute({
  date, examPackRepository, attemptRepository, studySessionRepository, planRepository,
  onBack, onLogStudy, onEditSession, modifier,
}) {
  const vm = viewModel(`day-log-${date}`, () => new DayLogViewModel(date, examPackRepository, attemptRepository, studySessionRepository, planRepository));
  // Back from logging or editing a session: re-read the day.
  LaunchedEffect([], () => { vm.refresh(); });
  return RouteHost(vm.state, (state) => DayLogScreen({
    state,
    onBack,
    onLogStudy: () => onLogStudy(date),
    onEditSession,
    onDeleteSession: (id) => vm.delete(id),
    modifier,
  }));
}

export function DayLogScreen({ state, onBack, onLogStudy, onEditSession, onDeleteSession, modifier }) {
  const pendingDelete = remember('dayLog:pendingDelete', null);
  const header = Row({ class: 'fill-w', css: { padding: `${sp('sm')} ${sp('xs')}` }, verticalAlignment: 'CenterVertically' },
    IconButton({ onClick: onBack, children: Icon({ imageVector: 'AutoMirrored.Filled.ArrowBack', contentDescription: R.string.day_log_back, tint: C.textSecondary }) }),
    Column({ class: 'weight' },
      Text({ text: R.string.day_log_title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold' }),
      Text({ text: formatInsightDate(state.date), style: 'bodySmall', color: C.textMuted }),
    ),
  );
  const log = state.log;
  let body;
  if (state.loading) body = CenteredNote(R.string.insights_loading);
  else if (state.loadFailed) body = CenteredNote(R.string.insights_load_failed);
  else if (log == null || log.entries.length === 0) {
    body = Column({ class: 'weight fill-w', css: { padding: sp('screen') }, verticalArrangement: { spacedBy: sp('lg'), alignment: 'Center' }, horizontalAlignment: 'CenterHorizontally' },
      Text({ text: R.string.day_log_empty, style: 'bodyLarge', color: C.textMuted }),
      LogStudyButton({ onClick: onLogStudy }),
    );
  } else {
    body = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('screen')}` } },
      Text({
        text: pluralStringResource(R.plurals.day_log_summary, log.entries.length, insightDuration(log.totalFocusedMinutes), log.entries.length),
        style: 'bodyMedium', color: C.textSecondary, css: { paddingTop: sp('sm'), paddingBottom: sp('md') },
      }),
      DaySessionList({ entries: log.entries, onEdit: onEditSession, onDelete: (id) => { pendingDelete.value = id; } }),
      LogStudyButton({ onClick: onLogStudy, css: { paddingTop: sp('lg') } }),
      Spacer({ height: sp('xl') }),
    );
    body.setAttribute('data-scroll-key', 'day-log');
    noShrink(body);
  }
  return Column({ modifier, class: 'weight fill-w' },
    header,
    body,
    observe(pendingDelete, (id) => (id == null ? null : DeleteSessionDialog({
      onConfirm: () => { pendingDelete.value = null; onDeleteSession(id); },
      onDismiss: () => { pendingDelete.value = null; },
    })), { style: { display: 'contents' } }),
  );
}

/** "Log study I did offline" (§6.3). Shown on Insights and in the day view. */
export function LogStudyButton({ onClick, class: cls, css }) {
  return OutlinedButton({
    onClick, class: ['fill-w', cls], css: { minHeight: sz('touchTarget'), ...(css || {}) },
    children: [
      Icon({ imageVector: 'Outlined.Add', contentDescription: null, size: sz('smallIcon') }),
      Spacer({ width: sp('sm') }),
      Text(R.string.log_session_open),
    ],
  });
}

/** The day's sessions in one card. Tapping a row edits it; the trailing action deletes it after a confirm. */
export function DaySessionList({ entries, onEdit, onDelete, class: cls }) {
  return AppCard({ class: ['fill-w', cls], children: entries.flatMap((entry, index) => [
    DaySessionRow(entry, onEdit, onDelete),
    index < entries.length - 1 ? HorizontalDivider({ color: C.borderSubtle }) : null,
  ]).filter(Boolean) });
}

function DaySessionRow(entry, onEdit, onDelete) {
  const type = activityLabel(entry.activityType);
  const detail = [type, entry.topicTitle, entry.subjectName].filter((it) => it != null).join(R.string.day_log_separator);
  const time = entry.endMinuteOfDay != null
    ? stringResource(R.string.day_log_time_range, formatMinuteOfDay(entry.startMinuteOfDay), formatMinuteOfDay(entry.endMinuteOfDay))
    : formatMinuteOfDay(entry.startMinuteOfDay);
  return SessionListItem({
    time,
    title: entry.title ?? type,
    detail,
    duration: insightDuration(entry.focusedMinutes),
    onClick: () => onEdit(entry.sessionId),
    trailing: IconButton({
      onClick: () => onDelete(entry.sessionId),
      children: Icon({ imageVector: 'Outlined.Delete', contentDescription: R.string.log_session_delete, tint: C.textMuted, size: sz('smallIcon') }),
    }),
  });
}

function CenteredNote(text) {
  return Box({ class: 'weight fill-w', css: { padding: sp('screen') }, contentAlignment: 'Center' },
    Text({ text, style: 'bodyLarge', color: C.textMuted }));
}

/** internal fun activityLabel(type): the label string (R.string) for a StudyActivityType. */
export function activityLabel(type) {
  switch (type) {
    case StudyActivityType.LEARN: return R.string.day_log_type_learn;
    case StudyActivityType.REVISION: return R.string.day_log_type_revision;
    case StudyActivityType.PRACTICE: return R.string.day_log_type_practice;
    case StudyActivityType.MOCK_TEST: return R.string.day_log_type_mock;
    case StudyActivityType.CLASS: return R.string.day_log_type_class;
    case StudyActivityType.CUSTOM: return R.string.day_log_type_custom;
    default: return R.string.day_log_type_break;
  }
}
