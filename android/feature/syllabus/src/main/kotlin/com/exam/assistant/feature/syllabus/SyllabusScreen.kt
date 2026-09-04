package com.exam.assistant.feature.syllabus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppCardTone
import com.exam.assistant.core.design.Elevation
import com.exam.assistant.core.design.Opacity
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.PendingSyllabusPick
import com.exam.assistant.domain.SyllabusTickState
import kotlinx.coroutines.launch

@Composable
fun SyllabusRoute(
    examPackRepository: ExamPackRepository,
    topicProgressRepository: TopicProgressRepository,
    attemptRepository: AttemptRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    rollingPlanRepository: RollingPlanRepository,
    onStartTopic: (PendingSyllabusPick) -> Unit,
    onOrganise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyllabusViewModel = viewModel(
        factory = SyllabusViewModel.Factory(examPackRepository, topicProgressRepository, attemptRepository, targetSyllabusRepository, rollingPlanRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SyllabusScreen(
        state = state,
        onToggleExpand = viewModel::toggleExpand,
        onToggleTick = viewModel::toggleTick,
        onStartTopic = onStartTopic,
        onOrganise = onOrganise,
        modifier = modifier,
    )
}

private enum class SyllabusFilter { DUE, CLASS_11, CLASS_12 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusScreen(
    state: SyllabusUiState,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
    onOrganise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filters by remember { mutableStateOf<Set<SyllabusFilter>>(emptySet()) }
    var filtersOpen by remember { mutableStateOf(false) }
    var selectedSubjectKey by remember(state.subjects) {
        mutableStateOf(state.subjects.firstOrNull()?.key)
    }
    val visibleSubjects = state.subjects.filter { subject -> subject.key == selectedSubjectKey }
    val selectedSubject = visibleSubjects.firstOrNull()
    val dueCount = selectedSubject?.chapterStats?.count { !it.complete }.orZero()
    val shownChapterCount = selectedSubject?.chapterStats?.count { chapter ->
        chapter.matches(filters)
    }.orZero()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.tabBarHeight)
                    .padding(start = Spacing.lg, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.syllabus_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { filtersOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = stringResource(R.string.syllabus_filter),
                        tint = colors.textSecondary,
                    )
                }
                IconButton(onClick = onOrganise) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = stringResource(R.string.syllabus_edit),
                        tint = colors.textSecondary,
                    )
                }
            }
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }

                state.isEmpty -> EmptySyllabus(onOrganise = onOrganise)

                else -> {
                    SubjectTabs(
                        subjects = state.subjects,
                        selectedKey = selectedSubjectKey,
                        onSelect = { subject ->
                            selectedSubjectKey = subject.key
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    )

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            top = Spacing.lg,
                            end = Spacing.lg,
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(visibleSubjects, key = { it.key }) { subject ->
                            SubjectCard(
                                subject = subject,
                                onToggleExpand = onToggleExpand,
                                onToggleTick = onToggleTick,
                                onStartTopic = onStartTopic,
                                filters = filters,
                            )
                        }
                    }
                }
            }
        }

        if (filtersOpen && selectedSubject != null) {
            SyllabusFilterPopup(
                filters = filters,
                dueCount = dueCount,
                shownChapterCount = shownChapterCount,
                onToggle = { selected ->
                    filters = if (selected in filters) filters - selected else filters + selected
                },
                onReset = { filters = emptySet() },
                onDismiss = { filtersOpen = false },
            )
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun SyllabusChapterStat.matches(filters: Set<SyllabusFilter>): Boolean {
    if (SyllabusFilter.DUE in filters && complete) return false
    return matchesClass(classNumber, filters)
}

private fun SyllabusTreeRow.matches(filters: Set<SyllabusFilter>): Boolean {
    if (SyllabusFilter.DUE in filters && tickState == SyllabusTickState.ALL) return false
    return matchesClass(classNumber, filters)
}

private fun matchesClass(classNumber: Int?, filters: Set<SyllabusFilter>): Boolean {
    val class11 = SyllabusFilter.CLASS_11 in filters
    val class12 = SyllabusFilter.CLASS_12 in filters
    return when {
        class11 == class12 -> true
        class11 -> classNumber == 11
        else -> classNumber == 12
    }
}

@Composable
private fun EmptySyllabus(onOrganise: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.syllabus_empty_title), style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.text)
        Text(
            stringResource(R.string.syllabus_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Surface(
            onClick = onOrganise,
            shape = RoundedCornerShape(Radius.pill),
            color = AppTheme.colors.brandDeep,
            modifier = Modifier.padding(top = Spacing.lg),
        ) {
            Text(
                stringResource(R.string.syllabus_add_first_chapter),
                color = AppTheme.colors.onBrand,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectTabs(
    subjects: List<SyllabusSubjectCard>,
    selectedKey: String?,
    onSelect: (SyllabusSubjectCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val selectedIndex = subjects.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    SecondaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
        contentColor = colors.textSecondary,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                height = Stroke.tabIndicator,
                color = colors.primary,
            )
        },
        divider = {
            HorizontalDivider(
                thickness = Stroke.hairline,
                color = colors.outlineVariant,
            )
        },
    ) {
        subjects.forEach { subject ->
            val selected = subject.key == selectedKey
            Tab(
                selected = selected,
                onClick = { onSelect(subject) },
                modifier = Modifier.height(Size.syllabusSubjectTabHeight),
                selectedContentColor = colors.primary,
                unselectedContentColor = colors.textSecondary,
                icon = {
                    Icon(
                        imageVector = subjectIcon(subject.subjectId),
                        contentDescription = null,
                        modifier = Modifier.size(Size.standardIcon),
                    )
                },
                text = {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun SyllabusFilterPopup(
    filters: Set<SyllabusFilter>,
    dueCount: Int,
    shownChapterCount: Int,
    onToggle: (SyllabusFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = Opacity.modalScrim))
                .clickable(onClick = onDismiss),
        )
        Surface(
            shape = RoundedCornerShape(Radius.extraLarge),
            color = colors.surfaceContainerHigh,
            tonalElevation = Elevation.floatingTonal,
            shadowElevation = Elevation.floatingShadow,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Size.syllabusFilterPopupTop, start = Spacing.md, end = Spacing.md)
                .widthIn(max = Size.syllabusFilterPopupWidth)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.syllabus_filter),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(Size.timeField)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.syllabus_close_filters),
                            tint = colors.textSecondary,
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    FilterPill(
                        label = stringResource(R.string.syllabus_filter_due_compact, dueCount),
                        selected = SyllabusFilter.DUE in filters,
                        onClick = { onToggle(SyllabusFilter.DUE) },
                    )
                    FilterPill(
                        label = stringResource(R.string.syllabus_class_11),
                        selected = SyllabusFilter.CLASS_11 in filters,
                        onClick = { onToggle(SyllabusFilter.CLASS_11) },
                    )
                    FilterPill(
                        label = stringResource(R.string.syllabus_class_12),
                        selected = SyllabusFilter.CLASS_12 in filters,
                        onClick = { onToggle(SyllabusFilter.CLASS_12) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onReset,
                        shape = RoundedCornerShape(Radius.pill),
                        color = Color.Transparent,
                        modifier = Modifier.height(Size.timeField),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.syllabus_reset_filters),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.primary,
                            )
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(Radius.pill),
                        color = colors.primary,
                        modifier = Modifier.weight(1f).height(Size.timeField),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.syllabus_show_chapters,
                                    shownChapterCount,
                                    shownChapterCount,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun subjectIcon(subjectId: String): ImageVector = when (subjectId.lowercase()) {
    "physics" -> Icons.Filled.Bolt
    "chemistry" -> Icons.Filled.Science
    "botany" -> Icons.Filled.LocalFlorist
    "zoology" -> Icons.Filled.Pets
    else -> Icons.AutoMirrored.Filled.MenuBook
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.small),
        color = if (selected) colors.selectionContainer else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(Stroke.hairline, colors.outlineVariant),
        modifier = Modifier.height(Size.compactControl),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.icon),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onSelectionContainer,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) colors.onSelectionContainer else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SubjectCard(
    subject: SyllabusSubjectCard,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
    filters: Set<SyllabusFilter>,
) {
    val colors = AppTheme.colors
    AppCard(
        tone = AppCardTone.Raised,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(Size.syllabusRing), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { subject.percent / 100f },
                        modifier = Modifier.size(Size.syllabusRing),
                        color = colors.primary,
                        trackColor = colors.text.copy(alpha = 0.12f),
                        strokeWidth = Size.syllabusRingStroke,
                    )
                    Text(
                        text = stringResource(R.string.syllabus_percent_value, subject.percent),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.syllabus_subject_summary,
                            subject.totalChapterCount,
                            subject.doneChapterCount,
                            subject.totalChapterCount,
                            subject.totalEstimatedMinutes / 60,
                            subject.questions,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                PlayButton(
                    size = Size.syllabusPrimaryAction,
                    filled = true,
                    description = stringResource(R.string.syllabus_continue_topic, subject.firstTopicTitle),
                    onClick = {
                        val topicKey = subject.firstTopicKey ?: return@PlayButton
                        onStartTopic(
                            PendingSyllabusPick(
                                nodeKey = topicKey,
                                title = subject.firstTopicTitle,
                                sectionName = subject.name,
                                subjectId = subject.subjectId,
                                topicPath = subject.name,
                            ),
                        )
                    },
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = Spacing.sm)
                    .clickable { onToggleExpand(subject.key) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (subject.expanded) R.string.syllabus_hide_details else R.string.syllabus_see_details,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                )
                Icon(
                    imageVector = if (subject.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }

            if (subject.expanded) {
                Spacer(Modifier.height(Spacing.md))
                HorizontalDivider(color = colors.outlineVariant)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    subject.rows
                        .filter { row -> row.matches(filters) }
                        .withVisibleBranchEnds()
                        .forEach { row ->
                        SyllabusRowView(
                            row = row,
                            onToggleExpand = onToggleExpand,
                            onToggleTick = onToggleTick,
                            onStartTopic = onStartTopic,
                        )
                    }
                }
            }
        }
    }
}

private fun List<SyllabusTreeRow>.withVisibleBranchEnds(): List<SyllabusTreeRow> =
    mapIndexed { index, row ->
        if (row.depth == 0) return@mapIndexed row
        val hasLaterSibling = drop(index + 1)
            .takeWhile { candidate -> candidate.depth >= row.depth }
            .any { candidate -> candidate.depth == row.depth }
        row.copy(isLastChild = !hasLaterSibling)
    }

@Composable
private fun SyllabusRowView(
    row: SyllabusTreeRow,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
) {
    val colors = AppTheme.colors
    val muted = row.tickState == SyllabusTickState.ALL || row.excluded
    val rowName = if (row.isClassGroup) {
        stringResource(R.string.syllabus_class_number, row.classNumber ?: 0)
    } else {
        row.name
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.hasChildren) Modifier.clickable { onToggleExpand(row.key) } else Modifier)
            .height(IntrinsicSize.Min)
            .heightIn(min = Size.syllabusRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.depth > 0) {
            TreeConnectors(row = row)
        }
        if (row.hasChildren) {
            Icon(
                imageVector = if (row.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (row.expanded) {
                    stringResource(R.string.syllabus_collapse)
                } else {
                    stringResource(R.string.syllabus_expand)
                },
                tint = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xxs).size(Size.smallIcon),
            )
            Spacer(Modifier.width(Spacing.xs))
        }

        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(
                text = rowName,
                style = when (row.depth) {
                    0 -> MaterialTheme.typography.titleMedium
                    1 -> MaterialTheme.typography.bodyLarge
                    else -> MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
                color = if (muted) colors.textMuted else colors.text,
            )
            if (row.excluded) {
                Text(
                    text = stringResource(R.string.syllabus_parked),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
            if (row.depth <= 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    LinearProgressIndicator(
                        progress = { row.percent / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(Size.progressHeight)
                            .clip(RoundedCornerShape(Spacing.xxs)),
                        color = if (muted) colors.success else colors.brandSoft,
                        trackColor = colors.text.copy(alpha = 0.1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = if (row.depth == 1) {
                            val hours = row.estimatedMinutes / 60
                            val minutes = row.estimatedMinutes % 60
                            if (minutes == 0) {
                                stringResource(R.string.syllabus_hours_short, hours)
                            } else {
                                stringResource(R.string.syllabus_hours_minutes, hours, minutes)
                            }
                        } else {
                            stringResource(R.string.syllabus_percent_value, row.percent)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
        }

        StatusDot(
            state = row.tickState,
            onClick = { onToggleTick(row.key) },
        )
        if (!row.hasChildren) {
            Spacer(Modifier.width(Spacing.sm))
            PlayButton(
                size = Size.syllabusRowAction,
                filled = false,
                onClick = {
                    onStartTopic(
                        PendingSyllabusPick(
                            nodeKey = row.key,
                            title = rowName,
                            sectionName = row.sectionName,
                            subjectId = row.subjectId,
                            topicPath = row.topicPath,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun TreeConnectors(row: SyllabusTreeRow) {
    val connectorColor = AppTheme.colors.outlineVariant
    Canvas(
        modifier = Modifier
            .width(Size.syllabusBranchIndent * row.depth)
            .fillMaxHeight(),
    ) {
        val branchWidth = Size.syllabusBranchIndent.toPx()
        val railOffset = Size.syllabusRailOffset.toPx()
        val hookTop = Size.syllabusHookTop.toPx()
        val hookHeight = Size.syllabusHookHeight.toPx()
        val hookWidth = Size.syllabusHookWidth.toPx()
        val strokeWidth = Stroke.hairline.toPx()

        repeat((row.depth - 1).coerceAtLeast(0)) { level ->
            val x = (branchWidth * level) + railOffset
            drawLine(
                color = connectorColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = strokeWidth,
            )
        }

        val currentRailX = (branchWidth * (row.depth - 1)) + railOffset
        val hookBottom = hookTop + hookHeight
        drawLine(
            color = connectorColor,
            start = androidx.compose.ui.geometry.Offset(currentRailX, 0f),
            end = androidx.compose.ui.geometry.Offset(
                currentRailX,
                if (row.isLastChild && !(row.expanded && row.hasChildren)) hookBottom else size.height,
            ),
            strokeWidth = strokeWidth,
        )
        val hook = Path().apply {
            moveTo(currentRailX, hookTop)
            cubicTo(
                currentRailX,
                hookBottom,
                currentRailX,
                hookBottom,
                currentRailX + hookHeight,
                hookBottom,
            )
            lineTo(currentRailX + hookWidth, hookBottom)
        }
        drawPath(
            path = hook,
            color = connectorColor,
            style = DrawStroke(width = strokeWidth),
        )
    }
}

@Composable
private fun StatusDot(state: SyllabusTickState, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val background = if (state == SyllabusTickState.ALL) colors.success else colors.surfaceControl
    val tint = if (state == SyllabusTickState.ALL) colors.onSuccess else colors.textMuted
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = background,
        modifier = Modifier.size(Size.syllabusStatus),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.syllabus_mark_done),
                tint = tint,
                modifier = Modifier.padding(Spacing.icon),
            )
        }
    }
}

@Composable
private fun PlayButton(
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) colors.selectionContainer else Color.Transparent,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(Stroke.emphasis, colors.primary),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = description ?: stringResource(R.string.syllabus_start_topic),
                tint = if (filled) colors.onSelectionContainer else colors.primary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
