package com.exam.assistant.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.SyllabusSectionProgress

@Composable
fun ProgressRoute(
    planStore: PlanStore,
    syllabusRepository: SyllabusRepository,
    syllabusStore: SyllabusStore,
    focusStore: FocusStore,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.Factory(planStore, syllabusRepository, syllabusStore, focusStore),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    ProgressScreen(state = state, modifier = modifier)
}

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    if (!state.hasPlan && !state.loading) {
        Box(
            modifier = modifier.fillMaxSize().padding(Spacing.screen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.progress_no_plan),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textMuted,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Text(
            text = stringResource(R.string.progress_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.progress_days_to_go, state.daysUntilExam),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )

        CushionHeroCard(state = state)

        SectionLabel(text = stringResource(R.string.progress_today))
        ProgressCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.progress_today_hours,
                        state.todayDoneHours,
                        state.todayBudgetHours,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                Text(
                    text = "${state.todayPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            ProgressBar(percent = state.todayPercent, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                text = if (state.focusSessionsToday == 1) {
                    stringResource(R.string.progress_focus_sessions_one)
                } else {
                    stringResource(R.string.progress_focus_sessions, state.focusSessionsToday)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        SectionLabel(
            text = stringResource(
                R.string.progress_syllabus_header,
                state.syllabusHoursDone,
                state.syllabusHoursTotal,
            ),
        )
        ProgressCard {
            state.sections.forEach { section ->
                SectionRow(section = section)
            }
        }

        Text(
            text = stringResource(R.string.progress_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = Spacing.xl),
        )
    }
}

@Composable
private fun CushionHeroCard(state: ProgressUiState) {
    val colors = AppTheme.colors
    val heroColor = if (state.isShort) colors.danger else colors.success
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.gapHours.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = heroColor,
                )
                Text(
                    text = stringResource(
                        if (state.isShort) R.string.progress_hours_short else R.string.progress_hours_spare,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = heroColor,
                    modifier = Modifier.padding(start = Spacing.sm, bottom = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.elevated),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            if (state.isShort) state.coveragePercent / 100f else 1f,
                        )
                        .height(8.dp)
                        .background(colors.success),
                )
                if (state.isShort) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(colors.dangerContainer),
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.progress_hours_available,
                    state.haveHours,
                    state.needHours,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun SectionRow(section: SyllabusSectionProgress) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = sectionShortName(section.name),
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            Text(
                text = "${section.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        ProgressBar(percent = section.percent, modifier = Modifier.padding(top = Spacing.xs))
        Text(
            text = stringResource(
                R.string.progress_section_meta,
                section.topicsDone,
                section.topicsTotal,
                section.hoursDone,
                section.hoursTotal,
                section.questions,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.elevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(percent / 100f)
                .height(4.dp)
                .background(colors.success),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.sm),
    )
}

@Composable
private fun ProgressCard(content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            content()
        }
    }
}

private fun sectionShortName(name: String): String = when {
    name.contains("Quant", ignoreCase = true) -> "Quant"
    name.contains("Reasoning", ignoreCase = true) -> "Reasoning"
    name.contains("Awareness", ignoreCase = true) -> "GA"
    name.contains("English", ignoreCase = true) -> "English"
    else -> name
}
