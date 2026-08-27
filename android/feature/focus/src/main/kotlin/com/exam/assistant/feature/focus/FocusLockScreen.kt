package com.exam.assistant.feature.focus

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.exam.assistant.core.data.InstalledAppInfo
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.FOCUS_LOCK_SUGGESTED_PACKAGES
import com.exam.assistant.domain.FocusLockDisplayState

/** Re-checks real system permission state whenever the screen resumes — covers returning from Settings. */
@Composable
private fun ResumeEffect(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun FocusLockCard(
    state: FocusLockUiState,
    onToggleEnabled: (Boolean) -> Unit,
    onStartSetup: () -> Unit,
    onOpenManageApps: () -> Unit,
    onFixSetup: () -> Unit,
) {
    val colors = AppTheme.colors
    ResumeEffect(onResume = {})

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.focus_lock_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(R.string.focus_lock_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = state.display != FocusLockDisplayState.Off,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.brandDeep),
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            when (val display = state.display) {
                is FocusLockDisplayState.Off -> {
                    StatusLine(label = stringResource(R.string.focus_lock_status_off), color = colors.textMuted)
                    Text(
                        text = stringResource(R.string.focus_lock_off_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                }
                is FocusLockDisplayState.NeedsSetup -> {
                    StatusLine(label = stringResource(R.string.focus_lock_status_needs_setup), color = colors.warning)
                    Text(
                        text = stringResource(R.string.focus_lock_needs_setup_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                    Button(
                        onClick = onStartSetup,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
                        shape = RoundedCornerShape(Radius.lg),
                    ) { Text(stringResource(R.string.focus_lock_choose_distractions)) }
                }
                is FocusLockDisplayState.NeedsAttention -> {
                    StatusLine(label = stringResource(R.string.focus_lock_status_needs_attention), color = colors.warning)
                    Text(
                        text = stringResource(R.string.focus_lock_needs_attention_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
                    )
                    Button(
                        onClick = onFixSetup,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
                        shape = RoundedCornerShape(Radius.lg),
                    ) { Text(stringResource(R.string.focus_lock_fix_setup)) }
                }
                is FocusLockDisplayState.Ready -> {
                    StatusLine(label = stringResource(R.string.focus_lock_status_ready), color = colors.success)
                    Text(
                        text = stringResource(R.string.focus_lock_apps_selected, display.blockedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xs, bottom = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.focus_lock_auto_activates),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    )
                    TextButton(onClick = onOpenManageApps) { Text(stringResource(R.string.focus_lock_manage_apps)) }
                }
                is FocusLockDisplayState.Active -> {
                    StatusLine(label = stringResource(R.string.focus_lock_status_active), color = colors.brandSoft)
                    val minutes = display.session.remainingSec / 60
                    Text(
                        text = stringResource(R.string.focus_lock_min_remaining, minutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.focus_lock_apps_blocked_active, display.blockedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun FocusLockSetupFlow(
    state: FocusLockUiState,
    onDismiss: () -> Unit,
    onAdvanceToPermissions: () -> Unit,
    onAdvanceToAppPicker: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onSaveAndEnable: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.setupStep == FocusLockSetupStep.None) return
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surfaceCard,
        modifier = modifier.fillMaxWidth(),
    ) {
        when (state.setupStep) {
            FocusLockSetupStep.Explain -> ExplainStep(onContinue = onAdvanceToPermissions, onCancel = onDismiss)
            FocusLockSetupStep.Permissions -> PermissionsStep(
                state = state,
                onRefresh = onRefreshCapabilities,
                onContinue = onAdvanceToAppPicker,
                onCancel = onDismiss,
            )
            FocusLockSetupStep.AppPicker -> AppPickerStep(
                state = state,
                onToggleApp = onToggleApp,
                onSelectAll = onSelectAll,
                onClearAll = onClearAll,
                onSave = onSaveAndEnable,
                onCancel = onDismiss,
                onSearchQueryChange = onSearchQueryChange,
            )
            FocusLockSetupStep.None -> Unit
        }
    }
}

@Composable
private fun ExplainStep(onContinue: () -> Unit, onCancel: () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.xl)) {
        Text(
            stringResource(R.string.focus_lock_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.focus_lock_setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xl),
        )
        SetupStepRow(number = 1, label = stringResource(R.string.focus_lock_setup_step_1))
        SetupStepRow(number = 2, label = stringResource(R.string.focus_lock_setup_step_2))
        SetupStepRow(number = 3, label = stringResource(R.string.focus_lock_setup_step_3))
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
        ) { Text(stringResource(R.string.focus_lock_continue)) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.focus_lock_not_now)) }
    }
}

@Composable
private fun SetupStepRow(number: Int, label: String) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.xs)) {
        Box(
            modifier = Modifier.size(28.dp).background(colors.brandContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "$number", style = MaterialTheme.typography.labelLarge, color = colors.brandSoft)
        }
        Spacer(Modifier.width(Spacing.md))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = colors.text)
    }
}

@Composable
private fun PermissionsStep(
    state: FocusLockUiState,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    ResumeEffect(onResume = onRefresh)

    val notificationsPermissionAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.xl)) {
        Text(
            stringResource(R.string.focus_lock_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Spacer(Modifier.height(Spacing.lg))

        PermissionRow(
            label = stringResource(R.string.focus_lock_permission_usage_label),
            description = stringResource(R.string.focus_lock_permission_usage_description),
            granted = state.usageAccessGranted,
            onGrant = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        )
        PermissionRow(
            label = stringResource(R.string.focus_lock_permission_overlay_label),
            description = stringResource(R.string.focus_lock_permission_overlay_description),
            granted = state.overlayGranted,
            onGrant = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            },
        )
        if (notificationsPermissionAvailable) {
            PermissionRow(
                label = stringResource(R.string.focus_lock_permission_notifications_label),
                description = stringResource(R.string.focus_lock_permission_notifications_description),
                granted = state.notificationsGranted,
                onGrant = { notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            enabled = state.requiredPermissionsGranted,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
        ) { Text(stringResource(R.string.focus_lock_continue)) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.focus_lock_not_now)) }
    }
}

@Composable
private fun PermissionRow(label: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.text, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = 2.dp))
        }
        if (granted) {
            Box(
                modifier = Modifier.size(32.dp).background(colors.successContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.successStrong, modifier = Modifier.size(18.dp))
            }
        } else {
            TextButton(onClick = onGrant) { Text(stringResource(R.string.focus_lock_grant)) }
        }
    }
}

@Composable
private fun AppPickerStep(
    state: FocusLockUiState,
    onToggleApp: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val query = state.appSearchQuery.trim()
    val matches = if (query.isBlank()) {
        state.installedApps
    } else {
        state.installedApps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val suggested = matches.filter { it.packageName in FOCUS_LOCK_SUGGESTED_PACKAGES }
        .sortedBy { FOCUS_LOCK_SUGGESTED_PACKAGES.indexOf(it.packageName) }
    val suggestedKeys = suggested.map { it.packageName }.toSet()
    val others = matches.filterNot { it.packageName in suggestedKeys }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.lg)) {
        Text("Choose distractions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.text)
        Text(
            text = "These apps will be unavailable while you're studying.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
        )
        OutlinedTextField(
            value = state.appSearchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search apps…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted) },
            shape = RoundedCornerShape(Radius.lg),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandDeep,
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(top = Spacing.md),
        ) {
            TextButton(onClick = onSelectAll) { Text("Select all") }
            TextButton(onClick = onClearAll) { Text("Clear") }
        }
        if (state.appsLoading) {
            Text("Loading apps…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.padding(top = Spacing.lg))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (suggested.isNotEmpty()) {
                    item { SectionLabel("Suggested") }
                    items(suggested, key = { it.packageName }) { app ->
                        AppRow(app = app, checked = app.packageName in state.selectedPackages, onToggle = { onToggleApp(app.packageName) })
                    }
                }
                if (others.isNotEmpty()) {
                    item { SectionLabel(if (suggested.isNotEmpty()) "All apps" else "") }
                    items(others, key = { it.packageName }) { app ->
                        AppRow(app = app, checked = app.packageName in state.selectedPackages, onToggle = { onToggleApp(app.packageName) })
                    }
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            text = "No apps match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = Spacing.lg),
                        )
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            enabled = state.selectedPackages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            shape = RoundedCornerShape(Radius.lg),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
        ) { Text("Save & Enable") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    if (text.isBlank()) return
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun AppRow(app: InstalledAppInfo, checked: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = remember(app.packageName) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(modifier = Modifier.size(36.dp).background(colors.surfaceControl, CircleShape))
        }
        Spacer(Modifier.width(Spacing.md))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = colors.brandDeep),
        )
    }
}
