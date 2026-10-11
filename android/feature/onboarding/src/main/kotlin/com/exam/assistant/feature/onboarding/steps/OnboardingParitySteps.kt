@file:OptIn(ExperimentalLayoutApi::class)

package com.exam.assistant.feature.onboarding.steps

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.exam.assistant.core.design.ThemePreviewMetrics
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppStepperControl
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.CalendarMetrics
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.rememberHaptics
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.PlanFit
import com.exam.assistant.domain.PlanFitOption
import com.exam.assistant.domain.PlanMode
import com.exam.assistant.domain.StartingPoint
import com.exam.assistant.feature.onboarding.CapacityIssuesCard
import com.exam.assistant.feature.onboarding.CapacityIssue
import com.exam.assistant.feature.onboarding.CommitmentIssue
import com.exam.assistant.feature.onboarding.freeMinutesOnDay
import com.exam.assistant.feature.onboarding.R
import com.exam.assistant.feature.onboarding.components.OnboardingSelectableCard
import com.exam.assistant.feature.onboarding.components.selectionBorder
import com.exam.assistant.feature.onboarding.model.ExamCatalog
import com.exam.assistant.feature.onboarding.model.OnboardingCommitment
import com.exam.assistant.feature.onboarding.model.OnboardingUiState
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * "Choose a theme": background carousel. The centered page is the selection; the
 * accent picker lives above Continue ([OnboardingAccentPicker]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnboardingAppearanceStep(
    background: BackgroundAppearance,
    accent: AccentPalette,
    onBackground: (BackgroundAppearance) -> Unit,
) {
    val haptics = rememberHaptics()
    val options = BackgroundAppearance.entries
    val pagerState = rememberPagerState(initialPage = options.indexOf(background)) { options.size }
    val scope = rememberCoroutineScope()
    val currentBackground by rememberUpdatedState(background)
    val currentOnBackground by rememberUpdatedState(onBackground)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settled = options[page]
            if (settled != currentBackground) currentOnBackground(settled)
        }
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val ringColor = accentColor
    val gapColor = AppTheme.colors.bg

    Column {
        BoxWithConstraints(Modifier.bleedHorizontally(Spacing.lg)) {
            val side = ((maxWidth - ThemePreviewMetrics.pageWidth) / 2).coerceAtLeast(Spacing.none)
            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(ThemePreviewMetrics.pageWidth),
                pageSpacing = ThemePreviewMetrics.pageSpacing,
                contentPadding = PaddingValues(horizontal = side, vertical = Spacing.sm),
                modifier = Modifier.fillMaxWidth().selectableGroup(),
            ) { page ->
                val option = options[page]
                val centered = page == pagerState.currentPage
                val name = stringResource(backgroundLabel(option))
                Box(
                    Modifier
                        .graphicsLayer {
                            val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .absoluteValue.coerceAtMost(1f)
                            val scale = 1f - (1f - ThemePreviewMetrics.minScale) * distance
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (1f - ThemePreviewMetrics.minAlpha) * distance
                        }
                        .previewRing(visible = centered, ring = ringColor, gap = gapColor)
                        .clip(RoundedCornerShape(ThemePreviewMetrics.corner))
                        .selectable(
                            selected = centered,
                            role = Role.RadioButton,
                            onClick = { haptics.select(); scope.launch { pagerState.animateScrollToPage(page) } },
                        )
                        .semantics { contentDescription = name },
                ) {
                    BackgroundMiniature(option, accent.primaryFor(option.isDark))
                }
            }
        }
        Text(
            text = stringResource(backgroundLabel(options[pagerState.currentPage])),
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        ) {
            options.indices.forEach { index ->
                val active = index == pagerState.currentPage
                val width by animateDpAsState(if (active) Size.pagerDotActive else Size.pagerDot, label = "pagerDot")
                Box(
                    Modifier
                        .size(width = width, height = Size.pagerDot)
                        .clip(CircleShape)
                        .background(if (active) accentColor else AppTheme.colors.border),
                )
            }
        }
    }
}

/** Five accent circles in one pill outline, docked above Continue on the theme step. */
@Composable
internal fun OnboardingAccentPicker(
    accent: AccentPalette,
    onAccent: (AccentPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val dark = AppTheme.colors.isDark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(Stroke.hairline, AppTheme.colors.outline, CircleShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentPalette.entries.forEach { option ->
            val selected = option == accent
            val name = stringResource(accentLabel(option))
            Box(
                modifier = Modifier
                    .size(Size.touchTarget)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.select(); onAccent(option) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(Size.accentDot).clip(CircleShape).background(option.primaryFor(dark)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = option.onPrimaryFor(dark),
                            modifier = Modifier.size(Size.accentCheck),
                        )
                    }
                }
            }
        }
    }
}

/** Lets a full-bleed child ignore the screen margin of its parent column. */
private fun Modifier.bleedHorizontally(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

/** 2dp accent ring with a 2dp gap outside the preview, drawn beyond its bounds. */
private fun Modifier.previewRing(visible: Boolean, ring: Color, gap: Color): Modifier =
    if (!visible) this else drawBehind {
        val gapPx = ThemePreviewMetrics.ringGap.toPx()
        val ringPx = ThemePreviewMetrics.ringWidth.toPx()
        val corner = ThemePreviewMetrics.corner.toPx()
        val outset = gapPx + ringPx / 2
        drawRoundRect(
            color = gap,
            topLeft = Offset(-gapPx / 2, -gapPx / 2),
            size = androidx.compose.ui.geometry.Size(size.width + gapPx, size.height + gapPx),
            cornerRadius = CornerRadius(corner + gapPx / 2),
            style = DrawStroke(width = gapPx),
        )
        drawRoundRect(
            color = ring,
            topLeft = Offset(-outset, -outset),
            size = androidx.compose.ui.geometry.Size(size.width + outset * 2, size.height + outset * 2),
            cornerRadius = CornerRadius(corner + outset),
            style = DrawStroke(width = ringPx),
        )
    }

@Composable
private fun AppearanceLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textSecondary,
    )
}

/** A Today screen in miniature: header, hero card, two block rows, bottom nav. */
@Composable
private fun BackgroundMiniature(option: BackgroundAppearance, accent: Color) {
    val ink = option.previewInk
    val m = ThemePreviewMetrics
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(m.aspectRatio)
            .clip(RoundedCornerShape(m.corner))
            .background(option.previewBackground)
            .border(Stroke.hairline, AppTheme.colors.outline, RoundedCornerShape(m.corner))
            .padding(Stroke.hairline),
    ) {
        val inner = maxWidth
        val block = inner - m.sideMargin * 2
        Column(Modifier.padding(top = m.topPadding)) {
            PreviewBar(inner * m.headerWidth, m.headerHeight, ink, Modifier.padding(start = m.sideMargin))
            PreviewBar(inner * m.lineWidth, m.lineHeight, ink.copy(alpha = m.inkSoft), Modifier.padding(start = m.sideMargin, top = m.lineTop))
            Column(
                modifier = Modifier
                    .padding(start = m.sideMargin, top = m.heroTop)
                    .size(block, m.heroHeight)
                    .clip(RoundedCornerShape(m.heroCorner))
                    .background(ink.copy(alpha = m.inkFill))
                    .padding(m.heroPadding),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                val heroInner = block - m.heroPadding * 2
                PreviewBar(heroInner * m.heroAccentWidth, m.heroBarHeight, accent)
                PreviewBar(heroInner * m.heroInkWidth, m.heroBarHeight, ink.copy(alpha = m.inkSoft))
            }
            listOf(m.itemBarWidth, m.itemBarShortWidth).forEach { fraction ->
                Row(
                    modifier = Modifier
                        .padding(start = m.sideMargin, top = m.itemTop)
                        .size(block, m.itemHeight)
                        .clip(RoundedCornerShape(m.itemCorner))
                        .background(ink.copy(alpha = m.inkFill))
                        .padding(horizontal = m.itemPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(m.itemGap),
                ) {
                    Box(Modifier.size(m.itemRing).border(m.itemRingStroke, accent, CircleShape))
                    PreviewBar((block - m.itemPadding * 2) * fraction, m.itemBarHeight, ink.copy(alpha = m.inkStrong))
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(m.navHeight)
                .background(ink.copy(alpha = m.inkNav))
                .padding(horizontal = m.sideMargin),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewBar(m.navActiveWidth, m.navIcon, accent)
            repeat(3) {
                Box(
                    Modifier
                        .size(m.navIcon)
                        .clip(RoundedCornerShape(m.navIconCorner))
                        .background(ink.copy(alpha = m.inkSoft)),
                )
            }
        }
    }
}

@Composable
private fun PreviewBar(width: Dp, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).clip(CircleShape).background(color))
}

private data class CoachingChoice(val id: String, val nameRes: Int, val icon: ImageVector)

private val primaryCoachingChoices = listOf(
    CoachingChoice("self", R.string.onboarding_coaching_self, Icons.Filled.Person),
    CoachingChoice("school", R.string.onboarding_coaching_school, Icons.Filled.School),
    CoachingChoice("coaching", R.string.onboarding_coaching_choice, Icons.Filled.Groups),
)

/** M3 Expressive grouped list: rows joined by 2dp gaps, 20dp outer corners, 4dp inner. */
@Composable
internal fun OnboardingCoachingStep(selectedId: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(top = Spacing.sm).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        primaryCoachingChoices.forEachIndexed { index, choice ->
            val shape = groupShape(index, primaryCoachingChoices.size)
            CoachingRow(choice, selected = selectedId == choice.id, shape = shape) {
                onSelect(choice.id)
            }
        }
    }
}

private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) Radius.group else Radius.extraSmall
    val bottom = if (index == count - 1) Radius.group else Radius.extraSmall
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun CoachingRow(choice: CoachingChoice, selected: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = AppTheme.colors.elevated,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = { haptics.select(); onClick() })
                .heightIn(min = Size.listRow)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                imageVector = choice.icon,
                contentDescription = null,
                tint = if (selected) primary else AppTheme.colors.textSecondary,
                modifier = Modifier.size(Size.standardIcon),
            )
            Text(
                text = stringResource(choice.nameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = AppTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = primary,
                    unselectedColor = AppTheme.colors.outline,
                ),
            )
        }
    }
}

@Composable
internal fun OnboardingCommitmentsStep(
    state: OnboardingUiState,
    issues: List<CommitmentIssue>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleDay: (String, Int) -> Unit,
    onStart: (String, Int) -> Unit,
    onEnd: (String, Int) -> Unit,
    onWake: (Int) -> Unit,
    onSleep: (Int) -> Unit,
) {
    val haptics = rememberHaptics()
    var showAddOptions by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Size.smallIcon),
                )
                AppearanceLabel(R.string.onboarding_commitments_suggested)
            }
            OutlinedButton(onClick = { showAddOptions = !showAddOptions }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(Size.smallIcon),
                )
                Text(
                    text = stringResource(R.string.onboarding_commitments_add),
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        if (showAddOptions) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("school", "coaching", "lecture", "tuition", "work", "meal", "commute", "other").forEach { kind ->
                    val preset = commitmentKindLabel(kind)
                    Surface(
                        onClick = {
                            haptics.select()
                            onAdd(kind)
                            showAddOptions = false
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(Stroke.hairline, AppTheme.colors.outline),
                    ) {
                        Text(stringResource(preset), modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (state.commitments.isEmpty()) {
            Surface(shape = RoundedCornerShape(Radius.md), border = BorderStroke(Stroke.hairline, AppTheme.colors.border), color = Color.Transparent) {
                Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Filled.EventAvailable, null)
                    Text(stringResource(R.string.onboarding_commitments_empty))
                }
            }
        } else {
            state.commitments.forEach { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    issues = issues.filter { it.commitmentId == commitment.id },
                    onRemove = onRemove,
                    onRename = onRename,
                    onToggleDay = onToggleDay,
                    onStart = onStart,
                    onEnd = onEnd,
                )
            }
        }
        AppearanceLabel(R.string.onboarding_commitments_awake)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TimeField(stringResource(R.string.onboarding_commitments_up_at), state.wakeMinute, onWake, Modifier.weight(1f))
            TimeField(stringResource(R.string.onboarding_commitments_lights_out), state.sleepMinute, onSleep, Modifier.weight(1f))
        }
        if (CommitmentIssue.WakeAfterSleep in issues) {
            InlineCommitmentIssue(CommitmentIssue.WakeAfterSleep)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(Icons.Filled.EventAvailable, null, tint = AppTheme.colors.brand)
            Text(
                stringResource(R.string.onboarding_commitments_plan_around),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CommitmentCard(
    commitment: OnboardingCommitment,
    issues: List<CommitmentIssue>,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleDay: (String, Int) -> Unit,
    onStart: (String, Int) -> Unit,
    onEnd: (String, Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val defaultLabel = stringResource(commitment.labelRes)
    val label = commitment.customLabel ?: defaultLabel
    var showRename by remember(commitment.id) { mutableStateOf(false) }
    var draftLabel by remember(commitment.id) { mutableStateOf(label) }
    val colors = AppTheme.colors
    Surface(shape = RoundedCornerShape(Radius.lg), color = colors.elevated) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    Modifier.size(Size.swatch).clip(RoundedCornerShape(Radius.md)).background(colors.glassTint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Groups, null, tint = colors.brand)
                }
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = colors.text)
                    Text(
                        // Start and end are already in the fields below; the header shows only the length.
                        stringResource(
                            R.string.onboarding_commitments_duration,
                            planHour(((commitment.endMinute - commitment.startMinute + 24 * 60) % (24 * 60)) / 60f),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                }
                // Plain icon buttons; edge-to-edge spacing comes from their 48dp touch targets.
                Row {
                    IconButton(onClick = {
                        draftLabel = label
                        showRename = true
                    }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.onboarding_commitments_rename, label), tint = colors.text)
                    }
                    IconButton(onClick = { onRemove(commitment.id) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.onboarding_commitments_remove, label), tint = colors.text)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TimeField(stringResource(R.string.onboarding_commitments_starts), commitment.startMinute, { onStart(commitment.id, it) }, Modifier.weight(1f))
                TimeField(stringResource(R.string.onboarding_commitments_ends), commitment.endMinute, { onEnd(commitment.id, it) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.onboarding_commitments_repeat_on), style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dayOptions.forEach { (day, labelRes) ->
                        val selected = day in commitment.days
                        val dayName = stringResource(dayNames[day])
                        Surface(
                            onClick = { haptics.select(); onToggleDay(commitment.id, day) },
                            modifier = Modifier
                                .size(Size.dayControl)
                                .semantics {
                                    role = Role.Checkbox
                                    this.selected = selected
                                    contentDescription = dayName
                                },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (selected) null else BorderStroke(Stroke.hairline, colors.text),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.text,
                                )
                            }
                        }
                    }
                }
                issues.forEach { issue -> InlineCommitmentIssue(issue) }
            }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.onboarding_commitments_rename_title)) },
            text = {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it.take(40) },
                    label = { Text(stringResource(R.string.onboarding_commitments_rename_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(commitment.id, draftLabel)
                        showRename = false
                    },
                ) { Text(stringResource(R.string.onboarding_commitments_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.onboarding_commitments_rename_cancel))
                }
            },
        )
    }
}

@Composable
private fun InlineCommitmentIssue(issue: CommitmentIssue) {
    Text(
        text = issueText(issue),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.danger,
    )
}

@Composable
private fun issueText(issue: CommitmentIssue): String = when (issue) {
    CommitmentIssue.WakeAfterSleep -> stringResource(R.string.onboarding_commitments_wake_after_sleep)
    is CommitmentIssue.NoDays -> stringResource(R.string.onboarding_commitments_no_days, stringResource(issue.labelRes))
    is CommitmentIssue.InvalidRange -> stringResource(R.string.onboarding_commitments_invalid_range, stringResource(issue.labelRes))
    is CommitmentIssue.OutsideWakingHours -> stringResource(R.string.onboarding_commitments_outside_waking, stringResource(issue.labelRes))
}

/** M3 outlined, read-only time field. The whole field opens the time picker. */
@Composable
private fun TimeField(label: String, minute: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val colors = AppTheme.colors
    val value = clockLabel(minute)
    val openLabel = stringResource(R.string.onboarding_time_select)
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(Radius.extraSmall),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedBorderColor = colors.text,
                unfocusedBorderColor = colors.text,
                focusedLabelColor = colors.text,
                unfocusedLabelColor = colors.text,
                focusedTrailingIconColor = colors.text,
                unfocusedTrailingIconColor = colors.text,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = Size.textField),
        )
        // Clickable overlay so a tap anywhere on the field (not just the icon) opens the picker.
        Box(
            Modifier
                .matchParentSize()
                .padding(top = Spacing.sm)
                .clip(RoundedCornerShape(Radius.extraSmall))
                .clickable(role = Role.Button, onClickLabel = openLabel) { open = true }
                .semantics { contentDescription = "$label, $value" },
        )
    }
    if (open) MinutePickerDialog(minute, { open = false }, { onChange(it); open = false })
}

/** M3 time picker in a basic dialog: "Select time", dial, Cancel / OK. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinute / 60 % 24, initialMinute % 60, is24Hour = false)
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Radius.extraLarge),
            color = scheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(Spacing.xxl)) {
                Text(
                    text = stringResource(R.string.onboarding_time_select),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.colors.text,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                )
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = scheme.primary,
                        timeSelectorSelectedContainerColor = scheme.primary,
                        timeSelectorSelectedContentColor = scheme.onPrimary,
                        periodSelectorSelectedContainerColor = scheme.primary,
                        periodSelectorSelectedContentColor = scheme.onPrimary,
                        clockDialSelectedContentColor = scheme.onPrimary,
                    ),
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_time_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text(stringResource(R.string.onboarding_time_ok))
                    }
                }
            }
        }
    }
}

/** "Finish the syllabus by": Pick a date is deadline mode (preselected), Not sure yet is steady mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingModeStep(
    state: OnboardingUiState,
    onMode: (PlanMode) -> Unit,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                PlanMode.DEADLINE to R.string.onboarding_mode_yes,
                PlanMode.STEADY to R.string.onboarding_mode_not_yet,
            ).forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = state.planMode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = colors.brandDeep,
                        activeContentColor = colors.onBrand,
                        activeBorderColor = colors.brandDeep,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.text,
                        inactiveBorderColor = colors.outline,
                    ),
                    modifier = Modifier.height(Size.touchTarget),
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (state.planMode == PlanMode.DEADLINE) {
            Spacer(Modifier.height(Spacing.xxxl))
            OnboardingTargetDateStep(state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate)
        } else {
            Spacer(Modifier.height(Spacing.lg))
            ExamDateRow(state.examDate, state.examDateOfficial, state.examDateIsConfigured, onExamDate)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingTargetDateStep(
    targetDate: LocalDate,
    examDate: LocalDate,
    examDateOfficial: Boolean,
    examDateIsConfigured: Boolean,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    val days = ChronoUnit.DAYS.between(today, targetDate).toInt().coerceAtLeast(1)
    val minMillis = today.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val maxMillis = maxOf(examDate, today.plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
        },
    )
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            if (selected != targetDate) onDate(selected)
        }
    }
    Column {
        Text(targetDate.format(formatter), style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.text)
        Text(
            pluralStringResource(R.plurals.onboarding_date_days_to_go, days, days),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.lg))
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            title = null,
            headline = null,
            colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = AppTheme.colors.border)
        Spacer(Modifier.height(Spacing.sm))
        ExamDateRow(examDate, examDateOfficial, examDateIsConfigured, onExamDate)
    }
}

/** The exam date with its honesty label ("expected" until it is official or the student's own), and a way to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDateRow(
    examDate: LocalDate,
    official: Boolean,
    isConfigured: Boolean,
    onExamDate: (LocalDate) -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }
    var editing by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = Size.ctaHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(Icons.Filled.CalendarToday, null, tint = AppTheme.colors.textSecondary)
        Text(
            text = stringResource(
                if (official || !isConfigured) R.string.onboarding_date_exam_official else R.string.onboarding_date_exam_expected,
                examDate.format(formatter),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { editing = true }, modifier = Modifier.heightIn(min = Size.touchTarget)) {
            Text(stringResource(R.string.onboarding_exam_date_change))
        }
    }
    if (editing) {
        val today = LocalDate.now()
        val minMillis = today.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val maxMillis = today.plusYears(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = examDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onExamDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    editing = false
                }) { Text(stringResource(R.string.onboarding_ok)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.onboarding_cancel)) } },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** "Which class / attempt?" A dropper starts with the syllabus marked finished once. */
@Composable
internal fun OnboardingStartStep(
    state: OnboardingUiState,
    onSelectStartingPoint: (StartingPoint) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            Triple(StartingPoint.CLASS_11, R.string.onboarding_start_class_11, R.string.onboarding_start_class_11_detail),
            Triple(StartingPoint.CLASS_12, R.string.onboarding_start_class_12, R.string.onboarding_start_class_12_detail),
            Triple(StartingPoint.DROPPER, R.string.onboarding_start_dropper, R.string.onboarding_start_dropper_detail),
        ).forEach { (point, title, detail) ->
            OnboardingSelectableCard(
                title = stringResource(title),
                selected = state.startingPoint == point,
                onClick = { onSelectStartingPoint(point) },
                showUnselectedIndicator = true,
                trailing = {
                    Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                },
            )
        }
    }
}

@Composable
internal fun OnboardingSyllabusStep(
    state: OnboardingUiState,
    onToggleSubject: (String) -> Unit,
    onToggleChapter: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    var expandedSubjectId by remember(state.examId) { mutableStateOf<String?>(null) }
    val chapterCount = state.syllabusSubjects.sumOf { it.chapters.size }
    val selectedCount = state.selectedChapterIds.size
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (ExamCatalog.hasBundledSyllabus(state.examId)) {
            Text(
                stringResource(R.string.onboarding_syllabus_selected_count, selectedCount, chapterCount),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            state.syllabusSubjects.forEach { subject ->
                val subjectIds = subject.chapters.map { it.id }
                val subjectSelectedCount = subjectIds.count { it in state.selectedChapterIds }
                val allSubjectSelected = subjectIds.isNotEmpty() && subjectSelectedCount == subjectIds.size
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    border = BorderStroke(Stroke.hairline, AppTheme.colors.border),
                    color = AppTheme.colors.elevated,
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.onboarding_syllabus_subject_selected, subjectSelectedCount, subjectIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppTheme.colors.textSecondary,
                                )
                            }
                            TextButton(onClick = { haptics.select(); onToggleSubject(subject.id) }) {
                                Text(stringResource(if (allSubjectSelected) R.string.onboarding_syllabus_clear_all else R.string.onboarding_syllabus_select_all))
                            }
                            IconButton(onClick = {
                                expandedSubjectId = if (expandedSubjectId == subject.id) null else subject.id
                            }) {
                                Icon(
                                    if (expandedSubjectId == subject.id) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(
                                        if (expandedSubjectId == subject.id) R.string.onboarding_syllabus_collapse_subject
                                        else R.string.onboarding_syllabus_expand_subject,
                                        subject.name,
                                    ),
                                )
                            }
                        }
                        if (expandedSubjectId == subject.id) {
                            subject.chapters.groupBy { it.classNumber }.forEach { (classNumber, chapters) ->
                                Text(
                                    text = classNumber?.let { stringResource(R.string.onboarding_syllabus_class, it) }
                                        ?: stringResource(R.string.onboarding_syllabus_other),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                )
                                chapters.forEach { chapter ->
                                    OnboardingSelectableCard(
                                        title = chapter.name,
                                        selected = chapter.id in state.selectedChapterIds,
                                        onClick = { haptics.select(); onToggleChapter(chapter.id) },
                                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                        showUnselectedIndicator = true,
                                    )
                                }
                            }
                            Box(Modifier.height(Spacing.sm))
                        }
                    }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(Radius.md), border = BorderStroke(Stroke.hairline, AppTheme.colors.border), color = Color.Transparent) {
                Text(stringResource(R.string.onboarding_syllabus_blank), modifier = Modifier.padding(Spacing.lg), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun OnboardingHoursParityStep(
    state: OnboardingUiState,
    capacityIssues: List<CapacityIssue>,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
) {
    val weekdayFree = (1..5).minOf { day -> freeMinutesOnDay(day, state) } / 60f
    val weekendFree = listOf(0, 6).minOf { day -> freeMinutesOnDay(day, state) } / 60f
    val weeklyHours = state.weekdayHours * 5 + state.weekendHours * 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Surface(shape = RoundedCornerShape(Radius.md), color = AppTheme.colors.elevated) {
            Column {
                HoursRow(
                    label = stringResource(R.string.onboarding_hours_weekdays),
                    value = state.weekdayHours,
                    options = hourOptions(weekdayFree, MAX_WEEKDAY_HOURS, state.weekdayHours),
                    onChange = onWeekdayChange,
                )
                HorizontalDivider(color = AppTheme.colors.hairline)
                HoursRow(
                    label = stringResource(R.string.onboarding_hours_weekends),
                    value = state.weekendHours,
                    options = hourOptions(weekendFree, MAX_WEEKEND_HOURS, state.weekendHours),
                    onChange = onWeekendChange,
                )
            }
        }
        Surface(shape = RoundedCornerShape(Radius.md), color = AppTheme.colors.elevated) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.onboarding_hours_each_week),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                )
                Text(
                    stringResource(R.string.onboarding_hours_weekly_value, planHour(weeklyHours)),
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.text,
                )
            }
        }
        if (capacityIssues.isNotEmpty()) CapacityIssuesCard(capacityIssues)
    }
}

private const val MAX_WEEKDAY_HOURS = 14f
private const val MAX_WEEKEND_HOURS = 16f

/** Whole-hour choices up to the free time that day allows, plus the current value if it is off-grid. */
private fun hourOptions(freeHours: Float, maximum: Float, current: Float): List<Float> {
    val top = floor(minOf(freeHours, maximum)).toInt().coerceAtLeast(1)
    return ((1..top).map { it.toFloat() } + current).distinct().sorted()
}

@Composable
private fun hoursLabel(hours: Float): String =
    if (hours == 1f) stringResource(R.string.onboarding_hours_value_one)
    else stringResource(R.string.onboarding_hours_value, planHour(hours))

/** One row: label, then the hours; tapping anywhere opens a menu of hour choices. */
@Composable
private fun HoursRow(label: String, value: Float, options: List<Float>, onChange: (Float) -> Unit) {
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.onboardingHoursRow)
            .clickable { open = true }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.text)
        Box {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(hoursLabel(value), style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.text)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = AppTheme.colors.text)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { hours ->
                    DropdownMenuItem(
                        text = { Text(hoursLabel(hours)) },
                        onClick = {
                            open = false
                            haptics.select()
                            onChange(hours)
                        },
                        trailingIcon = if (hours == value) {
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

@Composable
internal fun OnboardingPlanStep(
    state: OnboardingUiState,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onDate: (LocalDate) -> Unit,
    onExamDate: (LocalDate) -> Unit,
    onApplyFitOption: (PlanFitOption) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val shortDatePattern = stringResource(R.string.onboarding_plan_short_date_pattern)
    val dateFormatter = remember(shortDatePattern) { DateTimeFormatter.ofPattern(shortDatePattern, Locale.getDefault()) }
    val targetLabel = state.targetDate.format(dateFormatter)
    val todayLabel = LocalDate.now().format(dateFormatter)
    val fit = state.fit

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxl)) {
        when {
            !state.useProvidedSyllabus -> PlanVerdict(stringResource(R.string.onboarding_plan_verdict_empty))
            state.planMode == PlanMode.STEADY -> {
                PlanVerdict(stringResource(R.string.onboarding_plan_steady))
                when {
                    state.projectionLoading -> CircularProgressIndicator()
                    state.projectedFinish != null -> PlanStatRow(
                        stringResource(R.string.onboarding_plan_projected_finish),
                        stringResource(R.string.onboarding_plan_projected_value, state.projectedFinish.format(dateFormatter)),
                    )
                    else -> Text(
                        stringResource(R.string.onboarding_plan_projected_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            fit == null -> CircularProgressIndicator()
            else -> {
                val estimateLabel = stringResource(
                    if (fit.calibrated) R.string.onboarding_plan_at_your_pace else R.string.onboarding_plan_estimate_label,
                )
                PlanVerdict(
                    if (fit.fits) {
                        stringResource(R.string.onboarding_plan_fits, targetLabel)
                    } else {
                        stringResource(
                            R.string.onboarding_plan_short,
                            planHour(fit.shortByMinutes / 60f),
                            estimateLabel,
                            targetLabel,
                        )
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_learn),
                        stringResource(R.string.onboarding_plan_hours_value, planHour(fit.learnMinutes / 60f)),
                    )
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_practice),
                        stringResource(R.string.onboarding_plan_hours_value, planHour(fit.practiceMinutes / 60f)),
                    )
                    PlanStatRow(
                        stringResource(R.string.onboarding_plan_stat_revision),
                        if (fit.revisionWindowDays > 0) {
                            pluralStringResource(R.plurals.onboarding_plan_revision_days, fit.revisionWindowDays, fit.revisionWindowDays)
                        } else {
                            stringResource(R.string.onboarding_plan_revision_none)
                        },
                    )
                }
                if (!fit.fits) {
                    FitOptions(fit, targetLabel, dateFormatter, state.fitLoading, onApplyFitOption)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlanSummaryRow(
                icon = Icons.Filled.CalendarToday,
                label = stringResource(R.string.onboarding_plan_every_weekday),
                value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekdayHours)),
                editing = editing,
            ) {
                AppStepperControl(
                    value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekdayHours)),
                    decreaseContentDescription = stringResource(R.string.onboarding_hours_decrease, stringResource(R.string.onboarding_hours_weekdays)),
                    increaseContentDescription = stringResource(R.string.onboarding_hours_increase, stringResource(R.string.onboarding_hours_weekdays)),
                    onDecrease = { onWeekdayChange((state.weekdayHours - .5f).coerceAtLeast(1f)) },
                    onIncrease = { onWeekdayChange((state.weekdayHours + .5f).coerceAtMost(14f)) },
                    decreaseEnabled = state.weekdayHours > 1f,
                    increaseEnabled = state.weekdayHours < 14f,
                )
            }
            PlanSummaryRow(
                icon = Icons.Filled.Weekend,
                label = stringResource(R.string.onboarding_plan_every_weekend),
                value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekendHours)),
                editing = editing,
            ) {
                AppStepperControl(
                    value = stringResource(R.string.onboarding_plan_hour_value, planHour(state.weekendHours)),
                    decreaseContentDescription = stringResource(R.string.onboarding_hours_decrease, stringResource(R.string.onboarding_hours_weekends)),
                    increaseContentDescription = stringResource(R.string.onboarding_hours_increase, stringResource(R.string.onboarding_hours_weekends)),
                    onDecrease = { onWeekendChange((state.weekendHours - .5f).coerceAtLeast(1f)) },
                    onIncrease = { onWeekendChange((state.weekendHours + .5f).coerceAtMost(16f)) },
                    decreaseEnabled = state.weekendHours > 1f,
                    increaseEnabled = state.weekendHours < 16f,
                )
            }
            PlanSummaryRow(
                icon = Icons.Filled.Flag,
                label = stringResource(R.string.onboarding_plan_starts_ends, todayLabel),
                value = targetLabel,
                editing = editing,
            ) {
                OnboardingTargetDateStep(state.targetDate, state.examDate, state.examDateOfficial, state.examDateIsConfigured, onDate, onExamDate)
            }
        }
        OutlinedButton(
            onClick = { editing = !editing },
            modifier = Modifier.fillMaxWidth().height(Size.ctaHeight),
            shape = CircleShape,
        ) {
            Icon(if (editing) Icons.Filled.Check else Icons.Filled.Edit, null)
            Text(
                stringResource(if (editing) R.string.onboarding_plan_done_editing else R.string.onboarding_plan_edit),
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        // §11.3: the usage-data disclosure, on the last step before the plan is made.
        Text(stringResource(R.string.onboarding_usage_data_note), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun PlanVerdict(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textSecondary)
}

@Composable
private fun PlanStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
    }
}

/** The four ways out of a plan that is short. Each button applies a choice the planner has already verified. */
@Composable
private fun FitOptions(
    fit: PlanFit,
    targetLabel: String,
    dateFormatter: DateTimeFormatter,
    loading: Boolean,
    onApply: (PlanFitOption) -> Unit,
) {
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(R.string.onboarding_plan_options_title), style = MaterialTheme.typography.titleSmall)
        fit.options.forEach { option ->
            val (title, detail, action) = when (option) {
                is PlanFitOption.AddMinutesPerDay -> Triple(
                    stringResource(R.string.onboarding_plan_option_add, option.minutes),
                    stringResource(R.string.onboarding_plan_option_add_detail, targetLabel),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.ParkChapters -> Triple(
                    pluralStringResource(R.plurals.onboarding_plan_option_park, option.count, option.count),
                    stringResource(R.string.onboarding_plan_option_park_detail, planHour(option.minutesFreed / 60f)),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.MoveFinishBy -> Triple(
                    stringResource(R.string.onboarding_plan_option_move, option.date.format(dateFormatter)),
                    stringResource(R.string.onboarding_plan_option_move_detail),
                    R.string.onboarding_plan_option_apply,
                )
                is PlanFitOption.AcceptCoverage -> Triple(
                    stringResource(R.string.onboarding_plan_option_accept, option.percent),
                    stringResource(R.string.onboarding_plan_option_accept_detail),
                    R.string.onboarding_plan_option_continue,
                )
            }
            Surface(shape = RoundedCornerShape(Radius.md), color = AppTheme.colors.elevated) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.text)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                    }
                    TextButton(onClick = { haptics.confirm(); onApply(option) }, enabled = !loading) { Text(stringResource(action)) }
                }
            }
        }
    }
}

@Composable
private fun PlanSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    editing: Boolean,
    editor: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(Radius.md), color = AppTheme.colors.elevated) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(Size.ctaHeight).padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Icon(icon, null, tint = AppTheme.colors.textSecondary)
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.brand)
            }
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.End,
                ) { editor() }
            }
        }
    }
}

private fun planHour(value: Float): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

private val dayOptions = listOf(
    1 to R.string.onboarding_day_monday,
    2 to R.string.onboarding_day_tuesday,
    3 to R.string.onboarding_day_wednesday,
    4 to R.string.onboarding_day_thursday,
    5 to R.string.onboarding_day_friday,
    6 to R.string.onboarding_day_saturday,
    0 to R.string.onboarding_day_sunday,
)

private val dayNames = listOf(
    R.string.onboarding_day_sunday_full,
    R.string.onboarding_day_monday_full,
    R.string.onboarding_day_tuesday_full,
    R.string.onboarding_day_wednesday_full,
    R.string.onboarding_day_thursday_full,
    R.string.onboarding_day_friday_full,
    R.string.onboarding_day_saturday_full,
)

private fun accentLabel(accent: AccentPalette): Int = when (accent) {
    AccentPalette.Blue -> R.string.onboarding_accent_blue
    AccentPalette.Purple -> R.string.onboarding_accent_purple
    AccentPalette.Green -> R.string.onboarding_accent_green
    AccentPalette.Amber -> R.string.onboarding_accent_amber
    AccentPalette.Rose -> R.string.onboarding_accent_rose
}

private fun backgroundLabel(background: BackgroundAppearance): Int = when (background) {
    BackgroundAppearance.System -> R.string.onboarding_background_system
    BackgroundAppearance.Light -> R.string.onboarding_background_light
    BackgroundAppearance.Dark -> R.string.onboarding_background_dark
    BackgroundAppearance.Grey -> R.string.onboarding_background_grey
    BackgroundAppearance.Slate -> R.string.onboarding_background_slate
}

private fun commitmentKindLabel(kind: String): Int = when (kind) {
    "school" -> R.string.onboarding_commitment_school
    "coaching" -> R.string.onboarding_commitment_coaching
    "lecture" -> R.string.onboarding_commitment_lecture
    "tuition" -> R.string.onboarding_commitment_tuition
    "work" -> R.string.onboarding_commitment_job
    "meal" -> R.string.onboarding_commitment_meal
    "commute" -> R.string.onboarding_commitment_commute
    else -> R.string.onboarding_commitment_other
}

/** 12-hour clock, lowercase am/pm: "4:00 pm". */
@Composable
private fun clockLabel(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60 % 24
    val minute = minuteOfDay % 60
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return stringResource(
        R.string.onboarding_time_value,
        hour12,
        minute,
        stringResource(if (hour < 12) R.string.onboarding_time_am else R.string.onboarding_time_pm),
    )
}
