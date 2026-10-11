// Port of feature/progress/ProgressScreen.kt — Insights tab (ProgressRoute, ProgressScreen) + insightDuration.
import { h } from '../../platform/h.js';
import { viewModel, LaunchedEffect, remember, observe } from '../../platform/nav.js';
import {
  C, alpha, Column, Row, Box, Spacer, Text, Icon, Surface, Button, OutlinedButton, TextButton, IconButton,
  HorizontalDivider, Checkbox, AlertDialog, AppCard, ProLockCard, ProLockIcon, ButtonDefaults, clickable,
} from '../../design/components.js';
import { chunked, roundToInt, coerceAtLeast, formatFixed } from '../../domain/kt.js';
import { InsightPeriod, formatInsightDate } from '../../domain/StudyAnalytics.js';
import { SubjectStatus } from '../../domain/Metrics.js';
import { ProgressViewModel } from './ProgressViewModel.js';
import { isProPeriod } from './ProgressUiState.js';
import { LogStudyButton } from './DayLogScreen.js';
import { R, stringResource, pluralStringResource } from './strings.js';
import { RouteHost, noShrink, fixedHeight, formatPattern } from './_local.js';

const sp = (k) => `var(--space-${k})`;
const sz = (k) => `var(--size-${k})`;
const rad = (k) => `var(--radius-${k})`;

export function ProgressRoute({
  examPackRepository, attemptRepository, planRepository, studySessionRepository, topicProgressRepository,
  targetSyllabusRepository, rollingPlanRepository, studyPreferenceRepository, revisionRepository,
  availabilityRepository, chapterStageRepository, mockTestRepository, proRepository,
  modifier, onOpenPaywall = () => {}, onOpenSettings = () => {}, onGoToday = () => {}, onOpenDay = () => {},
  onLogStudy = () => {}, onOpenTests = () => {},
}) {
  const vm = viewModel('ProgressViewModel', () => new ProgressViewModel(
    examPackRepository, attemptRepository, planRepository, studySessionRepository,
    topicProgressRepository, targetSyllabusRepository, rollingPlanRepository, studyPreferenceRepository,
    revisionRepository, availabilityRepository, chapterStageRepository, mockTestRepository, proRepository,
  ));
  LaunchedEffect([], () => { vm.refresh(); });
  return RouteHost(vm.state, (state) => {
    LaunchedEffect([state.showSoftPrompt], () => {
      if (state.showSoftPrompt) { vm.onSoftPromptShown(); onOpenPaywall(); }
    });
    return ProgressScreen({
      state,
      onPeriodSelected: (p) => vm.selectPeriod(p),
      onOpenPaywall, onOpenSettings, onGoToday, onOpenDay, onLogStudy, onOpenTests,
      onManageTarget: () => vm.openTargetManager(),
      onCloseTargetManager: () => vm.closeTargetManager(),
      onToggleSection: (key) => vm.toggleSectionTarget(key),
      modifier,
    });
  });
}

export function ProgressScreen({
  state, modifier, onPeriodSelected = () => {}, onOpenPaywall = () => {}, onOpenSettings = () => {},
  onGoToday = () => {}, onOpenDay = () => {}, onLogStudy = () => {}, onOpenTests = () => {},
  onManageTarget = () => {}, onCloseTargetManager = () => {}, onToggleSection = () => {},
}) {
  const centered = (text, padded) => Box({ modifier, class: 'weight fill-w', contentAlignment: 'Center', css: padded ? { padding: sp('screen') } : undefined },
    Text({ text, style: padded ? 'bodyLarge' : undefined, color: C.textMuted }));
  if (!state.hasPlan && !state.loading) return centered(R.string.insights_no_plan, true);
  if (!state.loading && state.loadFailed) return centered(R.string.insights_load_failed, true);
  if (state.loading || state.data == null) return centered(R.string.insights_loading, false);

  const data = state.data;
  if (!data.hasStudyHistory) return EmptyProgressScreen({ data, onGoToday, onLogStudy, onOpenTests, modifier });

  const scroll = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('screen')}` } },
    Row({ class: 'fill-w', css: { paddingTop: sp('sm') }, verticalAlignment: 'CenterVertically', horizontalArrangement: 'SpaceBetween' },
      Text({ text: R.string.insights_title, style: 'headlineLarge', color: C.text, fontWeight: 'Bold' }),
      IconButton({ onClick: onOpenSettings, children: Icon({ imageVector: 'Outlined.Settings', contentDescription: R.string.insights_settings, tint: C.textSecondary }) }),
    ),
    LogStudyButton({ onClick: onLogStudy, css: { paddingTop: sp('sm') } }),
    // Progress first (§5.5): what got done this week, then syllabus and revision. Hours come after.
    InsightsSection({ title: R.string.insights_progress_week, subtitle: R.string.insights_progress_week_subtitle, content: () => WeekProgressCard(data) }),
    InsightsSection({ title: R.string.insights_syllabus, subtitle: R.string.insights_syllabus_subtitle, content: () => SyllabusCard(data, onManageTarget) }),
    InsightsSection({ title: R.string.insights_subjects, subtitle: R.string.insights_subjects_subtitle, content: () => SubjectsCard(data.subjects) }),
    InsightsSection({ title: R.string.insights_revision, subtitle: R.string.insights_revision_subtitle, content: () => RevisionCard(data) }),
    InsightsSection({ title: R.string.tests_title, subtitle: R.string.insights_tests_subtitle, content: () => TestsCard(state.mostLost, state.proUnlocked, onOpenTests, onOpenPaywall) }),
    PeriodSelector({
      selected: state.period,
      unlocked: state.proUnlocked,
      onSelect: (period) => (isProPeriod(period) && !state.proUnlocked ? onOpenPaywall() : onPeriodSelected(period)),
    }),
    InsightsSection({ title: periodTitle(state.period), subtitle: periodSubtitle(state.period), content: () => QuickStats(data) }),
    InsightsSection({ title: R.string.insights_consistency, subtitle: R.string.insights_consistency_subtitle, content: () => ConsistencyCard(data, onOpenDay) }),
    InsightsSection({ title: R.string.insights_study_time, subtitle: R.string.insights_study_time_subtitle, content: () => StudyTimeCard(data) }),
    InsightsSection({ title: R.string.insights_plan_actual, subtitle: R.string.insights_plan_actual_subtitle, content: () => PlanActualCard(data) }),
    InsightsSection({
      title: data.forecast.hasTarget ? R.string.insights_forecast : R.string.insights_forecast_steady,
      subtitle: data.forecast.hasTarget ? R.string.insights_forecast_subtitle : R.string.insights_forecast_steady_subtitle,
      content: () => (state.proUnlocked
        ? ForecastCard(data)
        : ProLockCard({ title: R.string.pro_lock_forecast_title, body: R.string.pro_lock_forecast_body, actionLabel: R.string.pro_lock_action, onUnlock: onOpenPaywall })),
    }),
    InsightsSection({ title: R.string.insights_remaining, subtitle: R.string.insights_remaining_subtitle, content: () => RemainingCard(data.subjects, data.syllabusRemainingSections) }),
    InsightsSection({ title: R.string.insights_pattern, subtitle: R.string.insights_pattern_subtitle, content: () => PatternCard(data) }),
    Spacer({ height: sp('xl') }),
  );
  scroll.setAttribute('data-scroll-key', 'progress');
  noShrink(scroll);
  return Column({ modifier, class: 'weight fill-w' },
    scroll,
    state.showTargetManager ? TargetManagerDialog({ sections: state.targetSections, onToggle: onToggleSection, onDismiss: onCloseTargetManager }) : null,
  );
}

function EmptyProgressScreen({ data, onGoToday, onLogStudy, onOpenTests, modifier }) {
  const banner = Surface({
    class: 'fill-w', css: { marginTop: sp('xl') }, shape: rad('large'), color: C.selectionContainer, contentColor: C.onSelectionContainer,
    children: Row({ css: { padding: sp('lg') }, horizontalArrangement: { spacedBy: sp('md') }, verticalAlignment: 'CenterVertically' },
      Icon({ imageVector: 'Outlined.ShowChart', size: sz('standardIcon') }),
      Column({ class: 'weight', verticalArrangement: { spacedBy: sp('xs') } },
        Text({ text: R.string.progress_empty_banner_title, style: 'titleSmall' }),
        Text({ text: R.string.progress_empty_banner_body, style: 'bodySmall' }),
      ),
    ),
  });
  const facts = Column({ class: 'fill-w', css: { paddingTop: sp('xxl') }, verticalArrangement: { spacedBy: sp('md') } },
    ProgressFact(R.string.progress_sections_covered, stringResource(R.string.progress_fraction, data.syllabusCompletedSections, data.syllabusTotalSections)),
    ProgressFact(R.string.progress_days_studied, String(data.studyDaysInPeriod)),
    Button({ onClick: onGoToday, class: 'fill-w', css: { height: sz('ctaHeight') }, children: Text(R.string.progress_start_next) }),
    LogStudyButton({ onClick: onLogStudy }),
    OutlinedButton({ onClick: onOpenTests, class: 'fill-w', css: { height: sz('touchTarget') }, children: Text(R.string.tests_open) }),
  );
  const scroll = Column({ class: 'weight fill-w scroll-y', css: { padding: `0 ${sp('lg')}` } }, banner, facts, Spacer({ height: sp('xxl') }));
  scroll.setAttribute('data-scroll-key', 'progress-empty');
  noShrink(scroll);
  return Column({ modifier, class: 'weight fill-w' }, scroll);
}

function ProgressFact(label, value) {
  return Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween' },
    Text({ text: label, style: 'bodyMedium' }),
    Text({ text: value, style: 'titleSmall' }),
  );
}

function PeriodSelector({ selected, unlocked, onSelect }) {
  return Surface({
    class: 'fill-w', shape: rad('md'), color: C.surfaceControl,
    children: Row({ class: 'fill-w', css: { padding: sp('xs') } },
      ...InsightPeriod.entries.map((period) => fixedHeight(TextButton({
        onClick: () => onSelect(period),
        class: 'weight',
        css: { height: sz('dayControl') },
        shape: rad('sm'),
        colors: ButtonDefaults.textButtonColors({
          containerColor: period === selected ? C.selectionContainer : 'transparent',
          contentColor: period === selected ? C.onSelectionContainer : C.textMuted,
        }),
        children: [
          isProPeriod(period) && !unlocked ? ProLockIcon({ css: { marginRight: sp('xs') } }) : null,
          Text({ text: periodLabel(period), style: 'labelMedium', fontWeight: 'SemiBold', maxLines: 1 }),
        ].filter(Boolean),
      }))),
    ),
  });
}

function InsightsSection({ title, subtitle, content }) {
  return Column({ class: 'fill-w', css: { paddingTop: sp('xxl') } },
    Text({ text: title, style: 'titleLarge', color: C.text, fontWeight: 'SemiBold' }),
    Text({ text: subtitle, style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('xs'), paddingBottom: sp('md') } }),
    content(),
  );
}

function QuickStats(data) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w' },
    StatLine({
      leftLabel: R.string.insights_total_studied, leftValue: insightDuration(data.hasStudyInPeriod ? data.totalStudiedMinutes : null),
      rightLabel: R.string.insights_sessions, rightValue: data.hasStudyInPeriod ? String(data.completedSessionCount) : '—',
    }),
    HorizontalDivider({ color: C.borderSubtle }),
    StatLine({
      leftLabel: R.string.insights_avg_day, leftValue: insightDuration(data.averagePerActiveDayMinutes),
      rightLabel: R.string.insights_best_day, rightValue: insightDuration(data.bestDay?.minutes ?? null),
      rightNote: data.bestDay != null ? formatInsightDate(data.bestDay.date) : null,
    }),
  ) });
}

/** Chapters learned, lectures done (with coaching counts) and revisions done, all over the last 7 days. */
function WeekProgressCard(data) {
  return AppCard({ class: 'fill-w', children: Row({ class: 'fill-w', css: { padding: `${sp('md')} ${sp('lg')}` }, horizontalArrangement: { spacedBy: sp('lg') } },
    StatItem(R.string.insights_chapters_learned, String(data.chaptersLearnedThisWeek)),
    data.lecturesDone != null ? StatItem(R.string.insights_lectures_done, String(data.lecturesDone)) : null,
    StatItem(R.string.insights_revisions_done, String(data.revision.completedThisWeek)),
  ) });
}

function StatLine({ leftLabel, leftValue, rightLabel, rightValue, rightNote = null }) {
  return Row({ class: 'fill-w', css: { padding: `${sp('md')} ${sp('lg')}` }, horizontalArrangement: { spacedBy: sp('lg') } },
    StatItem(leftLabel, leftValue),
    StatItem(rightLabel, rightValue, rightNote),
  );
}

function StatItem(label, value, note = null) {
  return Column({ class: 'weight' },
    Text({ text: value, style: 'headlineSmall', color: C.text, fontWeight: 'Bold' }),
    Text({ text: label, style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('xs') } }),
    note != null ? Text({ text: note, style: 'labelSmall', color: C.textSecondary, css: { paddingTop: sp('xxs') } }) : null,
  );
}

function ConsistencyCard(data, onOpenDay) {
  const weeks = chunked(data.heatmapDays, 7);
  let last = -1;
  data.heatmapDays.forEach((d, i) => { if (d.minutes > 0) last = i; });
  const selectedIndex = remember(`consistency:selected:${data.period.name}`, Math.max(last, 0));
  return AppCard({ class: 'fill-w', children: observe(selectedIndex, (sel) => Column({ class: 'fill-w', css: { padding: sp('lg') } },
    Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween' },
      ...monthLabels(weeks).map((label) => Text({ text: label, style: 'labelSmall', color: alpha(C.textMuted, 0.55) })),
    ),
    Row({ class: 'fill-w', css: { paddingTop: sp('sm') }, horizontalArrangement: { spacedBy: sp('xs') } },
      ...weeks.map((days, week) => Column({ class: 'weight', verticalArrangement: { spacedBy: sp('xs') } },
        ...days.map((insight, day) => {
          const index = week * 7 + day;
          const cell = h('div', { style: { width: '100%', aspectRatio: '1', borderRadius: 'var(--cal-meterHeight)', background: heatColor(insight.minutes), flex: 'none' } });
          return clickable(cell, { onClick: () => { selectedIndex.value = index; } });
        }),
      )),
    ),
    ...(data.hasStudyHistory ? selectedDay(data, data.heatmapDays[sel] ?? null, onOpenDay) : [EmptyData(R.string.insights_empty_history)]),
    Row({ css: { paddingTop: sp('md') }, horizontalArrangement: { spacedBy: sp('lg') } },
      Legend(C.surfaceControl, R.string.insights_less),
      Legend(C.brandDeep, R.string.insights_more),
    ),
    HorizontalDivider({ css: { marginTop: sp('md') }, color: C.borderSubtle }),
    Row({ class: 'fill-w', css: { paddingTop: sp('md') }, horizontalArrangement: { spacedBy: sp('lg') } },
      SummaryItem(R.string.insights_current_streak,
        data.streakBroken ? R.string.insights_new_streak_today
          : data.hasStudyHistory ? streakLabel(data.currentStreak) : R.string.insights_no_value),
      SummaryItem(R.string.insights_active_this_period, data.hasStudyInPeriod
        ? pluralStringResource(R.plurals.insights_active_days_fraction, data.periodDays.length, data.studyDaysInPeriod, data.periodDays.length)
        : R.string.insights_no_value),
    ),
  ), { class: 'col fill-w' }) });
}

function selectedDay(data, selected, onOpenDay) {
  if (selected == null) return [];
  const out = [Surface({
    class: 'fill-w', shape: rad('sm'), color: C.elevated, css: { marginTop: sp('md') },
    children: Row({ class: 'fill-w', css: { padding: sp('sm') }, horizontalArrangement: 'SpaceBetween' },
      Column({},
        Text({ text: formatInsightDate(selected.date), style: 'labelMedium', color: C.text, fontWeight: 'SemiBold' }),
        Text({ text: selected.minutes > 0 ? insightDuration(selected.minutes) : R.string.insights_no_study_this_day, style: 'bodySmall', color: C.textSecondary }),
      ),
      selected.minutes > 0
        ? Text({ text: pluralStringResource(R.plurals.insights_sessions_count, selected.sessions, selected.sessions), style: 'bodySmall', color: C.textSecondary, css: { alignSelf: 'center' } })
        : null,
    ),
  })];
  if (selected.sessions > 0) {
    out.push(TextButton({ onClick: () => onOpenDay(selected.date), contentPadding: sp('none'), children: Text({ text: R.string.insights_view_day, color: C.brandSoft }) }));
  }
  return out;
}

function Legend(color, label) {
  return Row({ verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: sp('xs') } },
    h('div', { style: { width: sz('legendDot'), height: sz('legendDot'), borderRadius: 'var(--cal-meterHeight)', background: color, flex: 'none' } }),
    Text({ text: label, style: 'labelSmall', color: C.textMuted }),
  );
}

function SummaryItem(label, value) {
  return Column({ class: 'weight' },
    Text({ text: value, style: 'titleMedium', color: C.text, fontWeight: 'SemiBold' }),
    Text({ text: label, style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('xxs') } }),
  );
}

function monthLabels(weeks) {
  let lastMonth = -1;
  const out = [];
  for (const week of weeks) {
    const date = week[0]?.date;
    if (date == null) continue;
    const month = Number(date.slice(5, 7));
    if (month !== lastMonth) { lastMonth = month; out.push(formatPattern(date, 'MMM')); }
  }
  return out;
}

function heatColor(minutes) {
  if (minutes <= 0) return C.surfaceControl;
  if (minutes < 30) return C.brandContainer;
  if (minutes < 90) return alpha(C.brandDeep, 0.72);
  if (minutes < 180) return C.brand;
  return C.brandSoft;
}

function StudyTimeCard(data) {
  const points = data.trendDays;
  let chart;
  if (!data.hasStudyInPeriod || !points.some((it) => it.minutes > 0)) {
    chart = EmptyData(R.string.insights_empty_history);
  } else {
    const maxMinutes = coerceAtLeast(Math.max(...points.map((it) => it.minutes)), 1);
    const lastIndex = points.length - 1;
    chart = Row({ class: 'fill-w', css: { height: sz('progressChartTall') }, horizontalArrangement: { spacedBy: sp('sm') }, verticalAlignment: 'Bottom' },
      ...points.map((point, index) => Column({ class: 'weight', css: { height: '100%' }, horizontalAlignment: 'CenterHorizontally', verticalArrangement: 'Bottom' },
        Text({ text: insightDuration(point.minutes), style: 'labelSmall', color: C.textMuted, textAlign: 'Center' }),
        Spacer({ height: sp('xs') }),
        h('div', { style: {
          width: '72%', flex: 'none', height: `${Math.max(82 * point.minutes / maxMinutes, 4)}px`,
          borderRadius: `${rad('sm')} ${rad('sm')} 0 0`, background: index === lastIndex ? C.brand : C.brandDeep,
        } }),
        Text({ text: trendLabel(point, data.period), style: 'labelSmall', color: index === lastIndex ? C.brandSoft : C.textMuted, css: { paddingTop: sp('xs') }, textAlign: 'Center' }),
      )),
    );
  }
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: sp('lg') } },
    chart,
    Text({
      text: data.hasStudyInPeriod ? stringResource(R.string.insights_total_period, insightDuration(data.totalStudiedMinutes)) : R.string.insights_start_studying,
      style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('md') },
    }),
  ) });
}

function PlanActualCard(data) {
  const body = data.plannedMinutes === 0
    ? [EmptyData(R.string.insights_no_planned_blocks)]
    : [
      Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween', verticalAlignment: 'Bottom' },
        MetricPair(R.string.insights_planned, insightDuration(data.plannedMinutes), C.text, 'Start', { flexShrink: '0' }),
        MetricPair(R.string.insights_completed_planned, insightDuration(data.completedPlannedMinutes), C.brandSoft, 'End'),
      ),
      ProgressBar(data.planPercent ?? 0, C.brand, { marginTop: sp('md') }),
      Text({
        text: data.planPercent != null ? stringResource(R.string.insights_plan_percent, data.planPercent) : R.string.insights_no_actual_yet,
        style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('sm') },
      }),
      data.extraStudyMinutes > 0
        ? Text({ text: stringResource(R.string.insights_extra_study, insightDuration(data.extraStudyMinutes)), style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('xs') } })
        : null,
    ];
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: sp('lg') } }, ...body) });
}

/** css: Compose Row measures children in order, so a leading pair keeps its width (flexShrink 0). */
function MetricPair(label, value, valueColor = C.text, align = 'Start', css) {
  return Column({ horizontalAlignment: align, css },
    Text({ text: label, style: 'labelSmall', color: C.textMuted }),
    Text({ text: value, style: 'titleLarge', color: valueColor, fontWeight: 'Bold', css: { paddingTop: sp('xs') } }),
  );
}

function SyllabusCard(data, onManageTarget) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: sp('lg') } },
    Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween', verticalAlignment: 'Bottom' },
      Text({ text: pluralStringResource(R.plurals.insights_topics_fraction, data.syllabusTotalSections, data.syllabusCompletedSections, data.syllabusTotalSections), style: 'headlineSmall', color: C.text, fontWeight: 'Bold' }),
      Text({ text: stringResource(R.string.insights_covered, data.syllabusPercent), style: 'titleMedium', color: C.brandSoft, fontWeight: 'SemiBold' }),
    ),
    Row({ class: 'fill-w', css: { paddingTop: sp('sm') }, horizontalArrangement: 'SpaceBetween' },
      Text({ text: pluralStringResource(R.plurals.insights_remaining_count, data.syllabusRemainingSections, data.syllabusRemainingSections), style: 'bodySmall', color: C.textMuted }),
      TextButton({ onClick: onManageTarget, contentPadding: sp('none'), children: Text({ text: R.string.insights_manage_target, color: C.brandSoft }) }),
    ),
    ProgressBar(data.syllabusPercent, C.brand, { marginTop: sp('xs') }),
    data.excludedSectionCount > 0
      ? Text({ text: pluralStringResource(R.plurals.insights_target_of_official, data.officialSectionCount, data.syllabusTotalSections, data.officialSectionCount), style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('sm') } })
      : null,
  ) });
}

function ForecastCard(data) {
  const forecast = data.forecast;
  const earliest = forecast.forecastEarliestDate;
  const latest = forecast.forecastLatestDate;
  const paceRange = earliest != null && latest != null
    ? stringResource(R.string.insights_forecast_range, formatInsightDate(earliest), formatInsightDate(latest))
    : R.string.insights_not_enough_history;
  const body = [
    PlanFitLines(data),
    Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween' },
      forecast.hasTarget ? MetricPair(R.string.insights_target_date, formatInsightDate(forecast.targetDate), C.text, 'Start', { flexShrink: '0' }) : null,
      MetricPair(R.string.insights_at_current_pace, paceRange, forecast.hasTarget ? C.warning : C.text, forecast.hasTarget ? 'End' : 'Start'),
    ),
  ];
  if (!forecast.hasReliablePace) {
    body.push(Text({ text: R.string.insights_forecast_insufficient, style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('lg') } }));
  } else if (!forecast.hasTarget) {
    body.push(
      Text({ text: stringResource(R.string.insights_forecast_evidence, forecast.evidenceActiveDays, forecast.evidenceWindowDays), style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('lg') } }),
      Text({ text: R.string.insights_forecast_note, style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('md') } }),
    );
  } else {
    const delta = forecast.daysDelta ?? 0;
    body.push(
      Text({
        text: delta > 0 ? pluralStringResource(R.plurals.insights_days_behind, delta, delta)
          : delta < 0 ? pluralStringResource(R.plurals.insights_days_ahead, -delta, -delta)
            : R.string.insights_on_time,
        style: 'titleMedium', color: delta > 0 ? C.warning : C.success, fontWeight: 'SemiBold', css: { paddingTop: sp('lg') },
      }),
      Surface({
        shape: rad('md'), color: alpha(C.warningTint, 0.5), css: { marginTop: sp('md') },
        children: Column({ css: { padding: sp('md') } },
          Text({ text: stringResource(R.string.insights_to_finish_by, formatInsightDate(forecast.targetDate)), style: 'labelSmall', color: C.textSecondary }),
          Text({
            text: (forecast.extraMinutesPerDay ?? 0) > 0 ? stringResource(R.string.insights_extra_per_day, insightDuration(forecast.extraMinutesPerDay)) : R.string.insights_current_pace_works,
            style: 'titleLarge', color: C.text, fontWeight: 'Bold', css: { paddingTop: sp('xs') },
          }),
        ),
      }),
      Row({ css: { paddingTop: sp('md') }, horizontalArrangement: { spacedBy: sp('xl') } },
        MetricPair(R.string.insights_recent_average, insightDuration(forecast.recentAverageMinutesPerDay), C.text, 'Start', { flexShrink: '0' }),
        MetricPair(R.string.insights_required_pace, insightDuration(forecast.requiredMinutesPerDay)),
      ),
      Text({ text: stringResource(R.string.insights_forecast_evidence, forecast.evidenceActiveDays, forecast.evidenceWindowDays), style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('sm') } }),
      Text({ text: R.string.insights_forecast_note, style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('md') } }),
    );
  }
  return Surface({
    class: 'fill-w', shape: rad('lg'), color: forecast.hasTarget ? C.warningRow : C.elevated,
    children: Column({ class: 'fill-w', css: { padding: sp('lg') } }, ...body),
  });
}

/**
 * What the plan says about finish-by (deadline mode only, never in steady mode), and how the hours are
 * measured: the student's own pace once it is known, otherwise a plain "estimate" note.
 */
function PlanFitLines(data) {
  const fit = data.fit;
  let fitLine = null;
  if (fit != null) {
    const hours = coerceAtLeast(roundToInt(fit.shortByMinutes / 60.0), 1);
    fitLine = Text({
      text: fit.fits
        ? stringResource(R.string.insights_fit_ok, formatInsightDate(fit.finishBy))
        : stringResource(fit.calibrated ? R.string.insights_fit_short_calibrated : R.string.insights_fit_short, hours, formatInsightDate(fit.finishBy)),
      style: 'titleSmall', color: fit.fits ? C.success : C.warning, fontWeight: 'SemiBold',
    });
  }
  return Column({ css: { paddingBottom: sp('md') }, verticalArrangement: { spacedBy: sp('xs') } },
    fitLine,
    Text({
      text: data.calibration.active
        ? stringResource(R.string.insights_calibration_pace, formatFixed(data.calibration.rounded, 1))
        : R.string.insights_estimate_note,
      style: 'bodySmall', color: C.textSecondary,
    }),
  );
}

function SubjectsCard(subjects) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w' },
    ...subjects.flatMap((subject, index) => [SubjectRow(subject), index < subjects.length - 1 ? HorizontalDivider({ color: C.borderSubtle }) : null]),
  ) });
}

function SubjectRow(subject) {
  const expanded = remember(`subject:expanded:${subject.key}`, false);
  return observe(expanded, (open) => {
    const col = Column({ class: 'fill-w', css: { padding: `${sp('md')} ${sp('lg')}` } },
      Row({ class: 'fill-w', horizontalArrangement: 'SpaceBetween' },
        Text({ text: subject.name, style: 'titleSmall', color: C.text, fontWeight: 'SemiBold' }),
        Row({ horizontalArrangement: { spacedBy: sp('sm') } },
          Text({ text: stringResource(R.string.insights_percent_value, subject.percent), style: 'bodySmall', color: C.textSecondary }),
          subject.status != null ? Text({ text: subjectStatusLabel(subject.status), style: 'bodySmall', color: subjectStatusColor(subject.status), fontWeight: 'SemiBold' }) : null,
        ),
      ),
      ProgressBar(subject.percent, subject.status != null ? subjectStatusColor(subject.status) : C.brandSoft, { marginTop: sp('sm') }),
      open ? Text({ text: pluralStringResource(R.plurals.insights_subject_detail, subject.totalSections, subject.completedSections, subject.totalSections), style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('sm') } }) : null,
    );
    return clickable(col, { onClick: () => { expanded.value = !expanded.value; } });
  }, { class: 'col fill-w' });
}

function RemainingCard(subjects, totalRemaining) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: `0 ${sp('lg')}` } },
    Text({ text: pluralStringResource(R.plurals.insights_topics_left, totalRemaining, totalRemaining), style: 'headlineSmall', color: C.text, fontWeight: 'Bold', css: { paddingTop: sp('lg'), paddingBottom: sp('sm') } }),
    ...subjects.flatMap((subject, i) => [
      Row({ class: 'fill-w', css: { padding: `${sp('md')} 0` }, horizontalArrangement: 'SpaceBetween' },
        Text({ text: subject.name, style: 'bodyMedium', color: C.textSecondary }),
        Text({ text: pluralStringResource(R.plurals.insights_left, subject.remainingSections, subject.remainingSections), style: 'bodyMedium', color: C.text, fontWeight: 'SemiBold' }),
      ),
      i !== subjects.length - 1 ? HorizontalDivider({ color: C.borderSubtle }) : null,
    ]),
  ) });
}

function RevisionCard(data) {
  const showDue = remember('revision:showDue', false);
  return AppCard({ class: 'fill-w', children: observe(showDue, (show) => Column({ class: 'fill-w', css: { padding: sp('lg') } },
    Row({ class: 'fill-w', horizontalArrangement: { spacedBy: sp('sm') } },
      RevisionMetric(R.string.insights_due_now, data.revision.dueNow),
      RevisionMetric(R.string.insights_due_week, data.revision.dueThisWeek),
      RevisionMetric(R.string.insights_completed_week, data.revision.completedThisWeek),
    ),
    data.revision.overdue > 0
      ? Text({ text: pluralStringResource(R.plurals.insights_revision_overdue, data.revision.overdue, data.revision.overdue), style: 'bodySmall', color: C.warning, css: { paddingTop: sp('sm') } })
      : null,
    ...(data.revision.onTimePercent == null
      ? [EmptyData(R.string.insights_no_revision_history)]
      : [
        Row({ class: 'fill-w', css: { paddingTop: sp('lg') }, horizontalArrangement: 'SpaceBetween' },
          Text({ text: R.string.insights_revision_on_time, style: 'bodySmall', color: C.textSecondary }),
          Text({ text: stringResource(R.string.insights_percent_value, data.revision.onTimePercent), style: 'bodySmall', color: C.brandSoft, fontWeight: 'SemiBold' }),
        ),
        ProgressBar(data.revision.onTimePercent, C.brand, { marginTop: sp('sm') }),
      ]),
    ...(data.revision.dueTitles.length > 0
      ? [
        TextButton({ onClick: () => { showDue.value = !showDue.value; }, class: 'fill-w', children: Text(R.string.insights_revision_action) }),
        ...(show ? data.revision.dueTitles.map((title) => Text({ text: stringResource(R.string.insights_bullet_item, title), style: 'bodySmall', color: C.textSecondary, css: { paddingTop: sp('xs') } })) : []),
      ]
      : []),
  ), { class: 'col fill-w' }) });
}

function TestsCard(mostLost, unlocked, onOpenTests, onOpenPaywall) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: sp('lg') } },
    ...(unlocked ? MostLostChapters(mostLost) : MostLostLocked(onOpenPaywall)),
    OutlinedButton({ onClick: onOpenTests, class: 'fill-w', css: { paddingTop: sp('md') }, children: Text(R.string.tests_open) }),
  ) });
}

/** Free tier: what "most-lost chapters" is, and the way to unlock it. */
function MostLostLocked(onOpenPaywall) {
  return [
    Row({ verticalAlignment: 'CenterVertically', horizontalArrangement: { spacedBy: sp('sm') } },
      ProLockIcon(),
      Text({ text: R.string.insights_most_lost, style: 'titleSmall', color: C.text, fontWeight: 'SemiBold' }),
    ),
    Text({ text: R.string.pro_lock_most_lost_body, style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('xs') } }),
    TextButton({ onClick: onOpenPaywall, children: Text(R.string.pro_lock_action) }),
  ];
}

// Pro: shown only when unlocked (TestsCard).
function MostLostChapters(mostLost) {
  const title = Text({ text: R.string.insights_most_lost, style: 'titleSmall', color: C.text, fontWeight: 'SemiBold' });
  if (mostLost.length === 0) {
    return [title, Text({ text: R.string.insights_most_lost_empty, style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('xs') } })];
  }
  return [title, ...mostLost.map((chapter) => Row({ class: 'fill-w', css: { paddingTop: sp('sm') }, horizontalArrangement: 'SpaceBetween' },
    Text({ text: chapter.title, style: 'bodyMedium', color: C.textSecondary, class: 'weight' }),
    Text({ text: pluralStringResource(R.plurals.insights_most_lost_count, chapter.count, chapter.count), style: 'bodySmall', color: C.warning, fontWeight: 'SemiBold' }),
  ))];
}

function RevisionMetric(label, value) {
  return Surface({ class: 'weight', shape: rad('sm'), color: C.surfaceControl,
    children: Column({ css: { padding: sp('sm') } },
      Text({ text: value > 0 ? String(value) : R.string.insights_no_value, style: 'titleLarge', color: C.text, fontWeight: 'Bold' }),
      Text({ text: label, style: 'labelSmall', color: C.textMuted, css: { paddingTop: sp('xs') } }),
    ) });
}

function PatternCard(data) {
  return AppCard({ class: 'fill-w', children: Column({ class: 'fill-w', css: { padding: `0 ${sp('lg')}` } },
    ...PatternRow(R.string.insights_average_session, insightDuration(data.averageSessionMinutes)),
    ...PatternRow(R.string.insights_longest_session, insightDuration(data.longestSessionMinutes)),
    ...(data.averageBreakMinutes != null ? PatternRow(R.string.insights_average_break, insightDuration(data.averageBreakMinutes)) : []),
    ...PatternRow(R.string.insights_study_days, data.hasStudyHistory ? pluralStringResource(R.plurals.insights_days_count, data.studyDaysInPeriod, data.studyDaysInPeriod) : R.string.insights_no_value),
    ...PatternRow(R.string.insights_longest_streak, data.hasStudyHistory ? streakLabel(data.longestStreak) : R.string.insights_no_value, true),
  ) });
}

function PatternRow(label, value, last = false) {
  return [
    Row({ class: 'fill-w', css: { padding: `${sp('md')} 0` }, horizontalArrangement: 'SpaceBetween' },
      Text({ text: label, style: 'bodySmall', color: C.textSecondary }),
      Text({ text: value, style: 'bodySmall', color: C.text, fontWeight: 'SemiBold' }),
    ),
    !last ? HorizontalDivider({ color: C.borderSubtle }) : null,
  ];
}

/** css: outer spacing (Modifier.padding before the clip/background = margin). */
function ProgressBar(percent, color, css = {}) {
  const pct = Math.min(Math.max(percent, 0), 100);
  return h('div', { style: { width: '100%', alignSelf: 'stretch', flex: 'none', height: sz('progressBar'), borderRadius: rad('full'), background: C.elevated, overflow: 'hidden', ...css } },
    h('div', { style: { width: `${pct}%`, height: sz('progressBar'), borderRadius: rad('full'), background: color } }));
}

function EmptyData(text) {
  return Text({ text, style: 'bodySmall', color: C.textMuted, css: { paddingTop: sp('md') } });
}

function TargetManagerDialog({ sections, onToggle, onDismiss }) {
  return AlertDialog({
    key: 'targetManager',
    onDismissRequest: onDismiss,
    title: Text({ text: R.string.insights_manage_target, color: C.text }),
    text: Column({ class: 'fill-w' },
      Text({ text: R.string.insights_target_manager_body, style: 'bodyMedium', color: C.textSecondary }),
      ...sections.map((section) => clickable(Row({ class: 'fill-w', css: { paddingTop: sp('md') }, verticalAlignment: 'CenterVertically' },
        Checkbox({ checked: !section.excluded, onCheckedChange: () => onToggle(section.key) }),
        Column({ css: { paddingLeft: sp('sm') } },
          Text({ text: section.name, style: 'bodyMedium', color: C.text }),
          Text({ text: section.excluded ? R.string.insights_not_preparing : R.string.insights_preparing, style: 'labelSmall', color: C.textMuted }),
        ),
      ), { onClick: () => onToggle(section.key) })),
    ),
    confirmButton: Button({ onClick: onDismiss, children: Text(R.string.insights_done) }),
    containerColor: C.surface,
  });
}

function periodTitle(period) {
  return {
    DAY: R.string.insights_section_day, WEEK: R.string.insights_section_week, MONTH: R.string.insights_section_month,
    THREE_MONTHS: R.string.insights_section_three_months, ALL: R.string.insights_section_all,
  }[period.name];
}

function periodSubtitle(period) {
  return {
    DAY: R.string.insights_period_day, WEEK: R.string.insights_period_week, MONTH: R.string.insights_period_month,
    THREE_MONTHS: R.string.insights_period_three_months, ALL: R.string.insights_period_all,
  }[period.name];
}

/** internal fun insightDuration(minutes: Int?) */
export function insightDuration(minutes) {
  if (minutes == null) return R.string.insights_no_value;
  if (minutes < 60) return stringResource(R.string.insights_minutes_value, minutes);
  if (minutes % 60 === 0) return stringResource(R.string.insights_hours_value, Math.trunc(minutes / 60));
  return stringResource(R.string.insights_hours_minutes_value, Math.trunc(minutes / 60), minutes % 60);
}

function periodLabel(period) {
  return {
    DAY: R.string.insights_period_label_day, WEEK: R.string.insights_period_label_week, MONTH: R.string.insights_period_label_month,
    THREE_MONTHS: R.string.insights_period_label_three_months, ALL: R.string.insights_period_label_all,
  }[period.name];
}

function trendLabel(point, period) {
  return formatPattern(point.date, period === InsightPeriod.DAY || period === InsightPeriod.WEEK ? 'EEE' : 'd MMM');
}

const streakLabel = (days) => pluralStringResource(R.plurals.insights_days_count, days, days);

function subjectStatusLabel(status) {
  if (status === SubjectStatus.AHEAD) return R.string.insights_ahead_label;
  if (status === SubjectStatus.ON_TRACK) return R.string.insights_on_track_label;
  return R.string.insights_behind_label;
}

function subjectStatusColor(status) {
  if (status === SubjectStatus.AHEAD) return C.success;
  if (status === SubjectStatus.ON_TRACK) return C.brandSoft;
  return C.warning;
}
