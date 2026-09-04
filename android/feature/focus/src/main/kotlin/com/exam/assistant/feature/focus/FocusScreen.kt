package com.exam.assistant.feature.focus

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
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
    attemptRepository: AttemptRepository,
    planRepository: PlanRepository,
    studySessionRepository: StudySessionRepository,
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
            attemptRepository,
            planRepository,
            studySessionRepository,
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
        onExtend = viewModel::extendFiveMinutes,
        onSetDuration = viewModel::setDuration,
        onSelectQueueItem = viewModel::selectQueueItem,
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
        onAppSearchQueryChange = focusLockViewModel::setAppSearchQuery,
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
    onExtend: () -> Unit,
    onSetDuration: (Int) -> Unit,
    onSelectQueueItem: (String) -> Unit,
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
    onAppSearchQueryChange: (String) -> Unit,
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

    if (state.status == FocusStatus.IDLE && !showingSetup) {
        FocusIdleScreen(
            state = state,
            lockState = lockState,
            onStart = onStart,
            onSelectQueueItem = onSelectQueueItem,
            onToggleFocusLockEnabled = onToggleFocusLockEnabled,
            onStartFocusLockSetup = onStartFocusLockSetup,
            modifier = modifier,
        )
        return
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
                onSearchQueryChange = onAppSearchQueryChange,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                listOf(25, 50, 90).forEach { minutes ->
                    Surface(
                        onClick = { onSetDuration(minutes) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (state.durationMinutes == minutes) colors.brandDeep else colors.surfaceControl,
                    ) {
                        Text(
                            text = stringResource(R.string.focus_length_minutes, minutes),
                            color = if (state.durationMinutes == minutes) colors.onBrand else colors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = Spacing.sm),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        } else if (lockState.display is FocusLockDisplayState.Active) {
            val blockedCount = (lockState.display as FocusLockDisplayState.Active).blockedCount
            Text(
                text = pluralStringResource(
                    R.plurals.focus_lock_status_line_active,
                    blockedCount,
                    blockedCount,
                ),
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
                    modifier = Modifier.size(Size.focusDial),
                    color = arcColor,
                    trackColor = colors.text.copy(alpha = 0.12f),
                    strokeWidth = Size.summaryTrack,
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
                        onClick = onExtend,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.MoreTime, contentDescription = null)
                        Text(stringResource(R.string.focus_add_five))
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
                    FilledTonalButton(
                        onClick = onRequestStop,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text(stringResource(R.string.focus_end))
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
private fun FocusIdleScreen(
    state: FocusUiState,
    lockState: FocusLockUiState,
    onStart: () -> Unit,
    onSelectQueueItem: (String) -> Unit,
    onToggleFocusLockEnabled: (Boolean) -> Unit,
    onStartFocusLockSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var browsing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shieldOn = lockState.display !is FocusLockDisplayState.Off
    val options = buildList {
        if (state.hasBlock) {
            add(
                FocusQueueItem(
                    id = "__current__",
                    title = state.blockTitle,
                    subtitle = state.blockSubtitle,
                    tag = state.blockTag ?: BlockTag.READ,
                    durationMinutes = state.durationMinutes,
                ),
            )
        }
        addAll(state.queue)
    }.filter { row ->
        query.isBlank() || row.title.contains(query, ignoreCase = true) ||
            row.subtitle.contains(query, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.tabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (browsing) {
                IconButton(onClick = { browsing = false }, modifier = Modifier.size(Size.timeField)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.focus_back))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.focus_search_topics)) },
                    shape = CircleShape,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.focus_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                )
                Surface(
                    onClick = {
                        if (shieldOn) onToggleFocusLockEnabled(false) else onStartFocusLockSetup()
                    },
                    shape = CircleShape,
                    color = if (shieldOn) colors.selectionContainer else colors.surface,
                    contentColor = if (shieldOn) colors.onSelectionContainer else colors.textSecondary,
                    border = if (shieldOn) null else BorderStroke(Stroke.hairline, colors.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .height(Size.compactControl)
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = if (shieldOn) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(Size.smallIcon),
                        )
                        Text(
                            text = stringResource(
                                if (shieldOn) R.string.focus_shield_preview else R.string.focus_shield_off,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                IconButton(
                    onClick = { query = ""; browsing = true },
                    modifier = Modifier.size(Size.timeField),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.focus_browse_topics))
                }
            }
        }

        if (browsing) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(options, key = { it.id }) { row ->
                    FocusQueueRow(
                        item = row,
                        onClick = {
                            if (row.id != "__current__") onSelectQueueItem(row.id)
                            browsing = false
                        },
                    )
                }
                if (options.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.focus_no_topics_match, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(vertical = Spacing.xxl),
                        )
                    }
                }
            }
            return@Column
        }

        Surface(
            onClick = if (state.hasBlock) onStart else ({ browsing = true }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            shape = RoundedCornerShape(Radius.extraLarge),
            color = if (state.hasBlock) colors.selectionContainer else colors.surfaceContainerHigh,
            contentColor = if (state.hasBlock) colors.onSelectionContainer else colors.text,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (state.hasBlock) {
                    Text(stringResource(R.string.focus_up_next), style = MaterialTheme.typography.labelLarge)
                    Text(state.blockTitle, style = MaterialTheme.typography.headlineSmall)
                    if (state.blockSubtitle.isNotBlank()) {
                        Text(state.blockSubtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(Size.selectionIndicator),
                        )
                        Text(
                            stringResource(R.string.focus_start_minutes, state.durationMinutes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Text(stringResource(R.string.focus_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Row(
                        modifier = Modifier.padding(top = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(Size.selectionIndicator),
                        )
                        Text(stringResource(R.string.focus_pick_topic), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        if (state.queue.isNotEmpty()) {
            val left = state.queue.count { !it.completed } + if (state.hasBlock) 1 else 0
            Text(
                text = pluralStringResource(R.plurals.focus_left_today, left, left),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.xs),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.queue, key = { it.id }) { row ->
                FocusQueueRow(item = row, onClick = { onSelectQueueItem(row.id) })
            }
        }
    }
}

@Composable
private fun FocusQueueRow(item: FocusQueueItem, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(onClick = onClick, color = colors.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.ctaHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Size.focusTagVertical),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.completed) colors.textSecondary else colors.text,
                )
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            if (item.completed) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.focus_done),
                    tint = colors.primary,
                )
            } else {
                TagChip(item.tag)
            }
        }
    }
    HorizontalDivider(color = colors.outlineVariant, thickness = Stroke.hairline)
}

@Composable
private fun TagChip(tag: BlockTag) {
    val colors = AppTheme.colors
    val background = when (tag) {
        BlockTag.READ -> colors.selectionContainer
        BlockTag.PRACTICE -> colors.primary
        BlockTag.REVISE -> colors.surface
    }
    val foreground = when (tag) {
        BlockTag.READ -> colors.onSelectionContainer
        BlockTag.PRACTICE -> colors.onPrimary
        BlockTag.REVISE -> colors.primary
    }
    Surface(
        color = background,
        shape = CircleShape,
        border = if (tag == BlockTag.REVISE) BorderStroke(Stroke.hairline, colors.outline) else null,
    ) {
        Text(
            text = stringResource(tag.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            modifier = Modifier.padding(
                horizontal = Size.focusTagHorizontal,
                vertical = Size.focusTagVertical,
            ),
        )
    }
}

@StringRes
private fun BlockTag.labelRes(): Int = when (this) {
    BlockTag.READ -> R.string.focus_tag_learn
    BlockTag.PRACTICE -> R.string.focus_tag_practice
    BlockTag.REVISE -> R.string.focus_tag_revise
}
