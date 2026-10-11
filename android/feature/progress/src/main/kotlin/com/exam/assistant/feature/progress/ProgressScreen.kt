package com.exam.assistant.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.CalendarMetrics
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.InsightPeriod
import com.exam.assistant.domain.InsightsData
import com.exam.assistant.domain.SubjectInsight
import com.exam.assistant.domain.SubjectStatus
import com.exam.assistant.domain.StudyDayInsight
import com.exam.assistant.domain.formatInsightDate
import kotlin.math.ceil

@Composable
fun ProgressRoute(
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    planRepository: PlanRepository,
    studySessionRepository: StudySessionRepository,
    topicProgressRepository: TopicProgressRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    rollingPlanRepository: RollingPlanRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onGoToday: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.Factory(
            examPackRepository, attemptRepository, planRepository, studySessionRepository,
            topicProgressRepository, targetSyllabusRepository,
            rollingPlanRepository, studyPreferenceRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.refresh() }
    ProgressScreen(
        state = state,
        onPeriodSelected = viewModel::selectPeriod,
        onOpenSettings = onOpenSettings,
        onGoToday = onGoToday,
        onManageTarget = viewModel::openTargetManager,
        onCloseTargetManager = viewModel::closeTargetManager,
        onToggleSection = viewModel::toggleSectionTarget,
        modifier = modifier,
    )
}

@Composable
fun ProgressScreen(
    state: InsightsUiState,
    modifier: Modifier = Modifier,
    onPeriodSelected: (InsightPeriod) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onGoToday: () -> Unit = {},
    onManageTarget: () -> Unit = {},
    onCloseTargetManager: () -> Unit = {},
    onToggleSection: (String) -> Unit = {},
) {
    val colors = AppTheme.colors
    if (!state.hasPlan && !state.loading) {
        Box(modifier = modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.insights_no_plan), style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
        }
        return
    }

    if (!state.loading && state.loadFailed) {
        Box(modifier = modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.insights_load_failed), style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
        }
        return
    }

    if (state.loading || state.data == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.insights_loading), color = colors.textMuted)
        }
        return
    }

    val data = state.data
    if (!data.hasStudyHistory) {
        EmptyProgressScreen(data = data, onGoToday = onGoToday, modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineLarge, color = colors.text, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.insights_settings), tint = colors.textSecondary)
            }
        }
        PeriodSelector(selected = state.period, onSelect = onPeriodSelected)

        InsightsSection(title = stringResource(R.string.insights_this_week), subtitle = periodSubtitle(state.period)) {
            QuickStats(data)
        }
        InsightsSection(
            title = stringResource(R.string.insights_consistency),
            subtitle = stringResource(R.string.insights_consistency_subtitle),
        ) {
            ConsistencyCard(data)
        }
        InsightsSection(title = stringResource(R.string.insights_study_time), subtitle = stringResource(R.string.insights_study_time_subtitle)) {
            StudyTimeCard(data)
        }
        InsightsSection(title = stringResource(R.string.insights_plan_actual), subtitle = stringResource(R.string.insights_plan_actual_subtitle)) {
            PlanActualCard(data)
        }
        InsightsSection(title = stringResource(R.string.insights_syllabus), subtitle = stringResource(R.string.insights_syllabus_subtitle)) {
            SyllabusCard(data, onManageTarget)
        }
        InsightsSection(title = stringResource(R.string.insights_forecast), subtitle = stringResource(R.string.insights_forecast_subtitle)) {
            ForecastCard(data)
        }
        InsightsSection(title = stringResource(R.string.insights_subjects), subtitle = stringResource(R.string.insights_subjects_subtitle)) {
            SubjectsCard(data.subjects)
        }
        InsightsSection(title = stringResource(R.string.insights_remaining), subtitle = stringResource(R.string.insights_remaining_subtitle)) {
            RemainingCard(data.subjects, data.syllabusRemainingTopics)
        }
        InsightsSection(title = stringResource(R.string.insights_revision), subtitle = stringResource(R.string.insights_revision_subtitle)) {
            RevisionCard(data)
        }
        InsightsSection(title = stringResource(R.string.insights_pattern), subtitle = stringResource(R.string.insights_pattern_subtitle)) {
            PatternCard(data)
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    if (state.showTargetManager) {
        TargetManagerDialog(
            sections = state.targetSections,
            onToggle = onToggleSection,
            onDismiss = onCloseTargetManager,
        )
    }
}

@Composable
private fun EmptyProgressScreen(data: InsightsData, onGoToday: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xl),
            shape = RoundedCornerShape(Radius.large),
            color = colors.selectionContainer,
            contentColor = colors.onSelectionContainer,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ShowChart, contentDescription = null, modifier = Modifier.size(Size.standardIcon))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.progress_empty_banner_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.progress_empty_banner_body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressFact(
                stringResource(R.string.progress_chapters_done),
                stringResource(R.string.progress_fraction, data.syllabusCompletedTopics, data.syllabusTotalTopics),
            )
            ProgressFact(stringResource(R.string.progress_days_studied), data.studyDaysInPeriod.toString())
            Button(onClick = onGoToday, modifier = Modifier.fillMaxWidth().height(Size.ctaHeight)) {
                Text(stringResource(R.string.progress_start_next))
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun ProgressFact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PeriodSelector(selected: InsightPeriod, onSelect: (InsightPeriod) -> Unit) {
    val colors = AppTheme.colors
    Surface(shape = RoundedCornerShape(Radius.md), color = colors.surfaceControl) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.xs)) {
            InsightPeriod.entries.forEach { period ->
                TextButton(
                    onClick = { onSelect(period) },
                    modifier = Modifier.weight(1f).height(Size.dayControl),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (period == selected) colors.selectionContainer else Color.Transparent,
                        contentColor = if (period == selected) colors.onSelectionContainer else colors.textMuted,
                    ),
                ) { Text(periodLabel(period), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun InsightsSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = Spacing.xxl)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md))
        content()
    }
}

@Composable
private fun QuickStats(data: InsightsData) {
    val colors = AppTheme.colors
    AppCard {
        Column {
            StatLine(
                leftLabel = stringResource(R.string.insights_total_studied), leftValue = insightDuration(data.totalStudiedMinutes.takeIf { data.hasStudyInPeriod }),
                rightLabel = stringResource(R.string.insights_sessions), rightValue = data.completedSessionCount.takeIf { data.hasStudyInPeriod }?.toString() ?: "—",
            )
            HorizontalDivider(color = colors.borderSubtle)
            StatLine(
                leftLabel = stringResource(R.string.insights_avg_day), leftValue = insightDuration(data.averagePerActiveDayMinutes),
                rightLabel = stringResource(R.string.insights_best_day), rightValue = insightDuration(data.bestDay?.minutes), rightNote = data.bestDay?.let { formatInsightDate(it.date) },
            )
        }
    }
}

@Composable
private fun StatLine(leftLabel: String, leftValue: String, rightLabel: String, rightValue: String, rightNote: String? = null) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        StatItem(leftLabel, leftValue, Modifier.weight(1f))
        StatItem(rightLabel, rightValue, Modifier.weight(1f), rightNote)
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier, note: String? = null) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.text, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.xs))
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary, modifier = Modifier.padding(top = Spacing.xxs))
    }
}

@Composable
private fun ConsistencyCard(data: InsightsData) {
    val colors = AppTheme.colors
    val weeks = remember(data.heatmapDays) { data.heatmapDays.chunked(7) }
    var selectedIndex by remember(data.period) { mutableIntStateOf(data.heatmapDays.indexOfLast { it.minutes > 0 }.coerceAtLeast(0)) }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                monthLabels(weeks).forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted.copy(alpha = .55f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                weeks.forEachIndexed { week, days ->
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        days.forEachIndexed { day, insight ->
                            val index = week * 7 + day
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(CalendarMetrics.meterHeight))
                                    .background(heatColor(insight.minutes))
                                    .clickable { selectedIndex = index },
                            )
                        }
                    }
                }
            }
            if (data.hasStudyHistory) {
                val selected = data.heatmapDays.getOrNull(selectedIndex)
                if (selected != null) {
                    Surface(shape = RoundedCornerShape(Radius.sm), color = colors.elevated, modifier = Modifier.padding(top = Spacing.md)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(formatInsightDate(selected.date), style = MaterialTheme.typography.labelMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (selected.minutes > 0) insightDuration(selected.minutes) else stringResource(R.string.insights_no_study_this_day),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            if (selected.minutes > 0) {
                                Text(pluralStringResource(R.plurals.insights_sessions_count, selected.sessions, selected.sessions), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                }
            } else {
                EmptyData(text = stringResource(R.string.insights_empty_history))
            }
            Row(modifier = Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Legend(colors.surfaceControl, stringResource(R.string.insights_less))
                Legend(colors.brandDeep, stringResource(R.string.insights_more))
            }
            HorizontalDivider(modifier = Modifier.padding(top = Spacing.md), color = colors.borderSubtle)
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                SummaryItem(stringResource(R.string.insights_current_streak), if (data.hasStudyHistory) streakLabel(data.currentStreak) else stringResource(R.string.insights_no_value), Modifier.weight(1f))
                SummaryItem(stringResource(R.string.insights_active_this_period), if (data.hasStudyInPeriod) pluralStringResource(R.plurals.insights_active_days_fraction, data.periodDays.size, data.studyDaysInPeriod, data.periodDays.size) else stringResource(R.string.insights_no_value), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(Size.legendDot).clip(RoundedCornerShape(CalendarMetrics.meterHeight)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.xxs))
    }
}

private fun monthLabels(weeks: List<List<com.exam.assistant.domain.StudyDayInsight>>): List<String> {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM", java.util.Locale.getDefault())
    var lastMonth = -1
    return weeks.mapNotNull { week ->
        val date = week.firstOrNull()?.date ?: return@mapNotNull null
        if (date.monthValue != lastMonth) {
            lastMonth = date.monthValue
            date.format(formatter)
        } else {
            null
        }
    }
}

@Composable
private fun heatColor(minutes: Int): Color {
    val colors = AppTheme.colors
    return when {
        minutes <= 0 -> colors.surfaceControl
        minutes < 30 -> colors.brandContainer
        minutes < 90 -> colors.brandDeep.copy(alpha = .72f)
        minutes < 180 -> colors.brand
        else -> colors.brandSoft
    }
}

@Composable
private fun StudyTimeCard(data: InsightsData) {
    val colors = AppTheme.colors
    val points = data.trendDays
    AppCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            if (!data.hasStudyInPeriod || points.none { it.minutes > 0 }) {
                EmptyData(text = stringResource(R.string.insights_empty_history))
            } else {
                val maxMinutes = points.maxOf { it.minutes }.coerceAtLeast(1)
                Row(modifier = Modifier.fillMaxWidth().height(Size.progressChartTall), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                    points.forEachIndexed { index, point ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(insightDuration(point.minutes), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                            Spacer(Modifier.height(Spacing.xs))
                            Box(modifier = Modifier.fillMaxWidth(.72f).height((82f * point.minutes / maxMinutes).coerceAtLeast(4f).dp).clip(RoundedCornerShape(topStart = Radius.sm, topEnd = Radius.sm)).background(if (index == points.lastIndex) colors.brand else colors.brandDeep))
                            Text(trendLabel(point, data.period), style = MaterialTheme.typography.labelSmall, color = if (index == points.lastIndex) colors.brandSoft else colors.textMuted, modifier = Modifier.padding(top = Spacing.xs))
                        }
                    }
                }
            }
            Text(
                text = if (data.hasStudyInPeriod) stringResource(R.string.insights_total_period, insightDuration(data.totalStudiedMinutes)) else stringResource(R.string.insights_start_studying),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

@Composable
private fun PlanActualCard(data: InsightsData) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            if (data.plannedMinutes == 0) {
                EmptyData(text = stringResource(R.string.insights_no_planned_blocks))
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    MetricPair(stringResource(R.string.insights_planned), insightDuration(data.plannedMinutes))
                    MetricPair(stringResource(R.string.insights_completed_planned), insightDuration(data.completedPlannedMinutes), colors.brandSoft, Alignment.End)
                }
                ProgressBar(percent = data.planPercent ?: 0, color = colors.brand, modifier = Modifier.padding(top = Spacing.md))
                Text(
                    text = data.planPercent?.let { stringResource(R.string.insights_plan_percent, it) } ?: stringResource(R.string.insights_no_actual_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                if (data.extraStudyMinutes > 0) {
                    Text(
                        text = stringResource(R.string.insights_extra_study, insightDuration(data.extraStudyMinutes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Text(
                    text = if (data.planDeltaMinutes >= 0) stringResource(R.string.insights_ahead, insightDuration(data.planDeltaMinutes)) else stringResource(R.string.insights_behind, insightDuration(-data.planDeltaMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (data.planDeltaMinutes >= 0) colors.success else colors.warning,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun MetricPair(label: String, value: String, valueColor: Color = AppTheme.colors.text, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun SyllabusCard(data: InsightsData, onManageTarget: () -> Unit) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(pluralStringResource(R.plurals.insights_topics_fraction, data.syllabusTotalTopics, data.syllabusCompletedTopics, data.syllabusTotalTopics), style = MaterialTheme.typography.headlineSmall, color = colors.text, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.insights_covered, data.syllabusPercent), style = MaterialTheme.typography.titleMedium, color = colors.brandSoft, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(pluralStringResource(R.plurals.insights_remaining_count, data.syllabusRemainingTopics, data.syllabusRemainingTopics), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                TextButton(onClick = onManageTarget, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.none)) { Text(stringResource(R.string.insights_manage_target), color = colors.brandSoft) }
            }
            ProgressBar(data.syllabusPercent, colors.brand, Modifier.padding(top = Spacing.xs))
            if (data.excludedTopicCount > 0) Text(pluralStringResource(R.plurals.insights_target_of_official, data.officialTopicCount, data.syllabusTotalTopics, data.officialTopicCount), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun ForecastCard(data: InsightsData) {
    val colors = AppTheme.colors
    val forecast = data.forecast
    Surface(shape = RoundedCornerShape(Radius.lg), color = colors.warningRow) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricPair(stringResource(R.string.insights_target_date), formatInsightDate(forecast.targetDate))
                val earliest = forecast.forecastEarliestDate
                val latest = forecast.forecastLatestDate
                val paceRange = if (earliest != null && latest != null) {
                    stringResource(
                        R.string.insights_forecast_range,
                        formatInsightDate(earliest),
                        formatInsightDate(latest),
                    )
                } else {
                    stringResource(R.string.insights_not_enough_history)
                }
                MetricPair(stringResource(R.string.insights_at_current_pace), paceRange, colors.warning, Alignment.End)
            }
            if (!forecast.hasReliablePace) {
                Text(stringResource(R.string.insights_forecast_insufficient), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.lg))
            } else {
                val delta = forecast.daysDelta ?: 0
                Text(
                    text = when {
                        delta > 0 -> pluralStringResource(R.plurals.insights_days_behind, delta, delta)
                        delta < 0 -> pluralStringResource(R.plurals.insights_days_ahead, -delta, -delta)
                        else -> stringResource(R.string.insights_on_time)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (delta > 0) colors.warning else colors.success,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                Surface(shape = RoundedCornerShape(Radius.md), color = colors.warningTint.copy(alpha = .5f), modifier = Modifier.padding(top = Spacing.md)) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(stringResource(R.string.insights_to_finish_by, formatInsightDate(forecast.targetDate)), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        Text(if ((forecast.extraMinutesPerDay ?: 0) > 0) stringResource(R.string.insights_extra_per_day, insightDuration(forecast.extraMinutesPerDay)) else stringResource(R.string.insights_current_pace_works), style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
                Row(modifier = Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    MetricPair(stringResource(R.string.insights_recent_average), insightDuration(forecast.recentAverageMinutesPerDay))
                    MetricPair(stringResource(R.string.insights_required_pace), insightDuration(forecast.requiredMinutesPerDay), colors.warning)
                }
                Text(
                    stringResource(
                        R.string.insights_forecast_evidence,
                        forecast.evidenceActiveDays,
                        forecast.evidenceWindowDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                Text(stringResource(R.string.insights_forecast_note), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = Spacing.md))
            }
        }
    }
}

@Composable
private fun SubjectsCard(subjects: List<SubjectInsight>) {
    val colors = AppTheme.colors
    AppCard {
        Column {
            subjects.forEachIndexed { index, subject ->
                SubjectRow(subject)
                if (index < subjects.lastIndex) HorizontalDivider(color = colors.borderSubtle)
            }
        }
    }
}

@Composable
private fun SubjectRow(subject: SubjectInsight) {
    val colors = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(subject.name, style = MaterialTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.insights_percent_value, subject.percent), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Text(subjectStatusLabel(subject.status), style = MaterialTheme.typography.bodySmall, color = subjectStatusColor(subject.status), fontWeight = FontWeight.SemiBold)
            }
        }
        ProgressBar(subject.percent, subjectStatusColor(subject.status), Modifier.padding(top = Spacing.sm))
        if (expanded) Text(pluralStringResource(R.plurals.insights_subject_detail, subject.totalTopics, subject.completedTopics, subject.totalTopics), style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun RemainingCard(subjects: List<SubjectInsight>, totalRemaining: Int) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            Text(pluralStringResource(R.plurals.insights_topics_left, totalRemaining, totalRemaining), style = MaterialTheme.typography.headlineSmall, color = colors.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm))
            subjects.forEach { subject ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(subject.name, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    Text(pluralStringResource(R.plurals.insights_left, subject.remainingTopics, subject.remainingTopics), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
                }
                if (subject != subjects.last()) HorizontalDivider(color = colors.borderSubtle)
            }
        }
    }
}

@Composable
private fun RevisionCard(data: InsightsData) {
    val colors = AppTheme.colors
    var showDue by remember { mutableStateOf(false) }
    AppCard {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RevisionMetric(stringResource(R.string.insights_due_now), data.revision.dueNow, Modifier.weight(1f))
                RevisionMetric(stringResource(R.string.insights_due_week), data.revision.dueThisWeek, Modifier.weight(1f))
                RevisionMetric(stringResource(R.string.insights_completed_week), data.revision.completedThisWeek, Modifier.weight(1f))
            }
            if (data.revision.onTimePercent == null) {
                EmptyData(text = stringResource(R.string.insights_no_revision_history))
            } else {
                val onTime = data.revision.onTimePercent ?: 0
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.insights_revision_on_time), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    Text(stringResource(R.string.insights_percent_value, onTime), style = MaterialTheme.typography.bodySmall, color = colors.brandSoft, fontWeight = FontWeight.SemiBold)
                }
                ProgressBar(onTime, colors.brand, Modifier.padding(top = Spacing.sm))
            }
            if (data.revision.dueTitles.isNotEmpty()) {
                TextButton(onClick = { showDue = !showDue }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.insights_revision_action)) }
                if (showDue) data.revision.dueTitles.forEach { title -> Text(stringResource(R.string.insights_bullet_item, title), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.xs)) }
            }
        }
    }
}

@Composable
private fun RevisionMetric(label: String, value: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(Radius.sm), color = AppTheme.colors.surfaceControl, modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(value.takeIf { it > 0 }?.toString() ?: stringResource(R.string.insights_no_value), style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

@Composable
private fun PatternCard(data: InsightsData) {
    val colors = AppTheme.colors
    AppCard {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
            PatternRow(stringResource(R.string.insights_average_session), insightDuration(data.averageSessionMinutes))
            PatternRow(stringResource(R.string.insights_longest_session), insightDuration(data.longestSessionMinutes))
            data.averageBreakMinutes?.let { PatternRow(stringResource(R.string.insights_average_break), insightDuration(it)) }
            PatternRow(stringResource(R.string.insights_study_days), if (data.hasStudyHistory) pluralStringResource(R.plurals.insights_days_count, data.studyDaysInPeriod, data.studyDaysInPeriod) else stringResource(R.string.insights_no_value))
            PatternRow(stringResource(R.string.insights_longest_streak), if (data.hasStudyHistory) streakLabel(data.longestStreak) else stringResource(R.string.insights_no_value), last = true)
        }
    }
}

@Composable
private fun PatternRow(label: String, value: String, last: Boolean = false) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth().then(if (last) Modifier else Modifier.padding(bottom = Spacing.none)).padding(vertical = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.text, fontWeight = FontWeight.SemiBold)
    }
    if (!last) HorizontalDivider(color = colors.borderSubtle)
}

@Composable
private fun ProgressBar(percent: Int, color: Color, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(modifier = modifier.fillMaxWidth().height(Size.progressBar).clip(RoundedCornerShape(Radius.full)).background(colors.elevated)) {
        Box(modifier = Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(Size.progressBar).clip(RoundedCornerShape(Radius.full)).background(color))
    }
}

@Composable
private fun EmptyData(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.md))
}

@Composable
private fun TargetManagerDialog(sections: List<TargetSectionUi>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.insights_manage_target), color = colors.text) },
        text = {
            Column {
                Text(stringResource(R.string.insights_target_manager_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                sections.forEach { section ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onToggle(section.key) }.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = !section.excluded, onCheckedChange = { onToggle(section.key) })
                        Column(modifier = Modifier.padding(start = Spacing.sm)) {
                            Text(section.name, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                            Text(if (section.excluded) stringResource(R.string.insights_not_preparing) else stringResource(R.string.insights_preparing), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.insights_done)) } },
        containerColor = colors.surface,
    )
}

@Composable
private fun periodSubtitle(period: InsightPeriod): String = stringResource(when (period) {
    InsightPeriod.DAY -> R.string.insights_period_day
    InsightPeriod.WEEK -> R.string.insights_period_week
    InsightPeriod.MONTH -> R.string.insights_period_month
    InsightPeriod.THREE_MONTHS -> R.string.insights_period_three_months
    InsightPeriod.ALL -> R.string.insights_period_all
})

@Composable
private fun insightDuration(minutes: Int?): String = when {
    minutes == null -> stringResource(R.string.insights_no_value)
    minutes < 60 -> stringResource(R.string.insights_minutes_value, minutes)
    minutes % 60 == 0 -> stringResource(R.string.insights_hours_value, minutes / 60)
    else -> stringResource(R.string.insights_hours_minutes_value, minutes / 60, minutes % 60)
}

@Composable
private fun periodLabel(period: InsightPeriod): String = stringResource(when (period) {
    InsightPeriod.DAY -> R.string.insights_period_label_day
    InsightPeriod.WEEK -> R.string.insights_period_label_week
    InsightPeriod.MONTH -> R.string.insights_period_label_month
    InsightPeriod.THREE_MONTHS -> R.string.insights_period_label_three_months
    InsightPeriod.ALL -> R.string.insights_period_label_all
})

private fun trendLabel(point: StudyDayInsight, period: InsightPeriod): String = point.date.format(
    java.time.format.DateTimeFormatter.ofPattern(
        if (period == InsightPeriod.DAY || period == InsightPeriod.WEEK) "EEE" else "d MMM",
        java.util.Locale.getDefault(),
    ),
)

@Composable
private fun streakLabel(days: Int): String = pluralStringResource(R.plurals.insights_days_count, days, days)

@Composable
private fun subjectStatusLabel(status: SubjectStatus): String = stringResource(
    when (status) {
        SubjectStatus.AHEAD -> R.string.insights_ahead_label
        SubjectStatus.ON_TRACK -> R.string.insights_on_track_label
        SubjectStatus.BEHIND -> R.string.insights_behind_label
    },
)

@Composable
private fun subjectStatusColor(status: SubjectStatus): Color = when (status) {
    SubjectStatus.AHEAD -> AppTheme.colors.success
    SubjectStatus.ON_TRACK -> AppTheme.colors.brandSoft
    SubjectStatus.BEHIND -> AppTheme.colors.warning
}

private fun Color.textFaint(): Color = copy(alpha = .55f)
