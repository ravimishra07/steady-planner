package com.exam.assistant.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.SessionListItem
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.DaySessionEntry
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.formatInsightDate
import com.exam.assistant.domain.formatMinuteOfDay
import java.time.LocalDate

@Composable
fun DayLogRoute(
    date: LocalDate,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    studySessionRepository: StudySessionRepository,
    planRepository: PlanRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayLogViewModel = viewModel(
        key = "day-log-$date",
        factory = DayLogViewModel.Factory(date, examPackRepository, attemptRepository, studySessionRepository, planRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DayLogScreen(state = state, onBack = onBack, modifier = modifier)
}

@Composable
fun DayLogScreen(
    state: DayLogUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.day_log_back),
                    tint = colors.textSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.day_log_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(formatInsightDate(state.date), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        val log = state.log
        when {
            state.loading -> CenteredNote(stringResource(R.string.insights_loading))
            state.loadFailed -> CenteredNote(stringResource(R.string.insights_load_failed))
            log == null || log.entries.isEmpty() -> CenteredNote(stringResource(R.string.day_log_empty))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.day_log_summary,
                        log.entries.size,
                        insightDuration(log.totalFocusedMinutes),
                        log.entries.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
                )
                DaySessionList(entries = log.entries)
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

/** The day's sessions in one card. Each row is a [SessionListItem], ready for edit and delete. */
@Composable
internal fun DaySessionList(entries: List<DaySessionEntry>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier) {
        entries.forEachIndexed { index, entry ->
            DaySessionRow(entry)
            if (index < entries.lastIndex) HorizontalDivider(color = colors.borderSubtle)
        }
    }
}

@Composable
private fun DaySessionRow(entry: DaySessionEntry) {
    val type = stringResource(activityLabel(entry.activityType))
    val detail = listOfNotNull(type, entry.topicTitle, entry.subjectName).joinToString(stringResource(R.string.day_log_separator))
    val time = entry.endMinuteOfDay?.let {
        stringResource(R.string.day_log_time_range, formatMinuteOfDay(entry.startMinuteOfDay), formatMinuteOfDay(it))
    } ?: formatMinuteOfDay(entry.startMinuteOfDay)
    SessionListItem(
        time = time,
        title = entry.title ?: type,
        detail = detail,
        duration = insightDuration(entry.focusedMinutes),
    )
}

@Composable
private fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}

private fun activityLabel(type: StudyActivityType): Int = when (type) {
    StudyActivityType.LEARN -> R.string.day_log_type_learn
    StudyActivityType.REVISION -> R.string.day_log_type_revision
    StudyActivityType.PRACTICE -> R.string.day_log_type_practice
    StudyActivityType.MOCK_TEST -> R.string.day_log_type_mock
    StudyActivityType.CLASS -> R.string.day_log_type_class
    StudyActivityType.CUSTOM -> R.string.day_log_type_custom
    StudyActivityType.BREAK -> R.string.day_log_type_break
}
