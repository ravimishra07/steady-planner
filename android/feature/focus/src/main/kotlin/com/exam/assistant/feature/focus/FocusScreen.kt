package com.exam.assistant.feature.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.FocusLockCapabilityChecker
import com.exam.assistant.core.data.FocusLockStore
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.InstalledAppProvider
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusLockDisplayState
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.formatFocusClock
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope

@Composable
fun FocusRoute(
    focusStore: FocusStore,
    planStore: PlanStore,
    settingsStore: SettingsStore,
    studySessionStore: StudySessionStore,
    syllabusRepository: SyllabusRepository,
    syllabusStore: SyllabusStore,
    focusLockStore: FocusLockStore,
    focusLockCapabilityChecker: FocusLockCapabilityChecker,
    installedAppProvider: InstalledAppProvider,
    appScope: CoroutineScope,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusLockStart: () -> Unit = {},
    onFocusLockStop: () -> Unit = {},
    viewModel: FocusViewModel = viewModel(
        factory = FocusViewModel.Factory(
            focusStore,
            planStore,
            settingsStore,
            studySessionStore,
            syllabusRepository,
            syllabusStore,
            appScope,
            onFocusLockStart,
            onFocusLockStop,
        ),
    ),
    focusLockViewModel: FocusLockViewModel = viewModel(
        factory = FocusLockViewModel.Factory(
            focusLockStore,
            focusLockCapabilityChecker,
            installedAppProvider,
            focusStore,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val lockState by focusLockViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.status) { focusLockViewModel.refresh() }

    FocusScreen(
        state = state,
        remainingSeconds = remaining,
        lockState = lockState,
        onClose = onClose,
        onStart = viewModel::startSession,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onRequestStop = viewModel::requestStop,
        onConfirmStop = viewModel::confirmStop,
        onDismissStop = viewModel::dismissStopDialog,
        onStartAnother = viewModel::startAnother,
        onBackToday = onClose,
        onToggleFocusLockEnabled = focusLockViewModel::setEnabled,
        onStartFocusLockSetup = focusLockViewModel::startSetup,
        onOpenManageApps = focusLockViewModel::openManageApps,
        onDismissFocusLockSetup = focusLockViewModel::dismissSetup,
        onAdvanceToPermissions = focusLockViewModel::advanceToPermissions,
        onAdvanceToAppPicker = focusLockViewModel::advanceToAppPicker,
        onRefreshCapabilities = focusLockViewModel::refreshCapabilities,
        onToggleApp = focusLockViewModel::toggleAppSelected,
        onSelectAllApps = focusLockViewModel::selectAllApps,
        onClearAppSelection = focusLockViewModel::clearAppSelection,
        onSaveAndEnableFocusLock = focusLockViewModel::saveAndEnable,
        modifier = modifier,
    )
}

@Composable
fun FocusScreen(
    state: FocusUiState,
    remainingSeconds: Int,
    lockState: FocusLockUiState,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRequestStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onDismissStop: () -> Unit,
    onStartAnother: () -> Unit,
    onBackToday: () -> Unit,
    onToggleFocusLockEnabled: (Boolean) -> Unit,
    onStartFocusLockSetup: () -> Unit,
    onOpenManageApps: () -> Unit,
    onDismissFocusLockSetup: () -> Unit,
    onAdvanceToPermissions: () -> Unit,
    onAdvanceToAppPicker: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAllApps: () -> Unit,
    onClearAppSelection: () -> Unit,
    onSaveAndEnableFocusLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val showingSetup = state.status == FocusStatus.IDLE && lockState.setupStep != FocusLockSetupStep.None
    if (state.showStopDialog) {
        AlertDialog(
            onDismissRequest = onDismissStop,
            title = { Text(stringResource(R.string.focus_stop)) },
            text = { Text(stringResource(R.string.focus_stop_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirmStop) {
                    Text(stringResource(R.string.focus_stop_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissStop) {
                    Text(stringResource(R.string.focus_stop_confirm_no))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm, bottom = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (state.status) {
                    FocusStatus.RUNNING -> stringResource(R.string.focus_running)
                    FocusStatus.PAUSED -> stringResource(R.string.focus_paused)
                    FocusStatus.DONE -> stringResource(R.string.focus_complete)
                    FocusStatus.IDLE -> stringResource(R.string.focus_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(Size.touchTarget)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.focus_close))
            }
        }

        if (showingSetup) {
            FocusLockSetupFlow(
                state = lockState,
                onDismiss = onDismissFocusLockSetup,
                onAdvanceToPermissions = onAdvanceToPermissions,
                onAdvanceToAppPicker = onAdvanceToAppPicker,
                onRefreshCapabilities = onRefreshCapabilities,
                onToggleApp = onToggleApp,
                onSelectAll = onSelectAllApps,
                onClearAll = onClearAppSelection,
                onSaveAndEnable = onSaveAndEnableFocusLock,
                modifier = Modifier.weight(1f).padding(bottom = Spacing.lg),
            )
            return@Column
        }

        if (state.status == FocusStatus.IDLE) {
            FocusLockCard(
                state = lockState,
                onToggleEnabled = onToggleFocusLockEnabled,
                onStartSetup = onStartFocusLockSetup,
                onOpenManageApps = onOpenManageApps,
                onFixSetup = onStartFocusLockSetup,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        } else if (lockState.display is FocusLockDisplayState.Active) {
            Text(
                text = "Focus Lock · Active · ${(lockState.display as FocusLockDisplayState.Active).blockedCount} apps blocked",
                style = MaterialTheme.typography.labelMedium,
                color = colors.brandSoft,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            if (state.hasBlock) {
                state.blockTag?.let { tag -> TagChip(tag = tag) }
                Text(
                    text = state.blockTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                if (state.blockSubtitle.isNotBlank()) {
                    Text(
                        text = state.blockSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.focus_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.focus_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val progress = when (state.status) {
                FocusStatus.IDLE -> 0f
                FocusStatus.DONE -> 1f
                else -> {
                    val total = (state.durationMinutes * 60).coerceAtLeast(1)
                    ((total - remainingSeconds).toFloat() / total).coerceIn(0f, 1f)
                }
            }
            val arcColor = when (state.status) {
                FocusStatus.PAUSED -> colors.warning
                FocusStatus.DONE -> colors.success
                else -> colors.brandSoft
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(220.dp),
                    color = arcColor,
                    trackColor = colors.text.copy(alpha = 0.12f),
                    strokeWidth = 10.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.status == FocusStatus.DONE) {
                            stringResource(R.string.focus_done)
                        } else {
                            formatFocusClock(remainingSeconds)
                        },
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Text(
                        text = if (state.status == FocusStatus.DONE) {
                            stringResource(R.string.focus_logged_minutes, state.durationMinutes)
                        } else {
                            stringResource(R.string.focus_of_minutes, state.durationMinutes)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }


        when (state.status) {
            FocusStatus.RUNNING, FocusStatus.PAUSED -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilledTonalButton(
                        onClick = onRequestStop,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.focus_stop))
                    }
                    Button(
                        onClick = if (state.status == FocusStatus.RUNNING) onPause else onResume,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandDeep,
                            contentColor = colors.onBrand,
                        ),
                    ) {
                        Icon(
                            imageVector = if (state.status == FocusStatus.RUNNING) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (state.status == FocusStatus.RUNNING) {
                                stringResource(R.string.focus_pause)
                            } else {
                                stringResource(R.string.focus_resume)
                            },
                        )
                    }
                }
            }
            FocusStatus.DONE -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        onClick = onStartAnother,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandDeep,
                            contentColor = colors.onBrand,
                        ),
                    ) {
                        Text(stringResource(R.string.focus_start_another))
                    }
                    TextButton(
                        onClick = onBackToday,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.focus_back_today), textAlign = TextAlign.Center)
                    }
                }
            }
            FocusStatus.IDLE -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.lg),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandDeep,
                        contentColor = colors.onBrand,
                    ),
                ) {
                    Text(stringResource(R.string.focus_start_session, state.durationMinutes))
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: BlockTag) {
    val colors = AppTheme.colors
    val background = when (tag) {
        BlockTag.READ -> colors.infoTint
        BlockTag.PRACTICE -> colors.successContainer
        BlockTag.REVISE -> colors.warningTint
    }
    val foreground = when (tag) {
        BlockTag.READ -> colors.info
        BlockTag.PRACTICE -> colors.success
        BlockTag.REVISE -> colors.warning
    }
    Surface(color = background, shape = RoundedCornerShape(Radius.sm)) {
        Text(
            text = stringResource(tag.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@StringRes
private fun BlockTag.labelRes(): Int = when (this) {
    BlockTag.READ -> R.string.focus_tag_read
    BlockTag.PRACTICE -> R.string.focus_tag_practice
    BlockTag.REVISE -> R.string.focus_tag_revise
}
