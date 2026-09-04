package com.exam.assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppListDivider
import com.exam.assistant.core.design.AppListGroup
import com.exam.assistant.core.design.AppListRow
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Radius

@Composable
fun MoreScreen(
    planExamLabel: String?,
    daysLeft: Int?,
    examDateLabel: String?,
    weekdayHours: Float?,
    weekendHours: Float?,
    fixedBlockCount: Int,
    background: BackgroundAppearance,
    onOpenAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var morningReminder by remember { mutableStateOf(true) }
    var revisionReminder by remember { mutableStateOf(true) }
    var showRedoDialog by remember { mutableStateOf(false) }
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
        AccountCard(
            title = planExamLabel ?: stringResource(R.string.more_your_plan),
            subtitle = if (examDateLabel != null && weekdayHours != null) {
                stringResource(
                    R.string.more_plan_card_subtitle,
                    examDateLabel,
                    compactHours(weekdayHours),
                )
            } else {
                stringResource(R.string.more_no_plan)
            },
            onClick = onOpenSettings,
        )
        SectionLabel(stringResource(R.string.more_section_plan_short))
        ProfileGroup {
            SettingsValueRow(
                icon = Icons.Outlined.Event,
                title = stringResource(R.string.more_exam_date),
                value = examDateLabel.orEmpty(),
                onClick = { showRedoDialog = true },
            )
            AppListDivider()
            SettingsValueRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.more_hours_breaks),
                value = if (weekdayHours != null && weekendHours != null) {
                    stringResource(
                        R.string.more_hours_pair,
                        compactHours(weekdayHours),
                        compactHours(weekendHours),
                    )
                } else "",
                onClick = onOpenSettings,
            )
            AppListDivider()
            SettingsValueRow(
                icon = Icons.Outlined.EventBusy,
                title = stringResource(R.string.more_fixed_hours),
                value = pluralStringResource(R.plurals.more_blocks, fixedBlockCount, fixedBlockCount),
                onClick = { showRedoDialog = true },
            )
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
                onClick = onOpenAppearance,
            )
            AppListDivider()
            SettingsValueRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.more_revision_schedule),
                value = stringResource(R.string.more_revision_standard),
                onClick = onOpenSettings,
            )
            AppListDivider()
            SettingsValueRow(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.more_focus_shield_preview),
                value = stringResource(R.string.more_preview_settings),
                onClick = onOpenSettings,
            )
        }
        SectionLabel(stringResource(R.string.more_section_reminders))
        ProfileGroup {
            SettingsSwitchRow(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.more_morning_plan),
                checked = morningReminder,
                onCheckedChange = { morningReminder = it },
            )
            AppListDivider()
            SettingsSwitchRow(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.more_revision_due),
                checked = revisionReminder,
                onCheckedChange = { revisionReminder = it },
            )
        }
        SectionLabel(stringResource(R.string.more_section_about))
        ProfileGroup {
            ProfileRow(Icons.Outlined.Lock, R.string.more_privacy, R.string.more_privacy_sub) { onOpenPolicy("privacy") }
            AppListDivider()
            ProfileRow(Icons.Outlined.Info, R.string.more_terms, R.string.more_terms_sub) { onOpenPolicy("terms") }
            AppListDivider()
            ProfileRow(Icons.Outlined.Info, R.string.more_about, R.string.more_about_sub) { onOpenPolicy("about") }
        }
        Text(
            text = stringResource(R.string.more_footer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = Spacing.xl),
        )
    }
    if (showRedoDialog) {
        AlertDialog(
            onDismissRequest = { showRedoDialog = false },
            title = { Text(stringResource(R.string.more_redo_confirm_title)) },
            text = { Text(stringResource(R.string.more_redo_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRedoDialog = false
                        onRedoOnboarding()
                    },
                ) {
                    Text(stringResource(R.string.more_redo_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRedoDialog = false }) {
                    Text(stringResource(R.string.more_redo_confirm_no))
                }
            },
        )
    }
}

@Composable
fun PolicyScreen(
    policyId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val (titleRes, bodyRes) = when (policyId) {
        "privacy" -> R.string.policy_privacy_title to R.string.policy_privacy_body
        "terms" -> R.string.policy_terms_title to R.string.policy_terms_body
        else -> R.string.policy_about_title to R.string.policy_about_body
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
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxl),
        )
    }
}

@Composable
private fun AccountCard(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
        shape = RoundedCornerShape(Radius.extraLarge),
        color = colors.selectionContainer,
        contentColor = colors.onSelectionContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.padding(end = Spacing.md),
                shape = CircleShape,
                color = colors.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.more_zero_percent), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.more_covered), style = MaterialTheme.typography.labelSmall)
            }
        }
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

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(Size.standardIcon), tint = colors.textSecondary)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
