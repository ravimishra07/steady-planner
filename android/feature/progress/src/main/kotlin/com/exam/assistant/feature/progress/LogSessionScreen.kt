package com.exam.assistant.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.LOGGABLE_ACTIVITY_TYPES
import com.exam.assistant.domain.MANUAL_SESSION_MAX_MINUTES
import com.exam.assistant.domain.MANUAL_SESSION_MIN_MINUTES
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.formatInsightDate
import com.exam.assistant.domain.formatMinuteOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LogSessionRoute(
    sessionId: String?,
    date: LocalDate?,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    studySessionRepository: StudySessionRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogSessionViewModel = viewModel(
        key = "log-session-${sessionId ?: "new"}-$date",
        factory = LogSessionViewModel.Factory(sessionId, date, examPackRepository, attemptRepository, studySessionRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    LogSessionScreen(
        state = state,
        onBack = onDone,
        onSubject = viewModel::selectSubject,
        onChapter = viewModel::selectChapter,
        onType = viewModel::selectType,
        onStepDuration = viewModel::stepDuration,
        onDate = viewModel::setDate,
        onStartMinute = viewModel::setStartMinute,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
fun LogSessionScreen(
    state: LogSessionUiState,
    onBack: () -> Unit,
    onSubject: (String) -> Unit,
    onChapter: (String?) -> Unit,
    onType: (StudyActivityType) -> Unit,
    onStepDuration: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onStartMinute: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.day_log_back), tint = colors.textSecondary)
            }
            Text(
                stringResource(if (state.isEdit) R.string.log_session_edit_title else R.string.log_session_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.isEdit && !state.unavailable) {
                IconButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.log_session_delete), tint = colors.danger)
                }
            }
        }
        when {
            state.loading -> FormNote(stringResource(R.string.insights_loading))
            state.unavailable -> FormNote(stringResource(R.string.log_session_unavailable))
            else -> LogSessionForm(state, onSubject, onChapter, onType, onStepDuration, onDate, onStartMinute, onSave)
        }
    }
    if (confirmDelete) {
        DeleteSessionDialog(
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LogSessionForm(
    state: LogSessionUiState,
    onSubject: (String) -> Unit,
    onChapter: (String?) -> Unit,
    onType: (StudyActivityType) -> Unit,
    onStepDuration: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onStartMinute: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val colors = AppTheme.colors
    var pickChapter by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FieldLabel(stringResource(R.string.log_session_subject))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.subjects.forEach { subject ->
                FilterChip(
                    selected = subject.id == state.subjectId,
                    onClick = { onSubject(subject.id) },
                    label = { Text(subject.name) },
                )
            }
        }
        FieldLabel(stringResource(R.string.log_session_chapter))
        OutlinedButton(
            onClick = { pickChapter = true },
            enabled = state.chapters.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
        ) {
            Text(
                state.chapterTitle ?: stringResource(R.string.log_session_chapter_none),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
        }
        FieldLabel(stringResource(R.string.log_session_type))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            LOGGABLE_ACTIVITY_TYPES.forEach { type ->
                FilterChip(
                    selected = type == state.activityType,
                    onClick = { onType(type) },
                    label = { Text(stringResource(activityLabel(type))) },
                )
            }
        }
        AppValueStepper(
            label = stringResource(R.string.log_session_duration),
            value = insightDuration(state.durationMinutes),
            decreaseContentDescription = stringResource(R.string.log_session_shorter),
            increaseContentDescription = stringResource(R.string.log_session_longer),
            onDecrease = { onStepDuration(false) },
            onIncrease = { onStepDuration(true) },
            decreaseEnabled = state.durationMinutes > MANUAL_SESSION_MIN_MINUTES,
            increaseEnabled = state.durationMinutes < MANUAL_SESSION_MAX_MINUTES,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(stringResource(R.string.log_session_date))
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget)) {
                    Text(formatInsightDate(state.date))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(stringResource(R.string.log_session_start))
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget)) {
                    Text(formatMinuteOfDay(state.startMinuteOfDay))
                }
            }
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.log_session_save_failed), style = MaterialTheme.typography.bodySmall, color = colors.danger)
        }
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().height(Size.ctaHeight).padding(top = Spacing.xs),
        ) {
            Text(stringResource(if (state.isEdit) R.string.log_session_save_changes else R.string.log_session_save))
        }
        Spacer(Modifier.height(Spacing.xl))
    }
    if (pickChapter) {
        ChapterPickerDialog(
            chapters = state.chapters,
            selectedId = state.chapterId,
            onPick = { onChapter(it); pickChapter = false },
            onDismiss = { pickChapter = false },
        )
    }
    if (pickDate) SessionDatePicker(state.date, onDate = { onDate(it); pickDate = false }, onDismiss = { pickDate = false })
    if (pickTime) SessionTimePicker(state.startMinuteOfDay, onTime = { onStartMinute(it); pickTime = false }, onDismiss = { pickTime = false })
}

@Composable
private fun ChapterPickerDialog(
    chapters: List<ChapterChoice>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_session_chapter)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = Size.dialogListMaxHeight)) {
                item(key = "__none__") {
                    PickerRow(stringResource(R.string.log_session_chapter_none), selectedId == null) { onPick(null) }
                }
                items(chapters, key = { it.id }) { chapter ->
                    PickerRow(chapter.title, chapter.id == selectedId) { onPick(chapter.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_session_cancel)) } },
        containerColor = colors.surface,
    )
}

@Composable
private fun PickerRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) colors.brandSoft else colors.text,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDatePicker(date: LocalDate, onDate: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val maxMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text(stringResource(R.string.log_session_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_session_cancel)) } },
    ) { DatePicker(state = pickerState, showModeToggle = false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTimePicker(minuteOfDay: Int, onTime: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val pickerState = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(Radius.lg), color = colors.surface) {
            Column(modifier = Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = pickerState)
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_session_cancel)) }
                    TextButton(onClick = { onTime(pickerState.hour * 60 + pickerState.minute) }) {
                        Text(stringResource(R.string.log_session_ok))
                    }
                }
            }
        }
    }
}

/** Confirms deleting one past session. Shared by the edit form and the day view's row action. */
@Composable
internal fun DeleteSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_session_delete_title)) },
        text = { Text(stringResource(R.string.log_session_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.log_session_delete), color = colors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_session_cancel)) } },
        containerColor = colors.surface,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

@Composable
private fun FormNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}
