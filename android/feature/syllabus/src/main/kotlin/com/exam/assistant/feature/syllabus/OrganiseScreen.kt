package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppCardTone
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.PlanningOrder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class OrganiseView { PLAN, SYLLABUS }

@Composable
fun OrganiseRoute(
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    planRepository: PlanRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    rollingPlanRepository: RollingPlanRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrganiseViewModel = viewModel(
        factory = OrganiseViewModel.Factory(
            examPackRepository, attemptRepository, targetSyllabusRepository, planRepository,
            studyPreferenceRepository, rollingPlanRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    OrganiseScreen(
        state = state,
        onBack = onBack,
        onSelectSubject = viewModel::selectSubject,
        onToggleIncluded = viewModel::toggleIncluded,
        onAddChapter = viewModel::addChapter,
        onRemoveChapter = viewModel::removeChapter,
        onOrder = viewModel::setOrder,
        onDiscard = viewModel::discard,
        onApply = viewModel::apply,
        modifier = modifier,
    )
}

@Composable
fun OrganiseScreen(
    state: OrganiseUiState,
    onBack: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onToggleIncluded: (String) -> Unit,
    onAddChapter: (String, Float, String) -> Unit,
    onRemoveChapter: (String) -> Unit,
    onOrder: (PlanningOrder) -> Unit,
    onDiscard: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by remember { mutableStateOf(OrganiseView.PLAN) }
    var addOpen by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newHours by remember { mutableFloatStateOf(2f) }
    val colors = AppTheme.colors
    val defaultSubjectName = stringResource(R.string.organise_my_syllabus)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().height(Size.tabBarHeight).padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.organise_back))
                }
                Text(stringResource(R.string.organise_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (state.dirty) {
                    val changes = state.exactChangeCount
                    Text(
                        pluralStringResource(R.plurals.organise_changes, changes, changes),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = Spacing.md),
                    )
                }
            }
        },
        bottomBar = {
            if (state.dirty) {
                Surface(color = colors.surfaceCard, shadowElevation = com.exam.assistant.core.design.Elevation.floatingShadow) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TextButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.organise_discard)) }
                        Button(
                            onClick = { previewOpen = true },
                            modifier = Modifier.weight(1.5f).height(Size.touchTarget),
                            shape = RoundedCornerShape(Radius.pill),
                        ) { Text(stringResource(R.string.organise_preview)) }
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SegmentedChoice(stringResource(R.string.organise_plan), view == OrganiseView.PLAN, { view = OrganiseView.PLAN }, Modifier.weight(1f))
                    SegmentedChoice(stringResource(R.string.organise_syllabus), view == OrganiseView.SYLLABUS, { view = OrganiseView.SYLLABUS }, Modifier.weight(1f))
                }
                if (view == OrganiseView.PLAN) {
                    PlanPreview(state)
                } else {
                    SyllabusDraft(
                        state = state,
                        order = state.planningOrder,
                        onOrder = onOrder,
                        onSelectSubject = onSelectSubject,
                        onToggleIncluded = onToggleIncluded,
                        onAdd = { addOpen = true },
                        onRemove = onRemoveChapter,
                    )
                }
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text(stringResource(R.string.organise_add_chapter)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.organise_chapter_name)) },
                        singleLine = true,
                    )
                    AppValueStepper(
                        label = stringResource(R.string.organise_estimated_hours),
                        value = stringResource(R.string.organise_hours_short, newHours),
                        decreaseContentDescription = stringResource(R.string.organise_decrease_hours),
                        increaseContentDescription = stringResource(R.string.organise_increase_hours),
                        onDecrease = { newHours = (newHours - .5f).coerceAtLeast(.5f) },
                        onIncrease = { newHours = (newHours + .5f).coerceAtMost(20f) },
                        decreaseEnabled = newHours > .5f,
                        increaseEnabled = newHours < 20f,
                    )
                    Text(stringResource(R.string.organise_hours_hint), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        onAddChapter(newTitle, newHours, defaultSubjectName)
                        newTitle = ""
                        newHours = 2f
                        addOpen = false
                    },
                ) { Text(stringResource(R.string.organise_add)) }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text(stringResource(R.string.organise_cancel)) } },
        )
    }

    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text(stringResource(R.string.organise_what_changes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    DeltaRow(stringResource(R.string.organise_chapters_in_play), state.beforeIncludedCount.toString(), state.includedCount.toString())
                    DeltaRow(stringResource(R.string.organise_hours_left), "—", stringResource(R.string.organise_hours_short, state.totalMinutes / 60f))
                    if (state.saveFailed) Text(stringResource(R.string.organise_save_failed), color = colors.error)
                }
            },
            confirmButton = {
                Button(onClick = onApply, enabled = !state.saving) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(Spacing.xl), strokeWidth = Stroke.selected)
                    else Text(stringResource(R.string.organise_apply))
                }
            },
            dismissButton = { TextButton(onClick = { previewOpen = false }) { Text(stringResource(R.string.organise_keep_editing)) } },
        )
    }
}

@Composable
private fun PlanPreview(state: OrganiseUiState) {
    val colors = AppTheme.colors
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Text(stringResource(R.string.organise_today), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Text(
                stringResource(R.string.organise_hours_planned, (state.week.firstOrNull()?.plannedMinutes ?: 0) / 60f),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        item {
            AppCard(tone = AppCardTone.Raised, modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.todaySessions.forEachIndexed { index, session ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Size.topAppBarHeight)
                                .padding(horizontal = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Surface(
                                modifier = Modifier.size(Size.timeField),
                                shape = CircleShape,
                                color = colors.selectionContainer,
                                contentColor = colors.onSelectionContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (session.title.contains("practice", ignoreCase = true)) {
                                            Icons.Filled.Quiz
                                        } else {
                                            Icons.Filled.MenuBook
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(Size.standardIcon),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(
                                        R.string.organise_session_meta,
                                        stringResource(
                                            when {
                                                session.isRevision -> R.string.organise_task_revise
                                                session.title.contains("practice", ignoreCase = true) -> R.string.organise_task_practice
                                                else -> R.string.organise_task_learn
                                            },
                                        ),
                                        session.durationMinutes,
                                        LocalTime.ofSecondOfDay(session.startMinuteOfDay * 60L)
                                            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        if (index < state.todaySessions.lastIndex) {
                            HorizontalDivider(color = colors.outlineVariant, thickness = Stroke.hairline)
                        }
                    }
                    if (state.todaySessions.isEmpty()) {
                        Text(
                            stringResource(R.string.organise_no_sessions_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.organise_next_seven),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            LazyRow(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.week) { day ->
                    val selected = day == state.week.firstOrNull()
                    Surface(
                        shape = RoundedCornerShape(Radius.large),
                        color = if (selected) colors.primary else colors.surfaceContainerLow,
                        contentColor = if (selected) colors.onPrimary else colors.text,
                        modifier = Modifier
                            .width(Size.organiseDayWidth)
                            .height(Size.organiseDayHeight),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(day.date.format(DateTimeFormatter.ofPattern("E", Locale.getDefault())), style = MaterialTheme.typography.labelSmall)
                            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.organise_hours_short, day.plannedMinutes / 60f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            AppCard(tone = AppCardTone.Standard, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg)) {
                    Text(
                        state.week.firstOrNull()?.date?.format(
                            DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()),
                        ).orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.organise_timeline_body,
                            state.includedCount,
                            state.includedCount,
                            state.totalMinutes / 60f,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    HorizontalDivider(Modifier.padding(vertical = Spacing.md))
                    Text(stringResource(R.string.organise_projection_note), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SyllabusDraft(
    state: OrganiseUiState,
    order: PlanningOrder,
    onOrder: (PlanningOrder) -> Unit,
    onSelectSubject: (String) -> Unit,
    onToggleIncluded: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val selectedRows = state.chapters.filter { it.subjectId == state.selectedSubjectId }.let { rows ->
        when (order) {
            PlanningOrder.DEFAULT -> rows
            PlanningOrder.SHORTEST_FIRST -> rows.sortedBy { it.estimatedMinutes }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(state.subjects) { subject ->
                    FilterChip(
                        selected = state.selectedSubjectId == subject.first,
                        onClick = { onSelectSubject(subject.first) },
                        label = { Text(subject.second) },
                    )
                }
            }
        }
        item {
            Text(stringResource(R.string.organise_order), style = MaterialTheme.typography.titleMedium)
            LazyRow(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                item { FilterChip(order == PlanningOrder.DEFAULT, { onOrder(PlanningOrder.DEFAULT) }, { Text(stringResource(R.string.organise_order_default)) }) }
                item { FilterChip(order == PlanningOrder.SHORTEST_FIRST, { onOrder(PlanningOrder.SHORTEST_FIRST) }, { Text(stringResource(R.string.organise_order_shortest)) }) }
            }
            Text(stringResource(R.string.organise_order_hint), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.organise_chapters), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.organise_in_play, selectedRows.count { !it.excluded }), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null); Text(stringResource(R.string.organise_add)) }
            }
        }
        items(selectedRows, key = { it.id }) { row ->
            Surface(
                onClick = { onToggleIncluded(row.id) },
                color = if (row.excluded) colors.surfaceControl else colors.surfaceCard,
                shape = RoundedCornerShape(Radius.lg),
            ) {
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (row.excluded) Icons.Filled.CheckBoxOutlineBlank else Icons.Filled.CheckBox,
                        contentDescription = null,
                        tint = if (row.excluded) colors.textMuted else colors.brandDeep,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                        Text(row.title, style = MaterialTheme.typography.bodyLarge, color = if (row.excluded) colors.textMuted else colors.text)
                        Text(
                            if (row.excluded) stringResource(R.string.syllabus_parked) else stringResource(R.string.organise_hours_short, row.estimatedMinutes / 60f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    if (row.custom) IconButton(onClick = { onRemove(row.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.organise_delete_chapter), tint = colors.error)
                    }
                }
            }
        }
        if (selectedRows.isEmpty()) {
            item { Text(stringResource(R.string.organise_no_chapters), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary) }
        }
        item { Spacer(Modifier.height(Size.bottomClearance)) }
    }
}

@Composable
private fun SegmentedChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier = modifier) {
        Surface(onClick = onClick, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.height(Size.touchTarget), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) colors.primary else colors.text,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) Stroke.selected else Stroke.hairline)
                .background(if (selected) colors.primary else colors.outlineVariant),
        )
    }
}

@Composable
private fun DeltaRow(label: String, before: String, after: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.organise_change_value, before, after),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
