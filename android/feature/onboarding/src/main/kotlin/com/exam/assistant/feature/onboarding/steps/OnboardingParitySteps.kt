@file:OptIn(ExperimentalLayoutApi::class)

package com.exam.assistant.feature.onboarding.steps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppStepperControl
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.CalendarMetrics
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.PlanFit
import com.exam.assistant.domain.PlanFitOption
import com.exam.assistant.domain.PlanMode
import com.exam.assistant.domain.StartingPoint
import com.exam.assistant.feature.onboarding.CapacityIssuesCard
import com.exam.assistant.feature.onboarding.CapacityIssue
import com.exam.assistant.feature.onboarding.CommitmentIssue
import com.exam.assistant.feature.onboarding.R
import com.exam.assistant.feature.onboarding.components.OnboardingSelectableCard
import com.exam.assistant.feature.onboarding.model.DAY_SHAPES
import com.exam.assistant.feature.onboarding.model.ExamCatalog
import com.exam.assistant.feature.onboarding.model.OnboardingCommitment
import com.exam.assistant.feature.onboarding.model.OnboardingUiState
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun OnboardingAppearanceStep(
    background: BackgroundAppearance,
    accent: AccentPalette,
    onBackground: (BackgroundAppearance) -> Unit,
    onAccent: (AccentPalette) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        AppearanceLabel(R.string.onboarding_appearance_accent)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AccentPalette.entries.forEach { option ->
                val selected = option == accent
                Surface(
                    onClick = { onAccent(option) },
                    modifier = Modifier.weight(1f),
                    color = if (selected) AppTheme.colors.selectionContainer else AppTheme.colors.elevated,
                    shape = RoundedCornerShape(Radius.md),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Box(
                            modifier = Modifier.size(Size.swatch).clip(CircleShape).background(option.brand),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, null, tint = option.onSwatch)
                        }
                        Text(stringResource(accentLabel(option)), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        AppearanceLabel(R.string.onboarding_appearance_background)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = 3,
        ) {
            BackgroundAppearance.entries.forEach { option ->
                val selected = option == background
                Surface(
                    onClick = { onBackground(option) },
                    modifier = Modifier.weight(1f),
                    color = if (selected) AppTheme.colors.selectionContainer else AppTheme.colors.elevated,
                    shape = RoundedCornerShape(Radius.md),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        BackgroundMiniature(option, accent.brand, selected)
                        Text(stringResource(backgroundLabel(option)), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textSecondary,
    )
}

@Composable
private fun BackgroundMiniature(option: BackgroundAppearance, accent: Color, selected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = RoundedCornerShape(Radius.sm),
        color = option.previewBackground,
        border = if (selected) BorderStroke(Stroke.selected, AppTheme.colors.brand) else null,
    ) {
        Box(Modifier.padding(Spacing.sm)) {
            Box(
                Modifier.fillMaxWidth(.66f).height(Spacing.xs).clip(CircleShape).background(option.previewInk),
            )
            Box(
                Modifier.fillMaxWidth().padding(top = Spacing.md).height(CalendarMetrics.meterHeight).clip(CircleShape)
                    .background(option.previewInk.copy(alpha = .4f)),
            )
            Box(
                Modifier.align(Alignment.BottomStart).size(Spacing.md).clip(CircleShape).background(accent),
            )
        }
    }
}

private data class CoachingChoice(val id: String, val nameRes: Int, val modeRes: Int?, val icon: ImageVector)

private val primaryCoachingChoices = listOf(
    CoachingChoice("self", R.string.onboarding_coaching_self, R.string.onboarding_coaching_self_mode, Icons.Filled.Person),
    CoachingChoice("school", R.string.onboarding_coaching_school, R.string.onboarding_coaching_school_mode, Icons.Filled.School),
    CoachingChoice("coaching", R.string.onboarding_coaching_choice, R.string.onboarding_coaching_choice_mode, Icons.Filled.Groups),
)

@Composable
internal fun OnboardingCoachingStep(selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            stringResource(R.string.onboarding_coaching_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        primaryCoachingChoices.forEach { choice ->
            CoachingRow(choice, selected = selectedId == choice.id) {
                onSelect(choice.id)
            }
        }
    }
}

@Composable
private fun CoachingRow(choice: CoachingChoice, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = if (selected) AppTheme.colors.selectionContainer else AppTheme.colors.elevated,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(Size.themePreview).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(choice.icon, null, tint = if (selected) AppTheme.colors.onSelectionContainer else AppTheme.colors.textSecondary)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(choice.nameRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) AppTheme.colors.onSelectionContainer else AppTheme.colors.text,
                )
                choice.modeRes?.let { modeRes ->
                    Text(
                        stringResource(modeRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) AppTheme.colors.onSelectionContainer else AppTheme.colors.textSecondary,
                    )
                }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = AppTheme.colors.onSelectionContainer)
        }
    }
}

@Composable
internal fun OnboardingCommitmentsStep(
    state: OnboardingUiState,
    issues: List<CommitmentIssue>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleDay: (String, Int) -> Unit,
    onStart: (String, Int) -> Unit,
    onEnd: (String, Int) -> Unit,
    onWake: (Int) -> Unit,
    onSleep: (Int) -> Unit,
) {
    var showAddOptions by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(stringResource(R.string.onboarding_commitments_lede), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppearanceLabel(R.string.onboarding_commitments_fixed_times)
            OutlinedButton(onClick = { showAddOptions = !showAddOptions }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(Size.smallIcon),
                )
                Text(
                    text = stringResource(R.string.onboarding_commitments_add),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        if (showAddOptions) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("school", "coaching", "lecture", "tuition", "work", "meal", "commute", "other").forEach { kind ->
                    val preset = commitmentKindLabel(kind)
                    Surface(
                        onClick = {
                            onAdd(kind)
                            showAddOptions = false
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    ) {
                        Text(stringResource(preset), modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.commitments.isEmpty()) {
            Surface(shape = RoundedCornerShape(Radius.lg), border = BorderStroke(Stroke.hairline, AppTheme.colors.border), color = Color.Transparent) {
                Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Filled.EventAvailable, null)
                    Text(stringResource(R.string.onboarding_commitments_empty))
                }
            }
        } else {
            state.commitments.forEach { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    issues = issues.filter { it.commitmentId == commitment.id },
                    onRemove = onRemove,
                    onRename = onRename,
                    onToggleDay = onToggleDay,
                    onStart = onStart,
                    onEnd = onEnd,
                )
            }
        }
        AppearanceLabel(R.string.onboarding_commitments_awake)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TimeField(stringResource(R.string.onboarding_commitments_up_at), state.wakeMinute, onWake, Modifier.weight(1f))
            TimeField(stringResource(R.string.onboarding_commitments_lights_out), state.sleepMinute, onSleep, Modifier.weight(1f))
        }
        if (CommitmentIssue.WakeAfterSleep in issues) {
            InlineCommitmentIssue(CommitmentIssue.WakeAfterSleep)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.EventAvailable, null, tint = AppTheme.colors.brand)
            Text(
                stringResource(R.string.onboarding_commitments_plan_around),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CommitmentCard(
    commitment: OnboardingCommitment,
    issues: List<CommitmentIssue>,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleDay: (String, Int) -> Unit,
    onStart: (String, Int) -> Unit,
    onEnd: (String, Int) -> Unit,
) {
    val defaultLabel = stringResource(commitment.labelRes)
    val label = commitment.customLabel ?: defaultLabel
    var showRename by remember(commitment.id) { mutableStateOf(false) }
    var draftLabel by remember(commitment.id) { mutableStateOf(label) }
    Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.elevated) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = AppTheme.colors.brand)
                Text(label, modifier = Modifier.weight(1f).padding(start = Spacing.sm), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.onboarding_commitments_time_range,
                        clockLabel(commitment.startMinute),
                        clockLabel(commitment.endMinute),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.textSecondary,
                )
                IconButton(
                    onClick = {
                        draftLabel = label
                        showRename = true
                    },
                    modifier = Modifier.size(Size.dayControl),
                ) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.onboarding_commitments_rename, label))
                }
                IconButton(onClick = { onRemove(commitment.id) }, modifier = Modifier.size(Size.dayControl)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.onboarding_commitments_remove, label))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TimeField(stringResource(R.string.onboarding_commitments_starts), commitment.startMinute, { onStart(commitment.id, it) }, Modifier.weight(1f))
                TimeField(stringResource(R.string.onboarding_commitments_ends), commitment.endMinute, { onEnd(commitment.id, it) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                dayOptions.forEach { (day, labelRes) ->
                    val selected = day in commitment.days
                    val dayName = stringResource(dayNames[day])
                    Surface(
                        onClick = { onToggleDay(commitment.id, day) },
                        modifier = Modifier
                            .weight(1f)
                            .height(Size.dayControl)
                            .semantics {
                                role = Role.Checkbox
                                this.selected = selected
                                contentDescription = dayName
                            },
                        shape = CircleShape,
                        color = if (selected) AppTheme.colors.selectionContainer else Color.Transparent,
                        border = if (selected) null else BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) AppTheme.colors.onSelectionContainer else AppTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }
            issues.forEach { issue -> InlineCommitmentIssue(issue) }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.onboarding_commitments_rename_title)) },
            text = {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it.take(40) },
                    label = { Text(stringResource(R.string.onboarding_commitments_rename_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(commitment.id, draftLabel)
                        showRename = false
                    },
                ) { Text(stringResource(R.string.onboarding_commitments_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.onboarding_commitments_rename_cancel))
                }
            },
        )
    }
}

@Composable
private fun InlineCommitmentIssue(issue: CommitmentIssue) {
    Text(
        text = issueText(issue),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.danger,
    )
}

@Composable
private fun issueText(issue: CommitmentIssue): String = when (issue) {
    CommitmentIssue.WakeAfterSleep -> stringResource(R.string.onboarding_commitments_wake_after_sleep)
    is CommitmentIssue.NoDays -> stringResource(R.string.onboarding_commitments_no_days, stringResource(issue.labelRes))
    is CommitmentIssue.InvalidRange -> stringResource(R.string.onboarding_commitments_invalid_range, stringResource(issue.labelRes))
    is CommitmentIssue.OutsideWakingHours -> stringResource(R.string.onboarding_commitments_outside_waking, stringResource(issue.labelRes))
}

@Composable
private fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textSecondary)
        Surface(onClick = { open = true }, shape = RoundedCornerShape(Radius.sm), border = BorderStroke(Stroke.hairline, AppTheme.colors.border), color = Color.Transparent) {
            Text(clockLabel(minute), modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Size.summaryTrack), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (open) MinutePickerDialog(minute, { open = false }, { onChange(it); open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinute / 60 % 24, initialMinute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

/** "Do you have a date you want to finish the syllabus by?" Yes is deadline mode (preselected), Not yet is steady mode. */
@Composable
internal fun OnboardingModeStep(
    state: OnboardingUiState,
    onMode: (PlanMode) -> Unit,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OnboardingSelectableCard(
                title = stringResource(R.string.onboarding_mode_yes),
                selected = state.planMode == PlanMode.DEADLINE,
                onClick = { onMode(PlanMode.DEADLINE) },
                showUnselectedIndicator = true,
                trailing = {
                    Text(
                        stringResource(R.string.onboarding_mode_yes_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                },
            )
            OnboardingSelectableCard(
                title = stringResource(R.string.onboarding_mode_not_yet),
                selected = state.planMode == PlanMode.STEADY,
                onClick = { onMode(PlanMode.STEADY) },
                showUnselectedIndicator = true,
                trailing = {
                    Text(
                        stringResource(R.string.onboarding_mode_not_yet_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                },
            )
        }
        if (state.planMode == PlanMode.DEADLINE) {
            OnboardingTargetDateStep(state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate)
        } else {
            ExamDateRow(state.examDate, state.examDateOfficial, state.examDateIsConfigured, onExamDate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingTargetDateStep(
    targetDate: LocalDate,
    examDate: LocalDate,
    examDateOfficial: Boolean,
    examDateIsConfigured: Boolean,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()) }
    val days = ChronoUnit.DAYS.between(today, targetDate).toInt().coerceAtLeast(1)
    val minMillis = today.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = maxOf(examDate, today.plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
        },
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            if (selected != targetDate) onDate(selected)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(targetDate.format(formatter), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.onboarding_date_personal_target), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
            Surface(shape = CircleShape, color = AppTheme.colors.selectionContainer) {
                Text(pluralStringResource(R.plurals.onboarding_date_days_chip, days, days), modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), style = MaterialTheme.typography.labelLarge)
            }
        }
        ExamDateRow(examDate, examDateOfficial, examDateIsConfigured, onExamDate)
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            title = null,
            headline = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The exam date with its honesty label ("expected, not yet announced" until it is official or the student's own), and a way to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDateRow(
    examDate: LocalDate,
    official: Boolean,
    isConfigured: Boolean,
    onExamDate: (LocalDate) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()) }
    var editing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                if (official || !isConfigured) R.string.onboarding_date_exam_official else R.string.onboarding_date_exam_expected,
                examDate.format(formatter),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = Size.touchTarget)) {
            Text(stringResource(R.string.onboarding_exam_date_change))
        }
    }
    if (editing) {
        val today = LocalDate.now()
        val minMillis = today.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val maxMillis = today.plusYears(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = examDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onExamDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    editing = false
                }) { Text(stringResource(R.string.onboarding_ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.onboarding_cancel)) } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/**
 * "Which class / attempt?", then "Mark chapters you've already finished": tick a whole subject or class and
 * untick the exceptions. A dropper starts with everything ticked.
 */
@Composable
internal fun OnboardingStartStep(
    state: OnboardingUiState,
    onSelectStartingPoint: (StartingPoint) -> Unit,
    onToggleSubject: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
    onToggleClass: (Int) -> Unit,
) {
    var expandedSubjectId by remember { mutableStateOf<String?>(null) }
    val allChapters = state.syllabusSubjects.flatMap { it.chapters }
    val finishedCount = allChapters.count { it.id in state.coveredChapterIds }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppearanceLabel(R.string.onboarding_start_question)
        listOf(
            Triple(StartingPoint.CLASS_11, R.string.onboarding_start_class_11, R.string.onboarding_start_class_11_detail),
            Triple(StartingPoint.CLASS_12, R.string.onboarding_start_class_12, R.string.onboarding_start_class_12_detail),
            Triple(StartingPoint.DROPPER, R.string.onboarding_start_dropper, R.string.onboarding_start_dropper_detail),
        ).forEach { (point, title, detail) ->
            OnboardingSelectableCard(
                title = stringResource(title),
                selected = state.startingPoint == point,
                onClick = { onSelectStartingPoint(point) },
                showUnselectedIndicator = true,
                trailing = {
                    Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                },
            )
        }
        if (state.startingPoint != null && allChapters.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            AppearanceLabel(R.string.onboarding_finished_title)
            Text(
                pluralStringResource(R.plurals.onboarding_finished_summary, allChapters.size, finishedCount, allChapters.size),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(11, 12).forEach { classNumber ->
                    val ids = allChapters.filter { it.classNumber == classNumber }.map { it.id }
                    if (ids.isNotEmpty()) {
                        FilterChip(
                            selected = ids.all { it in state.coveredChapterIds },
                            onClick = { onToggleClass(classNumber) },
                            label = { Text(stringResource(R.string.onboarding_finished_all_class, classNumber)) },
                            modifier = Modifier.heightIn(min = Size.touchTarget),
                        )
                    }
                }
            }
            state.syllabusSubjects.forEach { subject ->
                val ids = subject.chapters.map { it.id }
                val done = ids.count { it in state.coveredChapterIds }
                val all = ids.isNotEmpty() && done == ids.size
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    border = BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    color = AppTheme.colors.elevated,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.onboarding_finished_subject_count, done, ids.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                )
                            }
                            TextButton(onClick = { onToggleSubject(subject.id) }, modifier = Modifier.heightIn(min = Size.touchTarget)) {
                                Text(stringResource(if (all) R.string.onboarding_finished_clear else R.string.onboarding_finished_all))
                            }
                            IconButton(onClick = { expandedSubjectId = if (expandedSubjectId == subject.id) null else subject.id }) {
                                Icon(
                                    if (expandedSubjectId == subject.id) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(
                                        if (expandedSubjectId == subject.id) R.string.onboarding_syllabus_collapse_subject
                                        else R.string.onboarding_syllabus_expand_subject,
                                        subject.name,
                                    ),
                                )
                            }
                        }
                        if (expandedSubjectId == subject.id) {
                            subject.chapters.forEach { chapter ->
                                OnboardingSelectableCard(
                                    title = chapter.name,
                                    selected = chapter.id in state.coveredChapterIds,
                                    onClick = { onToggleChapter(chapter.id) },
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                    showUnselectedIndicator = true,
                                )
                            }
                            Box(Modifier.height(Spacing.sm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OnboardingSyllabusStep(
    state: OnboardingUiState,
    onToggleSubject: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
) {
    var expandedSubjectId by remember(state.examId) { mutableStateOf<String?>(null) }
    val chapterCount = state.syllabusSubjects.sumOf { it.chapters.size }
    val selectedCount = state.selectedChapterIds.size
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (ExamCatalog.hasBundledSyllabus(state.examId)) {
            Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.selectionContainer) {
                Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null)
                    Column {
                        Text(stringResource(R.string.onboarding_syllabus_ncert_ready), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.onboarding_syllabus_selected_count, selectedCount, chapterCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.onboarding_syllabus_choose_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            state.syllabusSubjects.forEach { subject ->
                val subjectIds = subject.chapters.map { it.id }
                val subjectSelectedCount = subjectIds.count { it in state.selectedChapterIds }
                val allSubjectSelected = subjectIds.isNotEmpty() && subjectSelectedCount == subjectIds.size
                Surface(
                    shape = RoundedCornerShape(Radius.lg),
                    border = BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    color = AppTheme.colors.elevated,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.onboarding_syllabus_subject_selected, subjectSelectedCount, subjectIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                )
                            }
                            TextButton(onClick = { onToggleSubject(subject.id) }) {
                                Text(stringResource(if (allSubjectSelected) R.string.onboarding_syllabus_clear_all else R.string.onboarding_syllabus_select_all))
                            }
                            IconButton(onClick = {
                                expandedSubjectId = if (expandedSubjectId == subject.id) null else subject.id
                            }) {
                                Icon(
                                    if (expandedSubjectId == subject.id) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(
                                        if (expandedSubjectId == subject.id) R.string.onboarding_syllabus_collapse_subject
                                        else R.string.onboarding_syllabus_expand_subject,
                                        subject.name,
                                    ),
                                )
                            }
                        }
                        if (expandedSubjectId == subject.id) {
                            subject.chapters.groupBy { it.classNumber }.forEach { (classNumber, chapters) ->
                                Text(
                                    text = classNumber?.let { stringResource(R.string.onboarding_syllabus_class, it) }
                                        ?: stringResource(R.string.onboarding_syllabus_other),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                )
                                chapters.forEach { chapter ->
                                    OnboardingSelectableCard(
                                        title = chapter.name,
                                        selected = chapter.id in state.selectedChapterIds,
                                        onClick = { onToggleChapter(chapter.id) },
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                        showUnselectedIndicator = true,
                                    )
                                }
                            }
                            Box(Modifier.height(Spacing.sm))
                        }
                    }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(Radius.lg), border = BorderStroke(Stroke.hairline, AppTheme.colors.border), color = Color.Transparent) {
                Text(stringResource(R.string.onboarding_syllabus_blank), modifier = Modifier.padding(Spacing.xl), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun OnboardingHoursParityStep(
    state: OnboardingUiState,
    capacityIssues: List<CapacityIssue>,
    onSelectWork: (String) -> Unit,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
) {
    val weeklyHours = state.weekdayHours * 5 + state.weekendHours * 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppearanceLabel(R.string.onboarding_hours_start_schedule)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DAY_SHAPES.forEach { shape ->
                    val selected = shape.id == state.workId
                    Surface(
                        onClick = { onSelectWork(shape.id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.lg),
                        color = if (selected) AppTheme.colors.selectionContainer else Color.Transparent,
                        border = if (selected) null else BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    ) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            Text(stringResource(shape.labelRes), style = MaterialTheme.typography.labelLarge)
                            Text(
                                stringResource(R.string.onboarding_shape_weekday_hours, planHour(shape.weekdayHours)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(R.string.onboarding_shape_weekend_hours, planHour(shape.weekendHours)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        HoursStepper(
            label = stringResource(R.string.onboarding_hours_weekdays),
            value = state.weekdayHours,
            minimum = 1f,
            maximum = 14f,
            onChange = onWeekdayChange,
        )
        HoursStepper(
            label = stringResource(R.string.onboarding_hours_weekends),
            value = state.weekendHours,
            minimum = 1f,
            maximum = 16f,
            onChange = onWeekendChange,
        )
        Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.elevated) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.onboarding_hours_each_week), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Surface(shape = CircleShape, color = AppTheme.colors.selectionContainer) {
                    Text(
                        stringResource(R.string.onboarding_hours_weekly_value, planHour(weeklyHours)),
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (capacityIssues.isNotEmpty()) CapacityIssuesCard(capacityIssues)
    }
}

@Composable
private fun HoursStepper(
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    onChange: (Float) -> Unit,
) {
    AppValueStepper(
        label = label,
        value = stringResource(R.string.onboarding_hours_value, planHour(value)),
        decreaseContentDescription = stringResource(R.string.onboarding_hours_decrease, label),
        increaseContentDescription = stringResource(R.string.onboarding_hours_increase, label),
        onDecrease = { onChange((value - .5f).coerceAtLeast(minimum)) },
        onIncrease = { onChange((value + .5f).coerceAtMost(maximum)) },
        decreaseEnabled = value > minimum,
        increaseEnabled = value < maximum,
    )
}

@Composable
internal fun OnboardingPlanStep(
    state: OnboardingUiState,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
    onApplyFitOption: (PlanFitOption) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val shortDatePattern = stringResource(R.string.onboarding_plan_short_date_pattern)
    val dateFormatter = remember(shortDatePattern) { DateTimeFormatter.ofPattern(shortDatePattern, Locale.getDefault()) }
    val targetLabel = state.targetDate.format(dateFormatter)
    val todayLabel = LocalDate.now().format(dateFormatter)
    val fit = state.fit

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        when {
            !state.useProvidedSyllabus -> PlanVerdict(stringResource(R.string.onboarding_plan_verdict_empty))
            state.planMode == PlanMode.STEADY -> {
                PlanVerdict(stringResource(R.string.onboarding_plan_steady))
                when {
                    state.projectionLoading -> CircularProgressIndicator()
                    state.projectedFinish != null -> PlanStatRow(
                        stringResource(R.string.onboarding_plan_projected_finish),
                        stringResource(R.string.onboarding_plan_projected_value, state.projectedFinish.format(dateFormatter)),
                    )
                    else -> Text(
                        stringResource(R.string.onboarding_plan_projected_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            fit == null -> CircularProgressIndicator()
            else -> {
                val estimateLabel = stringResource(
                    if (fit.calibrated) R.string.onboarding_plan_at_your_pace else R.string.onboarding_plan_estimate_label,
                )
                PlanVerdict(
                    if (fit.fits) {
                        stringResource(R.string.onboarding_plan_fits, targetLabel)
                    } else {
                        stringResource(
                            R.string.onboarding_plan_short,
                            planHour(fit.shortByMinutes / 60f),
                            estimateLabel,
                            targetLabel,
                        )
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_learn),
                        stringResource(R.string.onboarding_plan_hours_value, planHour(fit.learnMinutes / 60f), estimateLabel),
                    )
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_practice),
                        stringResource(R.string.onboarding_plan_hours_value, planHour(fit.practiceMinutes / 60f), estimateLabel),
                    )
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_revision),
                        if (fit.revisionWindowDays > 0) {
                            pluralStringResource(R.plurals.onboarding_plan_revision_days, fit.revisionWindowDays, fit.revisionWindowDays)
                        } else {
                            stringResource(R.string.onboarding_plan_revision_none)
                        },
                    )
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_verdict),
                        if (fit.fits) {
                            stringResource(R.string.onboarding_plan_stat_fits)
                        } else {
                            stringResource(R.string.onboarding_plan_stat_short, planHour(fit.shortByMinutes / 60f), estimateLabel)
                        },
                    )
                }
                if (!fit.fits) {
                    FitOptions(fit, targetLabel, dateFormatter, state.fitLoading, onApplyFitOption)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlanSummaryRow(
                icon = Icons.Filled.CalendarToday,
                label = stringResource(R.string.onboarding_plan_every_weekday),
                value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekdayHours)),
                editing = editing,
            ) {
                AppStepperControl(
                    value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekdayHours)),
                    decreaseContentDescription = stringResource(R.string.onboarding_hours_decrease, stringResource(R.string.onboarding_hours_weekdays)),
                    increaseContentDescription = stringResource(R.string.onboarding_hours_increase, stringResource(R.string.onboarding_hours_weekdays)),
                    onDecrease = { onWeekdayChange((state.weekdayHours - .5f).coerceAtLeast(1f)) },
                    onIncrease = { onWeekdayChange((state.weekdayHours + .5f).coerceAtMost(14f)) },
                    decreaseEnabled = state.weekdayHours > 1f,
                    increaseEnabled = state.weekdayHours < 14f,
                )
            }
            PlanSummaryRow(
                icon = Icons.Filled.Weekend,
                label = stringResource(R.string.onboarding_plan_every_weekend),
                value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekendHours)),
                editing = editing,
            ) {
                AppStepperControl(
                    value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekendHours)),
                    decreaseContentDescription = stringResource(R.string.onboarding_hours_decrease, stringResource(R.string.onboarding_hours_weekends)),
                    increaseContentDescription = stringResource(R.string.onboarding_hours_increase, stringResource(R.string.onboarding_hours_weekends)),
                    onDecrease = { onWeekendChange((state.weekendHours - .5f).coerceAtLeast(1f)) },
                    onIncrease = { onWeekendChange((state.weekendHours + .5f).coerceAtMost(16f)) },
                    decreaseEnabled = state.weekendHours > 1f,
                    increaseEnabled = state.weekendHours < 16f,
                )
            }
            PlanSummaryRow(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.onboarding_plan_starts_ends, todayLabel),
                value = targetLabel,
                editing = editing,
            ) {
                OnboardingTargetDateStep(state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate)
            }
        }
        OutlinedButton(
            onClick = { editing = !editing },
            modifier = Modifier.fillMaxWidth().height(Size.ctaHeight),
            shape = CircleShape,
        ) {
            Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, null)
            Text(
                stringResource(if (editing) R.string.onboarding_plan_done_editing else R.string.onboarding_plan_edit),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(Icons.Filled.Settings, null, modifier = Modifier.size(Size.smallIcon), tint = AppTheme.colors.textSecondary)
            Text(stringResource(R.string.onboarding_plan_settings_note), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        // §11.3: the usage-data disclosure, on the last step before the plan is made.
        Text(stringResource(R.string.onboarding_usage_data_note), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun PlanVerdict(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textSecondary)
}

@Composable
private fun PlanStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
    }
}

/** The four ways out of a plan that is short. Each button applies a choice the planner has already verified. */
@Composable
private fun FitOptions(
    fit: PlanFit,
    targetLabel: String,
    dateFormatter: DateTimeFormatter,
    loading: Boolean,
    onApply: (PlanFitOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.onboarding_plan_options_title), style = MaterialTheme.typography.titleSmall)
        fit.options.forEach { option ->
            val (title, detail, action) = when (option) {
                is PlanFitOption.AddMinutesPerDay -> Triple(
                    stringResource(R.string.onboarding_plan_option_add, option.minutes),
                    stringResource(R.string.onboarding_plan_option_add_detail, targetLabel),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.ParkChapters -> Triple(
                    pluralStringResource(R.plurals.onboarding_plan_option_park, option.count, option.count),
                    stringResource(R.string.onboarding_plan_option_park_detail, planHour(option.minutesFreed / 60f)),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.MoveFinishBy -> Triple(
                    stringResource(R.string.onboarding_plan_option_move, option.date.format(dateFormatter)),
                    stringResource(R.string.onboarding_plan_option_move_detail),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.AcceptCoverage -> Triple(
                    stringResource(R.string.onboarding_plan_option_accept, option.percent),
                    stringResource(R.string.onboarding_plan_option_accept_detail),
                    R.string.onboarding_plan_option_continue,
                )
            }
            Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.elevated) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                    }
                    TextButton(onClick = { onApply(option) }, enabled = !loading) { Text(stringResource(action)) }
                }
            }
        }
    }
}

@Composable
private fun PlanSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    editing: Boolean,
    editor: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.elevated) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(Size.ctaHeight).padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Icon(icon, null, tint = AppTheme.colors.textSecondary)
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.brand)
            }
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.End,
                ) { editor() }
            }
        }
    }
}

private fun planHour(value: Float): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

private val dayOptions = listOf(
    1 to R.string.onboarding_day_monday,
    2 to R.string.onboarding_day_tuesday,
    3 to R.string.onboarding_day_wednesday,
    4 to R.string.onboarding_day_thursday,
    5 to R.string.onboarding_day_friday,
    6 to R.string.onboarding_day_saturday,
    0 to R.string.onboarding_day_sunday,
)

private val dayNames = listOf(
    R.string.onboarding_day_sunday_full,
    R.string.onboarding_day_monday_full,
    R.string.onboarding_day_tuesday_full,
    R.string.onboarding_day_wednesday_full,
    R.string.onboarding_day_thursday_full,
    R.string.onboarding_day_friday_full,
    R.string.onboarding_day_saturday_full,
)

private fun accentLabel(accent: AccentPalette): Int = when (accent) {
    AccentPalette.Blue -> R.string.onboarding_accent_blue
    AccentPalette.Purple -> R.string.onboarding_accent_purple
    AccentPalette.Green -> R.string.onboarding_accent_green
    AccentPalette.Amber -> R.string.onboarding_accent_amber
    AccentPalette.Rose -> R.string.onboarding_accent_rose
}

private fun backgroundLabel(background: BackgroundAppearance): Int = when (background) {
    BackgroundAppearance.System -> R.string.onboarding_background_system
    BackgroundAppearance.Light -> R.string.onboarding_background_light
    BackgroundAppearance.Dark -> R.string.onboarding_background_dark
    BackgroundAppearance.Grey -> R.string.onboarding_background_grey
    BackgroundAppearance.Slate -> R.string.onboarding_background_slate
}

private fun commitmentKindLabel(kind: String): Int = when (kind) {
    "school" -> R.string.onboarding_commitment_school
    "coaching" -> R.string.onboarding_commitment_coaching
    "lecture" -> R.string.onboarding_commitment_lecture
    "tuition" -> R.string.onboarding_commitment_tuition
    "work" -> R.string.onboarding_commitment_job
    "meal" -> R.string.onboarding_commitment_meal
    "commute" -> R.string.onboarding_commitment_commute
    else -> R.string.onboarding_commitment_other
}

private fun clockLabel(minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60 % 24)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
}
