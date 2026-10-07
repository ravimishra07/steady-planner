package com.exam.assistant.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.design.ProLockCard
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.SessionListItem
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.MockSubject
import com.exam.assistant.domain.MockTest
import com.exam.assistant.domain.MockTestType
import com.exam.assistant.domain.MockTrendPoint
import com.exam.assistant.domain.formatInsightDate

@Composable
fun TestsRoute(
    attemptRepository: AttemptRepository,
    mockTestRepository: MockTestRepository,
    proRepository: ProRepository,
    onBack: () -> Unit,
    onAddMock: () -> Unit,
    onEditMock: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    viewModel: TestsViewModel = viewModel(factory = TestsViewModel.Factory(attemptRepository, mockTestRepository, proRepository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from the form: re-read the log.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TestsScreen(
        state = state,
        onBack = onBack,
        onAddMock = onAddMock,
        onEditMock = onEditMock,
        onToggleSubject = viewModel::toggleSubject,
        onOpenPaywall = onOpenPaywall,
        modifier = modifier,
    )
}

@Composable
fun TestsScreen(
    state: TestsUiState,
    onBack: () -> Unit,
    onAddMock: () -> Unit,
    onEditMock: (String) -> Unit,
    onToggleSubject: (MockSubject) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.day_log_back), tint = colors.textSecondary)
            }
            Text(
                stringResource(R.string.tests_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
        when {
            state.loading -> TestsNote(stringResource(R.string.insights_loading))
            state.loadFailed -> TestsNote(stringResource(R.string.insights_load_failed))
            !state.hasPlan -> TestsNote(stringResource(R.string.insights_no_plan))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
            ) {
                Button(onClick = onAddMock, modifier = Modifier.fillMaxWidth().height(Size.ctaHeight)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Size.smallIcon))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.tests_add))
                }
                if (state.mocks.isEmpty()) {
                    Text(
                        stringResource(R.string.tests_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xxl),
                    )
                } else {
                    SectionHeading(stringResource(R.string.tests_trend), stringResource(R.string.tests_trend_subtitle))
                    if (state.proUnlocked) {
                        MockTrendCard(state.trend, state.visibleSubjects, onToggleSubject)
                    } else {
                        ProLockCard(
                            title = stringResource(R.string.pro_lock_trend_title),
                            body = stringResource(R.string.pro_lock_trend_body),
                            actionLabel = stringResource(R.string.pro_lock_action),
                            onUnlock = onOpenPaywall,
                        )
                    }
                    SectionHeading(stringResource(R.string.tests_history), stringResource(R.string.tests_history_subtitle))
                    MockList(state.mocks, onEditMock)
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

// Pro: shown only when unlocked (TestsScreen).
@Composable
internal fun MockTrendCard(
    points: List<MockTrendPoint>,
    visible: Set<MockSubject>,
    onToggle: (MockSubject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            MockTrendChart(points, visible, Modifier.fillMaxWidth().height(Size.progressChartTall))
            Row(
                modifier = Modifier.padding(top = Spacing.md).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendSwatch(colors.brand, stringResource(R.string.tests_total))
                MockSubject.entries.forEach { subject ->
                    FilterChip(
                        selected = subject in visible,
                        onClick = { onToggle(subject) },
                        label = { Text(stringResource(subjectLabel(subject))) },
                        leadingIcon = { SwatchDot(subjectColor(subject)) },
                    )
                }
            }
            points.lastOrNull()?.let { latest ->
                Text(
                    stringResource(R.string.tests_latest, latest.total, latest.maxTotal, formatInsightDate(latest.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

/**
 * Score as a share of its maximum, oldest mock on the left. Totals are out of the mock's
 * maximum; a subject is out of a quarter of it, so all lines share one 0–100% scale.
 */
@Composable
private fun MockTrendChart(points: List<MockTrendPoint>, visible: Set<MockSubject>, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val gridColor = colors.borderSubtle
    val totalColor = colors.brand
    val subjectColors = MockSubject.entries.associateWith { subjectColor(it) }
    Canvas(modifier = modifier) {
        val strokeWidth = Stroke.emphasis.toPx()
        val dot = Stroke.selected.toPx() * 1.5f
        listOf(0f, .5f, 1f).forEach { fraction ->
            val y = size.height * (1 - fraction)
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), Stroke.hairline.toPx())
        }
        if (points.isEmpty()) return@Canvas
        fun x(index: Int) = if (points.size == 1) size.width / 2 else size.width * index / (points.size - 1)
        fun y(value: Int, max: Int) = size.height * (1 - (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0f, 1f))
        fun series(color: Color, values: List<Pair<Int, Offset>>) {
            if (values.size > 1) {
                val path = Path()
                values.forEachIndexed { i, (_, at) -> if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y) }
                drawPath(path, color, style = StrokeStyle(width = strokeWidth))
            }
            values.forEach { (_, at) -> drawCircle(color, dot, at) }
        }
        MockSubject.entries.filter { it in visible }.forEach { subject ->
            series(
                subjectColors.getValue(subject),
                points.mapIndexedNotNull { i, p -> p.subjects[subject]?.let { i to Offset(x(i), y(it, p.maxTotal / MockSubject.entries.size)) } },
            )
        }
        series(totalColor, points.mapIndexed { i, p -> i to Offset(x(i), y(p.total, p.maxTotal)) })
    }
}

@Composable
private fun MockList(mocks: List<MockTest>, onEdit: (String) -> Unit) {
    val colors = AppTheme.colors
    AppCard {
        mocks.forEachIndexed { index, mock ->
            val type = stringResource(mockTypeLabel(mock.type))
            SessionListItem(
                time = formatInsightDate(mock.date),
                title = mock.name.ifBlank { type },
                detail = if (mock.name.isBlank()) "" else type,
                duration = stringResource(R.string.tests_score, mock.total, mock.maxTotal),
                onClick = { onEdit(mock.id) },
            )
            if (index < mocks.lastIndex) HorizontalDivider(color = colors.borderSubtle)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.md)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Size.touchTarget)) {
        SwatchDot(color)
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Canvas(modifier = Modifier.size(Size.legendDot)) { drawCircle(color) }
}

@Composable
private fun TestsNote(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}

@Composable
internal fun subjectColor(subject: MockSubject): Color = when (subject) {
    MockSubject.PHYSICS -> AppTheme.colors.accentCyan
    MockSubject.CHEMISTRY -> AppTheme.colors.warning
    MockSubject.BOTANY -> AppTheme.colors.success
    MockSubject.ZOOLOGY -> AppTheme.colors.info
}

internal fun subjectLabel(subject: MockSubject): Int = when (subject) {
    MockSubject.PHYSICS -> R.string.tests_physics
    MockSubject.CHEMISTRY -> R.string.tests_chemistry
    MockSubject.BOTANY -> R.string.tests_botany
    MockSubject.ZOOLOGY -> R.string.tests_zoology
}

internal fun mockTypeLabel(type: MockTestType): Int = when (type) {
    MockTestType.UNIT_TEST -> R.string.tests_type_unit
    MockTestType.PART_TEST -> R.string.tests_type_part
    MockTestType.FULL_MOCK -> R.string.tests_type_full
    MockTestType.PYQ_PAPER -> R.string.tests_type_pyq
}
