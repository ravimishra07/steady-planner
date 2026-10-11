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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.exam.assistant.core.design.Size
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
    onLogStudy: (LocalDate) -> Unit,
    onEditSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayLogViewModel = viewModel(
        key = "day-log-$date",
        factory = DayLogViewModel.Factory(date, examPackRepository, attemptRepository, studySessionRepository, planRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from logging or editing a session: re-read the day.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    DayLogScreen(
        state = state,
        onBack = onBack,
        onLogStudy = { onLogStudy(date) },
        onEditSession = onEditSession,
        onDeleteSession = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
fun DayLogScreen(
    state: DayLogUiState,
    onBack: () -> Unit,
    onLogStudy: () -> Unit,
    onEditSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var pendingDelete by remember { mutableStateOf<String?>(null) }
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
            log == null || log.entries.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.day_log_empty), style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                LogStudyButton(onClick = onLogStudy)
            }
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
                DaySessionList(entries = log.entries, onEdit = onEditSession, onDelete = { pendingDelete = it })
                LogStudyButton(onClick = onLogStudy, modifier = Modifier.padding(top = Spacing.lg))
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
    pendingDelete?.let { id ->
        DeleteSessionDialog(
            onConfirm = { pendingDelete = null; onDeleteSession(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** "Log study I did offline" (§6.3). Shown on Insights and in the day view. */
@Composable
internal fun LogStudyButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = Size.touchTarget)) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Size.smallIcon))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(R.string.log_session_open))
    }
}

/** The day's sessions in one card. Tapping a row edits it; the trailing action deletes it after a confirm. */
@Composable
internal fun DaySessionList(
    entries: List<DaySessionEntry>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier) {
        entries.forEachIndexed { index, entry ->
            DaySessionRow(entry, onEdit, onDelete)
            if (index < entries.lastIndex) HorizontalDivider(color = colors.borderSubtle)
        }
    }
}

@Composable
private fun DaySessionRow(entry: DaySessionEntry, onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
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
        onClick = { onEdit(entry.sessionId) },
        trailing = {
            IconButton(onClick = { onDelete(entry.sessionId) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.log_session_delete),
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }
        },
    )
}

@Composable
private fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}

internal fun activityLabel(type: StudyActivityType): Int = when (type) {
    StudyActivityType.LEARN -> R.string.day_log_type_learn
    StudyActivityType.REVISION -> R.string.day_log_type_revision
    StudyActivityType.PRACTICE -> R.string.day_log_type_practice
    StudyActivityType.MOCK_TEST -> R.string.day_log_type_mock
    StudyActivityType.CLASS -> R.string.day_log_type_class
    StudyActivityType.CUSTOM -> R.string.day_log_type_custom
    StudyActivityType.BREAK -> R.string.day_log_type_break
}
