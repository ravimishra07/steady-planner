package com.exam.assistant.feature.syllabus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.PendingSyllabusPick
import com.exam.assistant.domain.SyllabusTickState
import kotlinx.coroutines.launch

@Composable
fun SyllabusRoute(
    examPackRepository: ExamPackRepository,
    topicProgressRepository: TopicProgressRepository,
    attemptRepository: AttemptRepository,
    onStartTopic: (PendingSyllabusPick) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyllabusViewModel = viewModel(
        factory = SyllabusViewModel.Factory(examPackRepository, topicProgressRepository, attemptRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SyllabusScreen(
        state = state,
        onToggleExpand = viewModel::toggleExpand,
        onToggleTick = viewModel::toggleTick,
        onStartTopic = onStartTopic,
        modifier = modifier,
    )
}

private enum class SyllabusFilter { ALL, DUE }

@Composable
fun SyllabusScreen(
    state: SyllabusUiState,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(SyllabusFilter.ALL) }
    val visibleSubjects = if (filter == SyllabusFilter.DUE) {
        state.subjects.filter { it.percent < 100 }
    } else {
        state.subjects
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        SubjectChipRow(
            subjects = state.subjects,
            onSelect = { subject ->
                val index = visibleSubjects.indexOfFirst { it.key == subject.key }
                if (index >= 0) {
                    if (!subject.expanded) onToggleExpand(subject.key)
                    scope.launch { listState.animateScrollToItem(index) }
                }
            },
            modifier = Modifier.padding(top = Spacing.md),
        )
        FilterStatsBar(
            filter = filter,
            onSelectFilter = { filter = it },
            allCount = state.allCount,
            dueCount = state.dueCount,
            completedPercentLabel = state.completedPercentLabel,
            timeSpentLabel = state.timeSpentLabel,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                bottom = Spacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(visibleSubjects, key = { it.key }) { subject ->
                SubjectCard(
                    subject = subject,
                    onToggleExpand = onToggleExpand,
                    onToggleTick = onToggleTick,
                    onStartTopic = onStartTopic,
                )
            }
        }
    }
}

@Composable
private fun SubjectChipRow(
    subjects: List<SyllabusSubjectCard>,
    onSelect: (SyllabusSubjectCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(subjects, key = { it.key }) { subject ->
            FilterChip(
                selected = false,
                onClick = { onSelect(subject) },
                label = { Text(subject.shortLabel, style = MaterialTheme.typography.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceCard,
                    labelColor = colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
private fun FilterStatsBar(
    filter: SyllabusFilter,
    onSelectFilter: (SyllabusFilter) -> Unit,
    allCount: Int,
    dueCount: Int,
    completedPercentLabel: String,
    timeSpentLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.syllabus_filter),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilterPill(
                    label = stringResource(R.string.syllabus_filter_all, allCount),
                    selected = filter == SyllabusFilter.ALL,
                    onClick = { onSelectFilter(SyllabusFilter.ALL) },
                )
                FilterPill(
                    label = stringResource(R.string.syllabus_filter_due, dueCount),
                    selected = filter == SyllabusFilter.DUE,
                    onClick = { onSelectFilter(SyllabusFilter.DUE) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatBox(label = stringResource(R.string.syllabus_completed), value = completedPercentLabel)
            StatBox(label = stringResource(R.string.syllabus_time_spent), value = timeSpentLabel)
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) colors.brandDeep else colors.surfaceCard,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) colors.onBrand else colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    val colors = AppTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
        )
        Surface(
            shape = RoundedCornerShape(Radius.md),
            color = colors.surfaceCard,
            modifier = Modifier.padding(top = Spacing.xs),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

private val INDENT_WIDTH = 18.dp
private val CONNECTOR_WIDTH = 1.5.dp

@Composable
private fun SubjectCard(
    subject: SyllabusSubjectCard,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surfaceCard,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { subject.percent / 100f },
                        modifier = Modifier.size(64.dp),
                        color = colors.brandSoft,
                        trackColor = colors.text.copy(alpha = 0.12f),
                        strokeWidth = 5.dp,
                    )
                    Text(
                        text = "${subject.percent}%",
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
                        text = subject.timeSpentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                PlayButton(
                    size = 56.dp,
                    filled = true,
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
                    modifier = Modifier.size(18.dp),
                )
            }

            if (subject.expanded) {
                Spacer(Modifier.height(Spacing.md))
                androidx.compose.material3.HorizontalDivider(color = colors.hairlineSoft)
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    subject.rows.forEach { row ->
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

@Composable
private fun SyllabusRowView(
    row: SyllabusTreeRow,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    onStartTopic: (PendingSyllabusPick) -> Unit,
) {
    val colors = AppTheme.colors
    val connectorColor = colors.brandSoft.copy(alpha = 0.3f)
    val muted = row.tickState == SyllabusTickState.ALL

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.hasChildren) Modifier.clickable { onToggleExpand(row.key) } else Modifier)
            .height(IntrinsicSize.Min)
            .padding(
                top = if (row.depth == 0) Spacing.md else Spacing.sm,
                bottom = if (row.depth == 0) Spacing.sm else Spacing.sm,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (row.depth > 0) {
            Spacer(Modifier.width(INDENT_WIDTH * (row.depth - 1)))
            Box(modifier = Modifier.width(INDENT_WIDTH).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = INDENT_WIDTH / 2)
                        .width(CONNECTOR_WIDTH)
                        .fillMaxHeight()
                        .background(connectorColor),
                )
            }
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
                modifier = Modifier.padding(top = 2.dp).size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }

        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(
                text = row.name,
                style = if (row.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Medium,
                color = if (muted) colors.textMuted else colors.text,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { row.percent / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (muted) colors.success else colors.brandSoft,
                    trackColor = colors.text.copy(alpha = 0.1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "${row.percent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }

        StatusDot(
            state = row.tickState,
            onClick = { onToggleTick(row.key) },
        )
        Spacer(Modifier.width(Spacing.sm))
        PlayButton(
            size = 34.dp,
            filled = false,
            onClick = {
                onStartTopic(
                    PendingSyllabusPick(
                        nodeKey = row.key,
                        title = row.name,
                        sectionName = row.sectionName,
                        subjectId = row.subjectId,
                        topicPath = row.topicPath,
                    ),
                )
            },
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
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.syllabus_mark_done),
                tint = tint,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

@Composable
private fun PlayButton(size: androidx.compose.ui.unit.Dp, filled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) colors.brandDeep else Color.Transparent,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.5.dp, colors.brandSoft),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.syllabus_start_topic),
                tint = if (filled) colors.onBrand else colors.brandSoft,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
