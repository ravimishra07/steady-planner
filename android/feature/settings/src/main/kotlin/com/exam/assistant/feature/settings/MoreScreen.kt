package com.exam.assistant.feature.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.exam.assistant.core.design.AppListGroup
import com.exam.assistant.core.design.AppShapes
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.rememberHaptics
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Everything Settings can do; one holder keeps the screen signature readable. */
@Immutable
class SettingsActions(
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onWeekdayHours: (Float) -> Unit,
    val onWeekendHours: (Float) -> Unit,
    val onFinishBy: (LocalDate) -> Unit,
    val onDismissSaveResult: () -> Unit,
    val onFocusLength: (Int) -> Unit,
    val onReminderToggle: (Boolean) -> Unit,
    val onReminderTime: (Int) -> Unit,
    val onSessionSounds: (Boolean) -> Unit,
    val onWeekStart: (DayOfWeek) -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenPaywall: () -> Unit,
    val onDebugForcePro: (Boolean) -> Unit,
    val onOpenAppearance: () -> Unit,
    val onEraseAll: () -> Unit,
    val onOpenPolicy: (String) -> Unit,
)

/**
 * Settings: a Pro banner, then General, Study plan, Study experience and Account & data.
 * Every value is changed in place and saved at once.
 */
@Composable
fun MoreScreen(
    detail: SettingsDetailUiState,
    pro: ProRowState,
    background: BackgroundAppearance,
    appVersion: String,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var showGoalDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    // A local read of a few milliseconds: show nothing rather than a spinner for the first one.
    if (detail.loading) return
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
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()) }
    val yearDateFormat = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()) }
    val reminderLabel = if (detail.reminderEnabled) {
        stringResource(R.string.settings_reminder_daily_at, formatMinuteOfDay(detail.reminderMinuteOfDay, DateFormat.is24HourFormat(context)))
    } else {
        stringResource(R.string.settings_off)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        IconButton(onClick = actions.onBack, modifier = Modifier.padding(top = Spacing.sm).size(Size.touchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back), tint = colors.text)
        }
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl).semantics { heading() },
        )

        ProBanner(pro = pro, onClick = actions.onOpenPaywall)
        if (pro.showDebugToggle) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Size.touchTarget)
                    .toggleable(value = pro.debugForcePro, role = Role.Switch, onValueChange = actions.onDebugForcePro)
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.more_debug_force_pro), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Switch(checked = pro.debugForcePro, onCheckedChange = null)
            }
        }

        SectionLabel(stringResource(R.string.settings_section_general))
        SettingsGroup {
            NavRow(Icons.Outlined.Notifications, stringResource(R.string.settings_notifications), onClick = actions.onOpenNotifications)
            RowDivider()
            NavRow(
                icon = Icons.Outlined.LightMode,
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
            RowDivider()
            MenuRow(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.settings_start_of_week),
                options = WEEK_START_CHOICES,
                selected = detail.weekStart,
                label = { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                onSelect = actions.onWeekStart,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_study_plan))
        SettingsGroup {
            NavRow(
                icon = Icons.Outlined.BarChart,
                title = stringResource(R.string.settings_daily_goal),
                subtitle = stringResource(R.string.settings_daily_goal_value, compactHours(detail.weekdayHours), compactHours(detail.weekendHours)),
                onClick = { showGoalDialog = true },
            )
            RowDivider()
            NavRow(
                icon = Icons.Outlined.Flag,
                title = stringResource(R.string.settings_target_date),
                subtitle = detail.finishBy?.let { date ->
                    date.format(if (date.year == LocalDate.now().year) dateFormat else yearDateFormat)
                },
                onClick = { showDatePicker = true },
            )
            RowDivider()
            NavRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.settings_study_reminders),
                subtitle = reminderLabel,
                onClick = { showReminderDialog = true },
            )
        }
        if (detail.saveFailed) {
            TextButton(onClick = actions.onDismissSaveResult, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save_failed), color = colors.danger)
            }
        }

        SectionLabel(stringResource(R.string.settings_section_experience))
        SettingsGroup {
            SwitchRow(Icons.Outlined.MusicNote, stringResource(R.string.settings_session_sounds), detail.focusEndAlert, actions.onSessionSounds)
            RowDivider()
            MenuRow(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.settings_focus_timer),
                options = (FOCUS_CHOICES + detail.focusDurationMinutes).distinct().sorted(),
                selected = detail.focusDurationMinutes,
                label = { stringResource(R.string.settings_focus_minutes, it) },
                onSelect = actions.onFocusLength,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_account))
        SettingsGroup {
            NavRow(Icons.Outlined.Description, stringResource(R.string.settings_terms), onClick = { actions.onOpenPolicy("terms") })
            RowDivider()
            NavRow(Icons.Outlined.Shield, stringResource(R.string.settings_privacy_policy), onClick = { actions.onOpenPolicy("privacy") })
            RowDivider()
            NavRow(
                icon = Icons.Outlined.Delete,
                title = stringResource(R.string.settings_reset_data),
                tint = colors.danger,
                busy = resetting,
                onClick = { showResetDialog = true },
            )
        }

        Text(
            text = stringResource(R.string.more_about_version, appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.xl),
        )
    }

    if (showGoalDialog) {
        DailyGoalDialog(
            weekdayHours = detail.weekdayHours,
            weekendHours = detail.weekendHours,
            onWeekday = actions.onWeekdayHours,
            onWeekend = actions.onWeekendHours,
            onDismiss = { showGoalDialog = false },
        )
    }
    if (showReminderDialog) {
        ReminderDialog(
            enabled = detail.reminderEnabled,
            minuteOfDay = detail.reminderMinuteOfDay,
            permissionDenied = detail.reminderPermissionDenied,
            onToggle = actions.onReminderToggle,
            onTime = actions.onReminderTime,
            onDismiss = { showReminderDialog = false },
        )
    }
    if (showDatePicker) {
        TargetDatePicker(date = detail.finishBy, onPick = actions.onFinishBy, onDismiss = { showDatePicker = false })
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        resetting = true
                        actions.onEraseAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.danger),
                ) { Text(stringResource(R.string.settings_reset_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

private const val MAX_WEEKDAY_HOURS = 14
private const val MAX_WEEKEND_HOURS = 16
private val FOCUS_CHOICES = listOf(25, 50, 90)
private val WEEK_START_CHOICES = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)

private fun hourChoices(current: Float, maximum: Int): List<Float> =
    ((1..maximum).map { it.toFloat() } + current).distinct().sorted()

private fun compactHours(hours: Float): String =
    if (hours % 1f == 0f) hours.toInt().toString() else hours.toString()

private fun formatMinuteOfDay(minuteOfDay: Int, is24Hour: Boolean): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()))

/** The Pro entry at the top: the paywall for free users, a quiet "unlocked" for Pro. */
@Composable
private fun ProBanner(pro: ProRowState, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val unlocked = pro.isPro || !pro.paywallEnabled
    Surface(
        onClick = {
            haptics.select()
            onClick()
        },
        enabled = !unlocked,
        shape = AppShapes.card,
        color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(Color.Transparent, colors.primary.copy(alpha = BANNER_GLOW_ALPHA))),
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Size.standardIcon))
            Column(
                modifier = Modifier.weight(1f).padding(start = ROW_TEXT_GAP, end = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    stringResource(if (unlocked) R.string.settings_pro_active else R.string.settings_get_pro),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(if (unlocked) R.string.settings_pro_active_sub else R.string.settings_get_pro_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                )
            }
            if (!unlocked) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onPrimaryContainer)
            }
        }
    }
}

private const val BANNER_GLOW_ALPHA = 0.18f

/** Icon-to-text gap in every Settings row and the divider inset that follows it. */
private val ROW_TEXT_GAP = Spacing.xl

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(start = Spacing.sm, top = Spacing.xxl, bottom = Spacing.md).semantics { heading() },
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AppListGroup(modifier = Modifier.fillMaxWidth()) { Column(content = content) }
}

/** Inset to the text column, as in the grouped list. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.lg + Size.standardIcon + ROW_TEXT_GAP, end = Spacing.lg),
        color = AppTheme.colors.borderSubtle,
    )
}

@Composable
private fun RowContent(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = AppTheme.colors.text,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.listRow)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.standardIcon))
        Column(
            modifier = Modifier.weight(1f).padding(start = ROW_TEXT_GAP, end = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppTheme.colors.textSecondary)
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    tint: Color = AppTheme.colors.text,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.select()
            onClick()
        },
        enabled = !busy,
        color = Color.Transparent,
    ) {
        RowContent(icon, title, subtitle, tint) {
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            if (busy) CircularProgressIndicator(modifier = Modifier.size(Size.standardIcon)) else Chevron()
        }
    }
}

/** A row whose current value opens a menu of choices under it. */
@Composable
private fun <T> MenuRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, color = Color.Transparent) {
        RowContent(icon, title) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(label(selected), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                    Chevron()
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
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = {
                haptics.select()
                onChange(it)
            },
        ),
    ) {
        RowContent(icon, title) { Switch(checked = checked, onCheckedChange = null) }
    }
}

/** A value with a dropdown, for dialogs. */
@Composable
private fun <T> DropdownField(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(label(selected))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyGoalDialog(
    weekdayHours: Float,
    weekendHours: Float,
    onWeekday: (Float) -> Unit,
    onWeekend: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_goal)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_daily_goal_body), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                DropdownField(
                    title = stringResource(R.string.more_weekday_hours),
                    options = hourChoices(weekdayHours, MAX_WEEKDAY_HOURS),
                    selected = weekdayHours,
                    label = { stringResource(R.string.settings_hours_value, compactHours(it)) },
                    onSelect = onWeekday,
                )
                DropdownField(
                    title = stringResource(R.string.more_weekend_hours),
                    options = hourChoices(weekendHours, MAX_WEEKEND_HOURS),
                    selected = weekendHours,
                    label = { stringResource(R.string.settings_hours_value, compactHours(it)) },
                    onSelect = onWeekend,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(
    enabled: Boolean,
    minuteOfDay: Int,
    permissionDenied: Boolean,
    onToggle: (Boolean) -> Unit,
    onTime: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    var pickingTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_study_reminders)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_reminder_daily_detail), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.touchTarget)
                        .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_reminder_daily), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = null)
                }
                if (enabled) {
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_reminder_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickingTime = true }) { Text(formatMinuteOfDay(minuteOfDay, is24Hour)) }
                    }
                }
                if (permissionDenied) {
                    Text(stringResource(R.string.settings_reminder_denied), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.danger)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
    if (pickingTime) {
        val pickerState = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(pickerState.hour * 60 + pickerState.minute)
                    pickingTime = false
                }) { Text(stringResource(R.string.settings_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDatePicker(date: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneOffset.UTC
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(zone).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate().isBefore(LocalDate.now())
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.settings_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    ) { DatePicker(state = pickerState, showModeToggle = false) }
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
private fun SettingsStatusScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
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
                Text(text = message, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

