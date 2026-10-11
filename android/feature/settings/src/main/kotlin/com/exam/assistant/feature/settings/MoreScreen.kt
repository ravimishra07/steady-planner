package com.exam.assistant.feature.settings

import android.text.format.DateFormat
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.exam.assistant.core.design.rememberHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.exam.assistant.core.design.Stroke
import androidx.compose.foundation.layout.height
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import com.exam.assistant.core.data.ProStatus
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppListDivider
import com.exam.assistant.core.design.AppListGroup
import com.exam.assistant.core.design.AppListRow
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Radius
import kotlin.math.roundToInt

/** Everything Settings can do; one holder keeps the screen signature readable. */
@Immutable
class SettingsActions(
    val onRetry: () -> Unit,
    val onWeekdayHours: (Float) -> Unit,
    val onWeekendHours: (Float) -> Unit,
    val onFinishBy: (LocalDate) -> Unit,
    val onExamDate: (LocalDate) -> Unit,
    val onEditCommitments: () -> Unit,
    val onLiveBacklogSplit: (Int) -> Unit,
    val onDismissSaveResult: () -> Unit,
    val onFocusLength: (Int) -> Unit,
    val onReminderToggle: (Boolean) -> Unit,
    val onReminderTime: (Int) -> Unit,
    val onFocusEndAlert: (Boolean) -> Unit,
    val onOpenPaywall: () -> Unit,
    val onRestorePurchase: () -> Unit,
    val onDismissProStatus: () -> Unit,
    val onDebugForcePro: (Boolean) -> Unit,
    val onOpenAppearance: () -> Unit,
    val onShareUsageData: (Boolean) -> Unit,
    val onExportBackup: () -> Unit,
    val onImportBackup: () -> Unit,
    val onConfirmImport: () -> Unit,
    val onDismissImport: () -> Unit,
    val onDismissBackupMessage: () -> Unit,
    val onRedoSetup: () -> Unit,
    val onEraseAll: () -> Unit,
    /** Null hides the row (no support address configured). */
    val onContactSupport: (() -> Unit)?,
    val onRateApp: () -> Unit,
    val onOpenPolicy: (String) -> Unit,
)

/**
 * Settings, one level: Plan, Study, Pro, App, Data, Help. Every value is changed in place and saved
 * at once; nothing here restarts onboarding except "Redo setup".
 */
@Composable
fun MoreScreen(
    detail: SettingsDetailUiState,
    pro: ProRowState,
    fixedBlockCount: Int,
    liveBacklogSplit: Int?,
    background: BackgroundAppearance,
    appVersion: String,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var showRedoDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    if (detail.loading) {
        SettingsStatusScreen(stringResource(R.string.more_title), stringResource(R.string.settings_loading), modifier, loading = true)
        return
    }
    if (detail.loadFailed) {
        SettingsStatusScreen(
            title = stringResource(R.string.more_title),
            message = stringResource(R.string.settings_load_failed),
            actionLabel = stringResource(R.string.settings_retry),
            onAction = actions.onRetry,
            modifier = modifier,
        )
        return
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )

        SectionLabel(stringResource(R.string.more_section_plan_short))
        ProfileGroup {
            MenuRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.more_weekday_hours),
                options = hourChoices(detail.weekdayHours, MAX_WEEKDAY_HOURS),
                selected = detail.weekdayHours,
                label = { stringResource(R.string.settings_hours_value, compactHours(it)) },
                onSelect = actions.onWeekdayHours,
            )
            AppListDivider()
            MenuRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.more_weekend_hours),
                options = hourChoices(detail.weekendHours, MAX_WEEKEND_HOURS),
                selected = detail.weekendHours,
                label = { stringResource(R.string.settings_hours_value, compactHours(it)) },
                onSelect = actions.onWeekendHours,
            )
            AppListDivider()
            DateRow(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.more_finish_by_date),
                date = detail.finishBy,
                format = dateFormat,
                onPick = actions.onFinishBy,
            )
            AppListDivider()
            DateRow(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.more_exam_date),
                date = detail.examDate,
                format = dateFormat,
                // The official date is fixed; an expected one can be corrected.
                onPick = if (detail.examDateOfficial) null else actions.onExamDate,
            )
            AppListDivider()
            SettingsValueRow(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.more_fixed_hours),
                value = pluralStringResource(R.plurals.more_blocks, fixedBlockCount, fixedBlockCount),
                onClick = actions.onEditCommitments,
            )
            if (liveBacklogSplit != null) {
                AppListDivider()
                SettingsValueRow(
                    icon = Icons.Outlined.School,
                    title = stringResource(R.string.more_backlog_split),
                    value = stringResource(R.string.more_backlog_split_value, liveBacklogSplit, 100 - liveBacklogSplit),
                    onClick = { showSplitDialog = true },
                )
            }
        }
        if (detail.saveFailed) {
            TextButton(onClick = actions.onDismissSaveResult, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save_failed), color = colors.danger)
            }
        }

        SectionLabel(stringResource(R.string.more_section_study))
        ProfileGroup {
            MenuRow(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.settings_focus_length),
                options = (FOCUS_CHOICES + detail.focusDurationMinutes).distinct().sorted(),
                selected = detail.focusDurationMinutes,
                label = { stringResource(R.string.settings_focus_minutes, it) },
                onSelect = actions.onFocusLength,
            )
            AppListDivider()
            SwitchRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.settings_focus_end_alert),
                checked = detail.focusEndAlert,
                onChange = actions.onFocusEndAlert,
            )
            AppListDivider()
            SwitchRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.settings_reminder_daily),
                checked = detail.reminderEnabled,
                onChange = actions.onReminderToggle,
            )
            if (detail.reminderEnabled) {
                AppListDivider()
                TimeRow(
                    icon = Icons.Outlined.Schedule,
                    title = stringResource(R.string.settings_reminder_time),
                    minuteOfDay = detail.reminderMinuteOfDay,
                    onPick = actions.onReminderTime,
                )
            }
        }
        if (detail.reminderPermissionDenied) {
            Text(
                text = stringResource(R.string.settings_reminder_denied),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )
        }

        SectionLabel(stringResource(R.string.more_section_pro))
        ProfileGroup {
            ProSection(pro, actions.onOpenPaywall, actions.onRestorePurchase, actions.onDismissProStatus, actions.onDebugForcePro)
        }

        SectionLabel(stringResource(R.string.more_section_app_short))
        ProfileGroup {
            SettingsValueRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.more_appearance),
                value = stringResource(
                    when (background) {
                        BackgroundAppearance.System -> R.string.settings_theme_system
                        BackgroundAppearance.Light -> R.string.settings_theme_light
                        BackgroundAppearance.Dark -> R.string.settings_theme_dark
                        BackgroundAppearance.Grey -> R.string.settings_background_grey
                        BackgroundAppearance.Slate -> R.string.settings_background_slate
                    },
                ),
                onClick = actions.onOpenAppearance,
            )
            AppListDivider()
            SwitchRow(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.settings_share_usage),
                checked = detail.shareUsageData,
                onChange = actions.onShareUsageData,
            )
        }

        SectionLabel(stringResource(R.string.more_section_data))
        ProfileGroup {
            SettingsValueRow(Icons.Outlined.Upload, stringResource(R.string.settings_export_backup), "", actions.onExportBackup)
            AppListDivider()
            SettingsValueRow(Icons.Outlined.Download, stringResource(R.string.settings_import_backup), "", actions.onImportBackup)
            AppListDivider()
            SettingsValueRow(Icons.Outlined.Replay, stringResource(R.string.more_redo_setup), "") { showRedoDialog = true }
            AppListDivider()
            DangerRow(
                icon = Icons.Outlined.DeleteForever,
                title = stringResource(R.string.more_erase_all),
                busy = erasing,
                onClick = { showEraseDialog = true },
            )
        }
        detail.backupMessage?.let { message ->
            TextButton(onClick = actions.onDismissBackupMessage, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        when (message) {
                            BackupMessage.EXPORTED -> R.string.settings_backup_exported
                            BackupMessage.EXPORT_FAILED -> R.string.settings_backup_export_failed
                            BackupMessage.IMPORT_TOO_NEW -> R.string.settings_backup_too_new
                            BackupMessage.IMPORT_INCOMPATIBLE -> R.string.settings_backup_incompatible
                            BackupMessage.IMPORT_FAILED -> R.string.settings_backup_import_failed
                        },
                    ),
                    color = if (message == BackupMessage.EXPORTED) colors.success else colors.danger,
                )
            }
        }

        SectionLabel(stringResource(R.string.more_section_help))
        ProfileGroup {
            actions.onContactSupport?.let { contact ->
                SettingsValueRow(Icons.Outlined.Email, stringResource(R.string.more_contact_support), "", contact)
                AppListDivider()
            }
            SettingsValueRow(Icons.Outlined.StarRate, stringResource(R.string.more_rate_app), "", actions.onRateApp)
            AppListDivider()
            ProfileRow(Icons.Outlined.Lock, R.string.more_privacy, R.string.more_privacy_sub) { actions.onOpenPolicy("privacy") }
            AppListDivider()
            ProfileRow(Icons.Outlined.Info, R.string.more_terms, R.string.more_terms_sub) { actions.onOpenPolicy("terms") }
        }
        Text(
            text = stringResource(R.string.more_about_version, appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
            textAlign = TextAlign.Center,
        )
    }

    if (showRedoDialog) {
        AlertDialog(
            onDismissRequest = { showRedoDialog = false },
            title = { Text(stringResource(R.string.more_redo_confirm_title)) },
            text = { Text(stringResource(R.string.more_redo_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRedoDialog = false
                    actions.onRedoSetup()
                }) { Text(stringResource(R.string.more_redo_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { showRedoDialog = false }) { Text(stringResource(R.string.more_redo_confirm_no)) } },
        )
    }
    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text(stringResource(R.string.more_erase_all_title)) },
            text = { Text(stringResource(R.string.more_log_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEraseDialog = false
                        erasing = true
                        actions.onEraseAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
                ) { Text(stringResource(R.string.more_erase_all)) }
            },
            dismissButton = { TextButton(onClick = { showEraseDialog = false }) { Text(stringResource(R.string.more_redo_confirm_no)) } },
        )
    }
    if (detail.pendingImport != null) {
        AlertDialog(
            onDismissRequest = actions.onDismissImport,
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = actions.onConfirmImport, enabled = !detail.backupBusy) {
                    Text(stringResource(if (detail.backupBusy) R.string.settings_importing else R.string.settings_import_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissImport, enabled = !detail.backupBusy) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
    if (showSplitDialog && liveBacklogSplit != null) {
        BacklogSplitDialog(initial = liveBacklogSplit, onSave = actions.onLiveBacklogSplit, onDismiss = { showSplitDialog = false })
    }
}

private const val MAX_WEEKDAY_HOURS = 14
private const val MAX_WEEKEND_HOURS = 16
private val FOCUS_CHOICES = listOf(25, 50, 90)

private fun hourChoices(current: Float, maximum: Int): List<Float> =
    ((1..maximum).map { it.toFloat() } + current).distinct().sorted()

/** A row whose value opens a menu of choices right under it. */
@Composable
private fun <T> MenuRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.text)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            onClick = {
                                open = false
                                haptics.select()
                                if (option != selected) onSelect(option)
                            },
                            trailingIcon = if (option == selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Surface(color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = { haptics.select(); onChange(it) })
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** A date with a picker; [onPick] null makes it read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(icon: ImageVector, title: String, date: LocalDate?, format: DateTimeFormatter, onPick: ((LocalDate) -> Unit)?) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, enabled = onPick != null, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(date?.format(format).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
    if (open && onPick != null) {
        val zone = ZoneOffset.UTC
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate().isBefore(LocalDate.now())
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_cancel)) } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(icon: ImageVector, title: String, minuteOfDay: Int, onPick: (Int) -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    val label = remember(minuteOfDay) {
        time.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault()))
    }
    Surface(onClick = { open = true }, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
    if (open) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(pickerState.hour * 60 + pickerState.minute)
                    open = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun DangerRow(icon: ImageVector, title: String, busy: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(onClick = onClick, enabled = !busy, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.danger)
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.danger, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(modifier = Modifier.size(Size.standardIcon))
        }
    }
}

@Composable
private fun ProSection(
    pro: ProRowState,
    onOpenPaywall: () -> Unit,
    onRestorePurchase: () -> Unit,
    onDismissProStatus: () -> Unit,
    onDebugForcePro: (Boolean) -> Unit,
) {
    AppListRow(
        icon = Icons.Outlined.WorkspacePremium,
        title = stringResource(R.string.more_pro),
        subtitle = when {
            !pro.paywallEnabled -> stringResource(R.string.more_pro_unlocked_free)
            pro.isPro -> stringResource(R.string.more_pro_active)
            else -> stringResource(R.string.more_pro_get)
        },
        onClick = onOpenPaywall,
    )
    if (!pro.isPro) {
    AppListDivider()
    AppListRow(
        icon = Icons.Outlined.Restore,
        title = stringResource(R.string.more_restore),
        subtitle = stringResource(R.string.more_restore_sub),
        onClick = onRestorePurchase,
        trailing = if (pro.status == ProStatus.BUSY) {
            { CircularProgressIndicator(modifier = Modifier.size(Size.standardIcon)) }
        } else {
            null
        },
    )
    }
    statusMessage(pro.status)?.let { message ->
        TextButton(onClick = onDismissProStatus, modifier = Modifier.fillMaxWidth()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
    }
    if (pro.showDebugToggle) {
        AppListDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.touchTarget)
                .toggleable(value = pro.debugForcePro, role = Role.Switch, onValueChange = onDebugForcePro)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            Text(
                stringResource(R.string.more_debug_force_pro),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = pro.debugForcePro, onCheckedChange = null)
        }
    }
}

@Composable
fun PolicyScreen(
    policyId: String,
    appVersion: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val titleRes = when (policyId) {
        "privacy" -> R.string.policy_privacy_title
        "terms" -> R.string.policy_terms_title
        else -> R.string.policy_about_title
    }
    val body = when (policyId) {
        "privacy" -> stringResource(R.string.policy_privacy_body)
        "terms" -> stringResource(R.string.policy_terms_body)
        else -> stringResource(R.string.policy_about_body, appVersion)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxl),
        )
    }
}

@Composable
private fun SettingsValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Surface(onClick = onClick, color = colors.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value.isNotBlank()) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
    }
}

private fun compactHours(hours: Float): String =
    if (hours % 1f == 0f) hours.toInt().toString() else hours.toString()

@Composable
private fun ProfileGroup(content: @Composable ColumnScope.() -> Unit) {
    AppListGroup(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    titleRes: Int,
    subtitleRes: Int,
    onClick: () -> Unit,
) {
    AppListRow(
        icon = icon,
        title = stringResource(titleRes),
        subtitle = stringResource(subtitleRes),
        onClick = onClick,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.sm, top = Spacing.sm),
    )
}

@Composable
private fun SettingsStatusScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (loading) CircularProgressIndicator()
                Text(text = message, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

/** Live vs backlog share of each day (§5.4), in steps of 5. */
@Composable
private fun BacklogSplitDialog(
    initial: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var live by remember { mutableIntStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_backlog_split)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.more_backlog_split_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.more_backlog_split_value, live, 100 - live),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                )
                Slider(
                    value = live.toFloat(),
                    onValueChange = { live = ((it / SPLIT_STEP).roundToInt() * SPLIT_STEP).coerceIn(0, 100) },
                    valueRange = 0f..100f,
                    steps = 100 / SPLIT_STEP - 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(live); onDismiss() }) { Text(stringResource(R.string.more_backlog_split_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.more_redo_confirm_no)) }
        },
    )
}

private const val SPLIT_STEP = 5
