package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.SyllabusTickState

@Composable
fun SyllabusRoute(
    syllabusRepository: SyllabusRepository,
    syllabusStore: SyllabusStore,
    modifier: Modifier = Modifier,
    viewModel: SyllabusViewModel = viewModel(
        factory = SyllabusViewModel.Factory(syllabusRepository, syllabusStore),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SyllabusScreen(
        state = state,
        onSelectSection = viewModel::selectSection,
        onToggleExpand = viewModel::toggleExpand,
        onToggleTick = viewModel::toggleTick,
        modifier = modifier,
    )
}

@Composable
fun SyllabusScreen(
    state: SyllabusUiState,
    onSelectSection: (Int) -> Unit,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
    ) {
        Text(
            text = stringResource(R.string.syllabus_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = state.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            state.sectionTabs.forEachIndexed { index, label ->
                FilterChip(
                    selected = index == state.sectionIndex,
                    onClick = { onSelectSection(index) },
                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.brandContainer,
                        selectedLabelColor = colors.onBrandContainer,
                    ),
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(state.rows, key = { it.key }) { row ->
                SyllabusTreeRowView(
                    row = row,
                    onToggleExpand = onToggleExpand,
                    onToggleTick = onToggleTick,
                )
            }
        }
    }
}

@Composable
private fun SyllabusTreeRowView(
    row: SyllabusTreeRow,
    onToggleExpand: (String) -> Unit,
    onToggleTick: (String) -> Unit,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.md + (row.depth * 12).dp,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.hasChildren) {
                IconButton(
                    onClick = { onToggleExpand(row.key) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (row.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (row.expanded) {
                            stringResource(R.string.syllabus_collapse)
                        } else {
                            stringResource(R.string.syllabus_expand)
                        },
                        tint = colors.textMuted,
                    )
                }
            } else {
                Box(modifier = Modifier.size(32.dp))
            }
            TickBox(
                state = row.tickState,
                onClick = { onToggleTick(row.key) },
            )
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                color = when (row.tickState) {
                    SyllabusTickState.ALL -> colors.textMuted
                    else -> colors.text
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm),
            )
            Text(
                text = row.hoursLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun TickBox(
    state: SyllabusTickState,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val borderColor = when (state) {
        SyllabusTickState.ALL -> colors.success
        SyllabusTickState.PARTIAL -> colors.brandDeep
        SyllabusTickState.NONE -> colors.border
    }
    val fillColor = when (state) {
        SyllabusTickState.ALL -> colors.success
        SyllabusTickState.PARTIAL -> colors.brandContainer
        SyllabusTickState.NONE -> colors.surface
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(22.dp),
        shape = CircleShape,
        color = fillColor,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        if (state == SyllabusTickState.ALL) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.syllabus_mark_done),
                tint = colors.onSuccess,
                modifier = Modifier.padding(3.dp),
            )
        }
    }
}
