package com.exam.assistant.feature.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.ChapterStageRepository
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Motion
import com.exam.assistant.core.design.ProLockIcon
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.rememberHaptics
import com.exam.assistant.core.design.subjectIcon
import com.exam.assistant.domain.InsightsData
import com.exam.assistant.domain.RevisionInsight
import com.exam.assistant.domain.StudyDayInsight
import com.exam.assistant.domain.SubjectInsight
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Progress tab: how much is done, whether it finishes on time, which subjects lag, and the
 * week's rhythm. Every number comes from [computeInsights][com.exam.assistant.domain.computeInsights]
 * via [ProgressViewModel]; the full set of sections lives in [InsightsRoute] ("All insights").
 */
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
    revisionRepository: RevisionRepository,
    availabilityRepository: AvailabilityRepository,
    chapterStageRepository: ChapterStageRepository,
    mockTestRepository: MockTestRepository,
    proRepository: ProRepository,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    onOpenSyllabus: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onGoToday: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onLogStudy: () -> Unit = {},
    onOpenTests: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.Factory(
            examPackRepository, attemptRepository, planRepository, studySessionRepository,
            topicProgressRepository, targetSyllabusRepository,
            rollingPlanRepository, studyPreferenceRepository,
            revisionRepository, availabilityRepository, chapterStageRepository, mockTestRepository,
            proRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from logging, Focus or Syllabus: show what changed.
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.showSoftPrompt) {
        if (state.showSoftPrompt) {
            viewModel.onSoftPromptShown()
            onOpenPaywall()
        }
    }
    ProgressScreen(
        state = state,
        modifier = modifier,
        onOpenPaywall = onOpenPaywall,
        onOpenSyllabus = onOpenSyllabus,
        onOpenInsights = onOpenInsights,
        onGoToday = onGoToday,
        onOpenDay = onOpenDay,
        onLogStudy = onLogStudy,
        onOpenTests = onOpenTests,
    )
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    onOpenSyllabus: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onGoToday: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    onLogStudy: () -> Unit = {},
    onOpenTests: () -> Unit = {},
) {
    val colors = AppTheme.colors
    when {
        !state.hasPlan && !state.loading -> {
            CenteredMessage(stringResource(R.string.insights_no_plan), modifier)
            return
        }
        !state.loading && state.loadFailed -> {
            CenteredMessage(stringResource(R.string.insights_load_failed), modifier)
            return
        }
        state.loading || state.data == null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
            return
        }
    }
    val data = state.data ?: return
    var showQueue by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Header(examLabel = state.examLabel, onLogStudy = onLogStudy)
        if (!data.hasStudyHistory) FirstSessionBanner(onGoToday)
        SyllabusCompletedCard(data, onOpenSyllabus)
        // Steady mode has no finish-by date: no forecast, no "behind" (InsightPlan.planMode).
        if (data.forecast.hasTarget) {
            CompletionOutlookCard(
                data = data,
                unlocked = state.proUnlocked,
                onClick = if (state.proUnlocked) onOpenInsights else onOpenPaywall,
            )
        }
        if (data.subjects.isNotEmpty()) SubjectProgressCard(data.subjects, onOpenSyllabus)
        RevisionSummaryCard(data.revision, onOpenQueue = { showQueue = true })
        ConsistencySummaryCard(data, onOpenDay = onOpenDay, onOpenInsights = onOpenInsights)
        MoreLinks(onOpenTests = onOpenTests, onOpenInsights = onOpenInsights)
        Spacer(Modifier.height(Spacing.xl))
    }

    if (showQueue) {
        RevisionQueueSheet(
            titles = data.revision.dueTitles,
            onGoToday = {
                showQueue = false
                onGoToday()
            },
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun Header(examLabel: String?, onLogStudy: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (examLabel != null) {
                Text(examLabel, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
            Text(
                stringResource(R.string.progress_title),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        FilledTonalIconButton(onClick = onLogStudy, modifier = Modifier.size(Size.touchTarget)) {
            Icon(Icons.Outlined.EditCalendar, contentDescription = stringResource(R.string.progress_log_study))
        }
    }
}

@Composable
private fun FirstSessionBanner(onGoToday: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.large),
        color = colors.selectionContainer,
        contentColor = colors.onSelectionContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShowChart, contentDescription = null, modifier = Modifier.size(Size.standardIcon))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.progress_empty_banner_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.progress_empty_banner_body), style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onGoToday, modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget)) {
                Text(stringResource(R.string.progress_start_next))
            }
        }
    }
}

/** Card title with an optional trailing action label and chevron; the card itself carries the click. */
@Composable
private fun CardTitle(title: String, action: String? = null) {
    val colors = AppTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Size.standardIcon),
        )
    }
}

@Composable
private fun SummaryCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val haptics = rememberHaptics()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            haptics.select()
            onClick()
        },
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), content = content)
    }
}

@Composable
private fun SyllabusCompletedCard(data: InsightsData, onOpenSyllabus: () -> Unit) {
    val colors = AppTheme.colors
    SummaryCard(onClick = onOpenSyllabus) {
        CardTitle(stringResource(R.string.progress_syllabus_completed))
        Row(modifier = Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.insights_percent_value, data.syllabusPercent),
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
                fontWeight = FontWeight.Bold,
            )
            if (data.syllabusPercentGainedThisMonth > 0) {
                Text(
                    stringResource(R.string.progress_gained_month, data.syllabusPercentGainedThisMonth),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.success,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.sm),
                )
            }
        }
        AnimatedBar(percent = data.syllabusPercent, color = colors.brand, modifier = Modifier.padding(top = Spacing.md))
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.progress_sections_of, data.syllabusCompletedSections, data.syllabusTotalSections),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                stringResource(R.string.progress_sections_remaining, data.syllabusRemainingSections),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** Deadline mode only. The estimate and the extra daily time are Pro, as in the full forecast. */
@Composable
private fun CompletionOutlookCard(data: InsightsData, unlocked: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val forecast = data.forecast
    val estimate = forecast.forecastDate?.takeIf { unlocked && forecast.hasReliablePace }
    SummaryCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(Icons.Outlined.EventAvailable, tint = colors.brand)
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.progress_outlook))
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = Spacing.md)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.progress_estimated_finish), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        if (!unlocked) {
                            ProLockIcon(Modifier.padding(top = Spacing.sm))
                        } else {
                            Text(
                                estimate?.let { outlookDate(it) } ?: stringResource(R.string.insights_no_value),
                                style = MaterialTheme.typography.titleLarge,
                                color = if ((forecast.daysDelta ?: 0) > 0) colors.warning else colors.text,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = Spacing.xxs),
                            )
                        }
                    }
                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = Spacing.md), color = colors.borderSubtle)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.progress_target_date), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Text(
                            outlookDate(forecast.targetDate),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
                val extra = forecast.extraMinutesPerDay ?: 0
                Text(
                    text = when {
                        !unlocked -> stringResource(R.string.progress_outlook_locked)
                        data.syllabusRemainingSections == 0 -> stringResource(R.string.progress_outlook_done)
                        !forecast.hasReliablePace -> stringResource(R.string.progress_outlook_no_pace)
                        extra > 0 -> stringResource(R.string.progress_outlook_extra, insightDuration(extra))
                        else -> stringResource(R.string.progress_outlook_on_pace)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun SubjectProgressCard(subjects: List<SubjectInsight>, onViewAll: () -> Unit) {
    SummaryCard(onClick = onViewAll) {
        CardTitle(stringResource(R.string.progress_subjects), action = stringResource(R.string.progress_view_all))
        Column(modifier = Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            subjects.forEach { subject -> SubjectProgressRow(subject) }
        }
    }
}

@Composable
private fun SubjectProgressRow(subject: SubjectInsight) {
    val colors = AppTheme.colors
    val statusColor = subject.status?.let { subjectStatusColor(it) }
    val statusLabel = subject.status?.let { subjectStatusLabel(it) }
    val fraction = stringResource(R.string.progress_fraction, subject.completedSections, subject.totalSections)
    val percent = stringResource(R.string.insights_percent_value, subject.percent)
    val description = listOfNotNull(subject.name, percent, fraction, statusLabel).joinToString(", ")
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconTile(subjectIcon(subject.subjectId), tint = colors.brand)
        Text(
            subject.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f),
        )
        AnimatedBar(percent = subject.percent, color = statusColor ?: colors.brand, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(percent, style = MaterialTheme.typography.titleSmall, color = statusColor ?: colors.text, fontWeight = FontWeight.Bold)
            Text(fraction, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun RevisionSummaryCard(revision: RevisionInsight, onOpenQueue: () -> Unit) {
    val colors = AppTheme.colors
    SummaryCard(onClick = onOpenQueue) {
        CardTitle(stringResource(R.string.progress_revision), action = stringResource(R.string.progress_view_queue))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = Spacing.md)) {
            RevisionCount(
                icon = Icons.Outlined.Schedule,
                tint = colors.warning,
                value = revision.dueNow,
                label = stringResource(R.string.progress_due_now),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = Spacing.sm), color = colors.borderSubtle)
            RevisionCount(
                icon = Icons.Outlined.ErrorOutline,
                tint = if (revision.overdue > 0) colors.danger else colors.textSecondary,
                value = revision.overdue,
                label = stringResource(R.string.progress_overdue),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().padding(horizontal = Spacing.sm), color = colors.borderSubtle)
            RevisionCount(
                icon = Icons.Outlined.CheckCircle,
                tint = colors.success,
                value = revision.completedThisMonth,
                label = stringResource(R.string.progress_done_month),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RevisionCount(icon: ImageVector, tint: Color, value: Int, label: String, modifier: Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.standardIcon))
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 2)
        }
    }
}

/** The last 7 days of study time against the plan's time for the same days. Bars open that day's log. */
@Composable
private fun ConsistencySummaryCard(data: InsightsData, onOpenDay: (LocalDate) -> Unit, onOpenInsights: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.select()
                        onOpenInsights()
                    }
                    .heightIn(min = Size.touchTarget)
                    .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm),
            ) {
                CardTitle(stringResource(R.string.progress_consistency), action = stringResource(R.string.progress_last_7_days))
            }
            val days = data.lastSevenDays
            if (days.none { it.minutes > 0 }) {
                Text(
                    stringResource(R.string.progress_no_study_week),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            } else {
                WeekBars(days = days, onOpenDay = onOpenDay, modifier = Modifier.padding(horizontal = Spacing.lg))
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), color = colors.borderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.brand, modifier = Modifier.size(Size.smallIcon))
                Text(
                    stringResource(R.string.progress_studied, shortHours(data.lastSevenDaysMinutes)),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (data.weeklyGoalMinutes > 0) {
                    Text(
                        stringResource(R.string.progress_goal, shortHours(data.weeklyGoalMinutes)),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekBars(days: List<StudyDayInsight>, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val maxMinutes = days.maxOf { it.minutes }.coerceAtLeast(1)
    val today = days.lastOrNull()?.date
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            val hours = shortHours(day.minutes)
            val description = stringResource(
                R.string.progress_day_hours,
                day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                hours,
            )
            val fraction by animateFloatAsState(
                targetValue = day.minutes.toFloat() / maxMinutes,
                animationSpec = tween(Motion.medium, easing = Motion.emphasizedDecelerate),
                label = "bar",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.small))
                    .clickable(enabled = day.sessions > 0) {
                        haptics.select()
                        onOpenDay(day.date)
                    }
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(hours, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .fillMaxWidth(BAR_WIDTH_FRACTION)
                        .height((Size.progressChart * fraction).coerceAtLeast(Size.progressHeight))
                        .clip(RoundedCornerShape(topStart = Radius.small, topEnd = Radius.small))
                        .background(if (day.minutes > 0) colors.brand else colors.surfaceControl),
                )
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day.date == today) colors.text else colors.textSecondary,
                    fontWeight = if (day.date == today) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** Mock tests and every Insights section: the places this overview leaves out. */
@Composable
private fun MoreLinks(onOpenTests: () -> Unit, onOpenInsights: () -> Unit) {
    val colors = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth()) {
        LinkRow(Icons.Outlined.Quiz, stringResource(R.string.progress_mock_tests), null, onOpenTests)
        HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg), color = colors.borderSubtle)
        LinkRow(
            Icons.Outlined.Insights,
            stringResource(R.string.progress_all_insights),
            stringResource(R.string.progress_all_insights_body),
            onOpenInsights,
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, body: String?, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                onClick()
            }
            .heightIn(min = Size.listRow)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Size.standardIcon))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Size.standardIcon))
    }
}

/** The due-now revisions (overdue first), from [RevisionInsight.dueTitles]. Today is where they get studied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevisionQueueSheet(titles: List<String>, onGoToday: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                stringResource(R.string.progress_queue_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (titles.isEmpty()) R.string.progress_queue_empty else R.string.progress_queue_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
            )
            titles.forEachIndexed { index, title ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                )
                if (index < titles.lastIndex) HorizontalDivider(color = colors.borderSubtle)
            }
            if (titles.isNotEmpty()) {
                Button(onClick = onGoToday, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).heightIn(min = Size.touchTarget)) {
                    Text(stringResource(R.string.progress_queue_go_today))
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(Size.swatch)
            .clip(RoundedCornerShape(Radius.medium))
            .background(AppTheme.colors.surfaceControl),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.standardIcon))
    }
}

/** Fills from empty when first shown; later changes animate from the old value. */
@Composable
private fun AnimatedBar(percent: Int, color: Color, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(percent) { target = percent.coerceIn(0, 100) / 100f }
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(Motion.medium * 2, easing = Motion.emphasizedDecelerate),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Size.progressBar)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceControl),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** "3.5h": hours to one decimal, locale digits. */
@Composable
private fun shortHours(minutes: Int): String {
    val format = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    return stringResource(R.string.progress_hours_short, format.format(minutes / 60.0))
}

/** "24 April", with the year only when it is not this year. */
private fun outlookDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern(if (date.year == LocalDate.now().year) "d MMMM" else "d MMM yyyy", Locale.getDefault()),
)

private const val BAR_WIDTH_FRACTION = 0.6f
