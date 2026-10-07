package com.exam.assistant.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import com.exam.assistant.domain.MIN_FINISH_BY_DAYS_AHEAD
import com.exam.assistant.domain.PlanMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing

@Composable
fun SettingsDetailRoute(
    planStore: PlanStore,
    settingsStore: SettingsStore,
    focusStore: FocusStore,
    syllabusStore: SyllabusStore,
    studySessionStore: StudySessionStore,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    rollingPlanRepository: RollingPlanRepository,
    configExamDate: LocalDate,
    examDateOfficial: Boolean,
    onBack: () -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsDetailViewModel = viewModel(
        factory = SettingsDetailViewModel.Factory(
            planStore = planStore,
            settingsStore = settingsStore,
            focusStore = focusStore,
            syllabusStore = syllabusStore,
            studySessionStore = studySessionStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            studyPreferenceRepository = studyPreferenceRepository,
            rollingPlanRepository = rollingPlanRepository,
            configExamDate = configExamDate,
            examDateOfficial = examDateOfficial,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestBack = {
        if (viewModel.requestBack()) onBack()
    }
    BackHandler(onBack = requestBack)
    SettingsDetailScreen(
        state = state,
        onBack = requestBack,
        onRetry = viewModel::refresh,
        onWeekdayChange = viewModel::setWeekdayHours,
        onWeekendChange = viewModel::setWeekendHours,
        onStudyPlaceChange = viewModel::setStudyPlace,
        onFocusDuration = viewModel::setFocusDurationMinutes,
        onFocusEndAlert = viewModel::setFocusEndAlert,
        onPlanMode = viewModel::setPlanMode,
        onExamDate = viewModel::setExamDate,
        onFinishBy = viewModel::setFinishBy,
        onSave = viewModel::save,
        onDismissSaveResult = viewModel::dismissSaveResult,
        onDismissDiscard = viewModel::dismissDiscard,
        onConfirmDiscard = {
            viewModel.confirmDiscard()
            onBack()
        },
        onRequestClear = viewModel::requestClear,
        onConfirmClear = { viewModel.confirmClear(onCleared) },
        onDismissClear = viewModel::dismissClear,
        onDismissClearError = viewModel::dismissClearError,
        modifier = modifier,
    )
}

@Composable
fun SettingsDetailScreen(
    state: SettingsDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onStudyPlaceChange: (String) -> Unit,
    onFocusDuration: (Int) -> Unit,
    onFocusEndAlert: (Boolean) -> Unit,
    onPlanMode: (PlanMode) -> Unit,
    onExamDate: (LocalDate) -> Unit,
    onFinishBy: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDismissSaveResult: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onRequestClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
    onDismissClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = onDismissClear,
            title = { Text(stringResource(R.string.settings_clear_plan)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirmClear, enabled = !state.clearing) {
                    Text(
                        if (state.clearing) stringResource(R.string.settings_clearing)
                        else stringResource(R.string.settings_clear_yes),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear, enabled = !state.clearing) {
                    Text(stringResource(R.string.settings_clear_no))
                }
            },
        )
    }
    if (state.clearFailed) {
        AlertDialog(
            onDismissRequest = onDismissClearError,
            title = { Text(stringResource(R.string.settings_clear_failed_title)) },
            text = { Text(stringResource(R.string.settings_clear_failed_body)) },
            confirmButton = {
                TextButton(onClick = onDismissClearError) {
                    Text(stringResource(R.string.settings_ok))
                }
            },
        )
    }
    if (state.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text(stringResource(R.string.settings_discard_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard) {
                    Text(stringResource(R.string.settings_discard_no))
                }
            },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_detail_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }
        when {
            state.loading -> {
                Text(
                    text = stringResource(R.string.settings_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
            state.loadFailed -> {
                Text(
                    text = stringResource(R.string.settings_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.md),
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.settings_retry)) }
            }
            else -> {
                SettingsEditor(
                    state = state,
                    onWeekdayChange = onWeekdayChange,
                    onWeekendChange = onWeekendChange,
                    onStudyPlaceChange = onStudyPlaceChange,
                    onFocusDuration = onFocusDuration,
                    onFocusEndAlert = onFocusEndAlert,
                    onPlanMode = onPlanMode,
                    onExamDate = onExamDate,
                    onFinishBy = onFinishBy,
                    onSave = onSave,
                    onDismissSaveResult = onDismissSaveResult,
                    onRequestClear = onRequestClear,
                )
            }
        }
    }
}

@Composable
private fun SettingsEditor(
    state: SettingsDetailUiState,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onStudyPlaceChange: (String) -> Unit,
    onFocusDuration: (Int) -> Unit,
    onFocusEndAlert: (Boolean) -> Unit,
    onPlanMode: (PlanMode) -> Unit,
    onExamDate: (LocalDate) -> Unit,
    onFinishBy: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDismissSaveResult: () -> Unit,
    onRequestClear: () -> Unit,
) {
    val colors = AppTheme.colors
    PlanSection(state, onPlanMode, onExamDate, onFinishBy)
    SectionTitle(stringResource(R.string.settings_hours))
    AppValueStepper(
        label = stringResource(R.string.settings_weekdays),
        value = stringResource(R.string.settings_hours_value, settingsHour(state.weekdayHours)),
        decreaseContentDescription = stringResource(
            R.string.settings_decrease_hours,
            stringResource(R.string.settings_weekdays),
        ),
        increaseContentDescription = stringResource(
            R.string.settings_increase_hours,
            stringResource(R.string.settings_weekdays),
        ),
        onDecrease = { onWeekdayChange((state.weekdayHours - .5f).coerceAtLeast(1f)) },
        onIncrease = { onWeekdayChange((state.weekdayHours + .5f).coerceAtMost(14f)) },
        decreaseEnabled = !state.saving && state.weekdayHours > 1f,
        increaseEnabled = !state.saving && state.weekdayHours < 14f,
    )
    AppValueStepper(
        label = stringResource(R.string.settings_weekends),
        value = stringResource(R.string.settings_hours_value, settingsHour(state.weekendHours)),
        decreaseContentDescription = stringResource(
            R.string.settings_decrease_hours,
            stringResource(R.string.settings_weekends),
        ),
        increaseContentDescription = stringResource(
            R.string.settings_increase_hours,
            stringResource(R.string.settings_weekends),
        ),
        onDecrease = { onWeekendChange((state.weekendHours - .5f).coerceAtLeast(1f)) },
        onIncrease = { onWeekendChange((state.weekendHours + .5f).coerceAtMost(16f)) },
        decreaseEnabled = !state.saving && state.weekendHours > 1f,
        increaseEnabled = !state.saving && state.weekendHours < 16f,
    )
    OutlinedTextField(
        value = state.studyPlace,
        onValueChange = onStudyPlaceChange,
        enabled = !state.saving,
        singleLine = true,
        label = { Text(stringResource(R.string.settings_study_spot)) },
        placeholder = { Text(stringResource(R.string.settings_study_spot_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTitle(stringResource(R.string.settings_focus))
    Text(stringResource(R.string.settings_focus_length), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(25, 50, 90).forEach { minutes ->
            FilterChip(
                selected = state.focusDurationMinutes == minutes,
                onClick = { onFocusDuration(minutes) },
                enabled = !state.saving,
                label = { Text(stringResource(R.string.settings_focus_minutes, minutes)) },
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.touchTarget)
            .toggleable(
                value = state.focusEndAlert,
                role = Role.Switch,
                onValueChange = onFocusEndAlert,
            ),
    ) {
        Text(
            text = stringResource(R.string.settings_focus_end_alert),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = state.focusEndAlert, onCheckedChange = null)
    }
    Button(
        onClick = onSave,
        enabled = state.hasUnsavedChanges && !state.saving,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        shape = RoundedCornerShape(Radius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.brandDeep,
            contentColor = colors.onBrand,
        ),
    ) {
        Text(
            if (state.saving) stringResource(R.string.settings_saving)
            else stringResource(R.string.settings_save),
        )
    }
    if (state.saveDone || state.saveFailed) {
        TextButton(
            onClick = onDismissSaveResult,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(
                    if (state.saveDone) R.string.settings_saved else R.string.settings_save_failed,
                ),
                color = if (state.saveDone) colors.success else colors.danger,
            )
        }
    }
    SectionTitle(stringResource(R.string.settings_data))
    Button(
        onClick = onRequestClear,
        enabled = !state.saving && !state.clearing,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xl),
        shape = RoundedCornerShape(Radius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.danger,
            contentColor = colors.onBrand,
        ),
    ) {
        Text(stringResource(R.string.settings_clear_plan))
    }
}

/** Settings → Plan: switch mode, edit the exam date and the finish-by date. Saving regenerates future planner-made blocks only. */
@Composable
private fun PlanSection(
    state: SettingsDetailUiState,
    onPlanMode: (PlanMode) -> Unit,
    onExamDate: (LocalDate) -> Unit,
    onFinishBy: (LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    SectionTitle(stringResource(R.string.settings_plan))
    Text(
        stringResource(R.string.settings_plan_question),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        FilterChip(
            selected = state.planMode == PlanMode.DEADLINE,
            onClick = { onPlanMode(PlanMode.DEADLINE) },
            enabled = !state.saving,
            label = { Text(stringResource(R.string.settings_plan_yes)) },
        )
        FilterChip(
            selected = state.planMode == PlanMode.STEADY,
            onClick = { onPlanMode(PlanMode.STEADY) },
            enabled = !state.saving,
            label = { Text(stringResource(R.string.settings_plan_not_yet)) },
        )
    }
    Text(
        stringResource(
            if (state.planMode == PlanMode.DEADLINE) R.string.settings_plan_deadline_detail else R.string.settings_plan_steady_detail,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    val exam = state.examDate
    if (exam != null) {
        DateRow(
            title = stringResource(R.string.settings_plan_exam_date),
            date = exam,
            note = if (state.examDateIsConfigured && !state.examDateOfficial) stringResource(R.string.settings_plan_exam_expected) else null,
            minDate = LocalDate.now().plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong()),
            maxDate = LocalDate.now().plusYears(3),
            enabled = !state.saving,
            onDate = onExamDate,
        )
    }
    val finishBy = state.finishBy
    if (state.planMode == PlanMode.DEADLINE && finishBy != null) {
        DateRow(
            title = stringResource(R.string.settings_plan_finish_by),
            date = finishBy,
            note = null,
            minDate = LocalDate.now().plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong()),
            maxDate = maxOf(exam ?: finishBy, LocalDate.now().plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong())),
            enabled = !state.saving,
            onDate = onFinishBy,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    title: String,
    date: LocalDate,
    note: String?,
    minDate: LocalDate,
    maxDate: LocalDate,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    var editing by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.text)
            Text(
                text = date.format(formatter) + (note?.let { " · $it" }.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        TextButton(onClick = { editing = true }, enabled = enabled, modifier = Modifier.heightIn(min = Size.touchTarget)) {
            Text(stringResource(R.string.settings_plan_change))
        }
    }
    if (editing) {
        val minMillis = minDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val maxMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    editing = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.settings_cancel)) } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.text,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
    )
}

private fun settingsHour(value: Float): String = java.text.NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)
