package com.exam.assistant.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.CalendarMetrics
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.core.design.TimelineMetrics
import com.exam.assistant.domain.DayBlock
import com.exam.assistant.domain.DAY_TIMELINE_START
import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.FixedCommitmentBlock
import com.exam.assistant.domain.RevisionSuggestion
import com.exam.assistant.domain.formatGap
import com.exam.assistant.domain.isActionableStudyGap

private val railWidth = TimelineMetrics.railWidth
private val lineWidth = Spacing.xxs
private val nodeSize = Size.legendDot

internal fun formatClock(minuteOfDay: Int): String {
    val clamped = ((minuteOfDay % 1440) + 1440) % 1440
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, clamped / 60)
        set(java.util.Calendar.MINUTE, clamped % 60)
    }
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(calendar.time)
}

@Composable
internal fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return stringResource(R.string.home_duration_min, 0)
    if (minutes < 60) return stringResource(R.string.home_duration_min, minutes)
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) stringResource(R.string.home_duration_hours, h)
    else stringResource(R.string.home_duration_hours_minutes, h, m)
}

private fun formatRemaining(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "%d:%02d".format(mins, secs) else "0:%02d".format(secs)
}

/** One shared dp-per-minute rate for the whole day axis — free time and study blocks read on the same scale. */
private const val AXIS_PX_PER_MINUTE = 1.0f

private fun axisHeight(minutes: Int): androidx.compose.ui.unit.Dp = (minutes * AXIS_PX_PER_MINUTE).dp

/** Duration has spatial meaning on the timeline: a 2h block reads taller than a 30min one. */
private fun studyBlockMinHeight(minutes: Int): androidx.compose.ui.unit.Dp =
    axisHeight(minutes).coerceAtLeast(Size.topAppBarHeight)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TodaySummary(
    entries: List<DayTimelineEntry>,
    plannedMinutes: Int,
    completedMinutes: Int,
    selectedIsToday: Boolean,
    onEditPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    val colors = AppTheme.colors
    val studyBlocks = entries.filterIsInstance<DayTimelineEntry.Study>().map { it.block }
    val fixedBlocks = entries.filterIsInstance<DayTimelineEntry.Fixed>().map { it.block }
    val nowMinute = entries.filterIsInstance<DayTimelineEntry.NowMarker>().firstOrNull()?.minuteOfDay
    val allStarts = studyBlocks.map { it.startMinuteOfDay } + fixedBlocks.map { it.startMinuteOfDay } +
        entries.filterIsInstance<DayTimelineEntry.Gap>().map { it.startMinuteOfDay }
    val allEnds = studyBlocks.map { it.endMinuteOfDay } + fixedBlocks.map { it.endMinuteOfDay } +
        entries.filterIsInstance<DayTimelineEntry.Gap>().map { it.endMinuteOfDay }
    val dayStart = allStarts.minOrNull() ?: DAY_TIMELINE_START
    val dayEnd = allEnds.maxOrNull() ?: com.exam.assistant.domain.DAY_TIMELINE_END
    val daySpan = (dayEnd - dayStart).coerceAtLeast(60)
    val left = (plannedMinutes - completedMinutes).coerceAtLeast(0)
    val headline = when {
        plannedMinutes == 0 -> stringResource(R.string.home_summary_nothing)
        left == 0 -> stringResource(R.string.home_summary_complete)
        else -> stringResource(R.string.home_summary_left, formatDuration(left))
    }
    val fixedMinutes = fixedBlocks.sumOf { it.endMinuteOfDay - it.startMinuteOfDay }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.summary),
        color = colors.surfaceCard,
    ) {
    Column(
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(headline, style = MaterialTheme.typography.titleLarge, color = colors.text)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
            TextButton(onClick = onEditPlan) {
                Icon(Icons.Filled.EditCalendar, contentDescription = null, modifier = Modifier.size(Size.smallIcon))
                Text(stringResource(R.string.home_edit_plan), modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        if (expanded) {
            Canvas(Modifier.fillMaxWidth().height(Size.summaryTrack)) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(colors.surface3, cornerRadius = radius)
                fixedBlocks.forEach { block ->
                    val leftPx = ((block.startMinuteOfDay - dayStart).toFloat() / daySpan) * size.width
                    val widthPx = (((block.endMinuteOfDay - block.startMinuteOfDay).toFloat() / daySpan) * size.width).coerceAtLeast(Spacing.xxs.toPx())
                    drawRoundRect(
                        color = colors.surface3,
                        topLeft = Offset(leftPx, 0f),
                        size = GeometrySize(widthPx.coerceAtMost(size.width - leftPx), size.height),
                        cornerRadius = radius,
                    )
                }
                studyBlocks.forEach { block ->
                    val leftPx = ((block.startMinuteOfDay - dayStart).toFloat() / daySpan) * size.width
                    val widthPx = (((block.endMinuteOfDay - block.startMinuteOfDay).toFloat() / daySpan) * size.width).coerceAtLeast(Spacing.xxs.toPx())
                    val blockSize = GeometrySize(widthPx.coerceAtMost(size.width - leftPx), size.height)
                    if (block.completed) {
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(leftPx, 0f),
                            size = blockSize,
                            cornerRadius = radius,
                        )
                    } else {
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(leftPx, 0f),
                            size = blockSize,
                            cornerRadius = radius,
                            style = DrawStroke(width = Stroke.emphasis.toPx()),
                        )
                    }
                }
                if (selectedIsToday && nowMinute != null && nowMinute in dayStart..dayEnd) {
                    val x = ((nowMinute - dayStart).toFloat() / daySpan) * size.width
                    drawLine(colors.onSurface, Offset(x, -Spacing.xxs.toPx()), Offset(x, size.height + Spacing.xxs.toPx()), Spacing.xxs.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock(dayStart), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                if (selectedIsToday && nowMinute != null && nowMinute in dayStart..dayEnd) {
                    Text(stringResource(R.string.home_summary_now), style = MaterialTheme.typography.labelSmall, color = colors.brand)
                }
                Text(formatClock(dayEnd), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SummaryFact(Icons.Filled.Check, stringResource(R.string.home_summary_logged, formatDuration(completedMinutes), formatDuration(plannedMinutes)))
                if (fixedMinutes > 0) SummaryFact(Icons.Filled.Lock, stringResource(R.string.home_summary_fixed, formatDuration(fixedMinutes)))
            }
        }
    }
    }
}

@Composable
private fun SummaryFact(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = AppTheme.colors.elevated,
    ) {
        Row(
            modifier = Modifier
                .height(Size.compactControl)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Size.compactIcon), tint = AppTheme.colors.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
internal fun DaySummaryHeader(
    plannedMinutes: Int,
    completedMinutes: Int,
    daysUntilExam: Int,
    syllabusPercent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            if (plannedMinutes > 0) {
                Text(
                    text = formatDuration(plannedMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.home_completed_caption, formatDuration(completedMinutes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = TimelineMetrics.microOffset),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = pluralStringResource(R.plurals.home_days_to_exam, daysUntilExam, daysUntilExam),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.home_syllabus_progress, syllabusPercent),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = TimelineMetrics.microOffset),
            )
        }
    }
}

@Composable
private fun DashedVerticalLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dash = TimelineMetrics.dash.toPx()
        val gap = TimelineMetrics.dash.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = color,
                start = Offset(size.width / 2, y),
                end = Offset(size.width / 2, (y + dash).coerceAtMost(size.height)),
                strokeWidth = size.width,
            )
            y += dash + gap
        }
    }
}

@Composable
private fun TimelineRow(
    time: String?,
    lineColor: Color,
    dashed: Boolean,
    showNode: Boolean,
    nodeColor: Color,
    drawLineBelow: Boolean = true,
    minContentHeight: androidx.compose.ui.unit.Dp = Spacing.none,
    contentBottomPadding: androidx.compose.ui.unit.Dp = Spacing.lg,
    rowModifier: Modifier = Modifier,
    contentBlock: @Composable () -> Unit,
) {
    Row(modifier = rowModifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(railWidth)) {
            if (time != null) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = CalendarMetrics.meterHeight),
                )
            }
        }
        Box(
            modifier = Modifier.width(TimelineMetrics.compactRail).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (drawLineBelow) {
                val lineModifier = Modifier
                    .fillMaxHeight()
                    .width(lineWidth)
                    .padding(top = Spacing.sm)
                if (dashed) {
                    DashedVerticalLine(color = lineColor, modifier = lineModifier)
                } else {
                    Box(modifier = lineModifier.background(lineColor))
                }
            }
            if (showNode) {
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .size(nodeSize)
                        .clip(CircleShape)
                        .background(nodeColor),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = minContentHeight)
                .padding(start = Spacing.sm, bottom = contentBottomPadding),
        ) {
            contentBlock()
        }
    }
}

@Composable
private fun StudyBlockContent(
    block: DayBlock,
    isRunning: Boolean,
    isMissed: Boolean,
    remainingLabel: String?,
    expanded: Boolean,
    canStartNow: Boolean,
    onToggleExpand: () -> Unit,
    onStart: () -> Unit,
    onReschedule: () -> Unit,
) {
    val colors = AppTheme.colors
    if (block.completed && !expanded) {
        CompletedBlockSummary(block = block, onClick = onToggleExpand)
        return
    }

    val containerColor = if (isRunning) colors.brandContainer else colors.surfaceCard
    val contentPadding = if (isRunning) Spacing.lg else Spacing.md

    val hasSubtopics = block.subtopics.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = containerColor,
        modifier = Modifier
            .clickable(onClick = onToggleExpand)
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.brandSoft,
                        modifier = Modifier.size(Spacing.ml),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                if (block.isRevision) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = colors.brandSoft,
                        modifier = Modifier.size(TimelineMetrics.tinyIcon),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                if (isMissed) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = stringResource(R.string.home_missed_semantics),
                        tint = colors.warning,
                        modifier = Modifier.size(TimelineMetrics.tinyIcon),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    text = if (block.isRevision) {
                        stringResource(R.string.home_revision_tag).uppercase() + " · " + block.subjectLabel
                    } else {
                        block.subjectLabel
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isMissed) colors.warning else colors.brandSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = Spacing.xxs)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        style = if (isRunning) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs),
                    ) {
                        if (expanded) {
                            Text(
                                text = stringResource(
                                    R.string.home_time_range,
                                    formatClock(block.startMinuteOfDay),
                                    formatClock(block.endMinuteOfDay),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(TimelineMetrics.tinyIcon),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                            text = if (hasSubtopics) {
                                    val topics = if (block.subtopics.size == 1) {
                                        stringResource(R.string.home_topic_count_one)
                                    } else {
                                        pluralStringResource(R.plurals.home_topics_count, block.subtopics.size, block.subtopics.size)
                                    }
                                    stringResource(R.string.home_duration_topics, formatDuration(block.durationMinutes), topics)
                                } else {
                                    formatDuration(block.durationMinutes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (expanded) R.string.home_collapse_details else R.string.home_expand_details,
                            ),
                            tint = colors.textMuted,
                            modifier = Modifier.size(Size.compactIcon),
                        )
                    }
                    val lastStudiedDaysAgo = block.lastStudiedDaysAgo
                    if (block.isRevision && lastStudiedDaysAgo != null) {
                        Text(
                            text = if (lastStudiedDaysAgo == 1) {
                                stringResource(R.string.home_last_studied_day_one)
                            } else {
                                pluralStringResource(R.plurals.home_last_studied_days, lastStudiedDaysAgo, lastStudiedDaysAgo)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                when {
                    block.completed -> Box(
                        modifier = Modifier.size(Size.standardIcon).clip(CircleShape).background(colors.successContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.home_completed_semantics),
                            tint = colors.successStrong,
                            modifier = Modifier.size(Spacing.ml),
                        )
                    }
                    canStartNow -> Button(
                        onClick = onStart,
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandDeep,
                            contentColor = colors.onBrand,
                        ),
                        shape = RoundedCornerShape(Radius.pill),
                    ) {
                        Text(
                            text = when {
                                isMissed -> stringResource(R.string.home_start_now)
                                isRunning -> stringResource(R.string.home_continue_short)
                                else -> stringResource(R.string.home_start_short)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (isRunning && remainingLabel != null) {
                Text(
                    text = stringResource(R.string.home_studying_now, remainingLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.brandSoft,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (block.subtopics.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_details_duration, formatDuration(block.durationMinutes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    block.subtopics.forEach { subtopic ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatClock(subtopic.startMinuteOfDay),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                                modifier = Modifier.width(TimelineMetrics.trailingAction),
                            )
                            Text(
                                text = subtopic.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    if (isMissed) {
                        TextButton(onClick = onReschedule) {
                            Text(stringResource(R.string.home_choose_another_time))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedBlockSummary(block: DayBlock, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = colors.success,
            modifier = Modifier.size(Size.compactIcon),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_subject_title, block.subjectLabel, block.title),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = if (block.subtopics.isNotEmpty()) {
                val topics = if (block.subtopics.size == 1) {
                    stringResource(R.string.home_topic_count_one)
                } else {
                    pluralStringResource(R.plurals.home_topics_count, block.subtopics.size, block.subtopics.size)
                }
                stringResource(R.string.home_completed_summary_topics, formatDuration(block.durationMinutes), topics)
            } else {
                stringResource(R.string.home_completed_summary, formatDuration(block.durationMinutes))
            }
            Text(text = summary, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun GapContent(minutes: Int, actionable: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = colors.surfaceCard,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (actionable) {
                    stringResource(R.string.home_gap_free, formatGap(minutes))
                } else {
                    stringResource(R.string.home_gap_break, formatGap(minutes))
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            if (actionable) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = colors.brandSoft,
                    modifier = Modifier.size(TimelineMetrics.tinyIcon),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_add_something),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.brandSoft,
                )
            }
        }
    }
}

/**
 * A free stretch of the day rendered as continuous hour ruler segments (so the axis
 * exists whether or not anything is scheduled), with one centered "add something" chip
 * for the whole stretch. Tapping anywhere in it opens the add flow prefilled to this range.
 */
@Composable
private fun GapSegment(startMinute: Int, endMinute: Int, onAdd: () -> Unit) {
    val colors = AppTheme.colors
    val actionable = isActionableStudyGap(endMinute - startMinute)
    val breakpoints = remember(startMinute, endMinute) {
        val points = mutableListOf(startMinute)
        var hour = ((startMinute / 60) + 1) * 60
        while (hour < endMinute) {
            points += hour
            hour += 60
        }
        points += endMinute
        points
    }
    Box(
        modifier = (if (actionable) Modifier.clickable(onClick = onAdd) else Modifier)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until breakpoints.size - 1) {
                val segStart = breakpoints[i]
                val segEnd = breakpoints[i + 1]
                val isHourMark = segStart % 60 == 0 && segStart != startMinute
                TimelineRow(
                    time = if (isHourMark) formatClock(segStart) else null,
                    lineColor = colors.hairlineSoft,
                    dashed = true,
                    showNode = false,
                    nodeColor = Color.Transparent,
                    minContentHeight = axisHeight(segEnd - segStart),
                    contentBottomPadding = Spacing.none,
                ) {}
            }
        }
        GapContent(
            minutes = endMinute - startMinute,
            actionable = actionable,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun NowMarkerContent() {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs)) {
        Text(
            text = stringResource(R.string.home_now_marker).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.brandSoft,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Spacing.xxs)
                .background(colors.brandSoft),
        )
    }
}

@Composable
private fun EmptyDayTimeline(onPlanMyDay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    TimelineRow(
        time = null,
        lineColor = colors.hairlineSoft,
        dashed = true,
        showNode = false,
        nodeColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxxl),
        ) {
            Text(
                text = stringResource(R.string.home_empty_today_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = onPlanMyDay,
                colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
                shape = RoundedCornerShape(Radius.lg),
            ) {
                Text(stringResource(R.string.home_plan_my_day))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DayTimelineSection(
    entries: List<DayTimelineEntry>,
    activeSprint: ActiveSprintUi?,
    revisionItems: List<RevisionSuggestion>,
    onStartScheduled: (String) -> Unit,
    onStartMissed: (String) -> Unit,
    onStartAutoRevision: (RevisionSuggestion) -> Unit,
    onRequestReschedule: (DayBlock) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenAddInGap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val hasAnyBlock = entries.any { it is DayTimelineEntry.Study || it is DayTimelineEntry.Fixed }
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }
    val nowMinute = entries.filterIsInstance<DayTimelineEntry.NowMarker>().firstOrNull()?.minuteOfDay
    val nowRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(nowMinute != null) {
        if (nowMinute != null) nowRequester.bringIntoView()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!hasAnyBlock) {
            EmptyDayTimeline(onPlanMyDay = onOpenAdd)
            return@Column
        }
        entries.forEach { entry ->
            when (entry) {
                is DayTimelineEntry.Study -> {
                    val block = entry.block
                    val isRunning = activeSprint?.sessionId == block.id
                    val expandedNow = expandedIds[block.id] == true
                    val isMissed = !block.completed && !isRunning &&
                        nowMinute != null && block.endMinuteOfDay <= nowMinute
                    TimelineRow(
                        time = formatClock(block.startMinuteOfDay),
                        lineColor = when {
                            block.completed -> colors.success
                            isRunning -> colors.brandSoft
                            isMissed -> colors.warning
                            else -> colors.border
                        },
                        dashed = false,
                        showNode = true,
                        nodeColor = when {
                            block.completed -> colors.success
                            isRunning -> colors.brandSoft
                            isMissed -> colors.warning
                            else -> colors.border
                        },
                        minContentHeight = when {
                            block.completed && !expandedNow -> Spacing.none
                            block.subtopics.isNotEmpty() && !expandedNow -> Spacing.none
                            else -> studyBlockMinHeight(block.durationMinutes)
                        },
                    ) {
                        StudyBlockContent(
                            block = block,
                            isRunning = isRunning,
                            isMissed = isMissed,
                            remainingLabel = if (isRunning) formatRemaining(activeSprint?.remainingSec ?: 0) else null,
                            expanded = expandedNow,
                            canStartNow = nowMinute != null,
                            onToggleExpand = { expandedIds[block.id] = !(expandedIds[block.id] ?: false) },
                            onStart = {
                                if (isMissed) {
                                    onStartMissed(block.id)
                                } else if (block.id.startsWith(AUTO_REVISION_ID_PREFIX)) {
                                    val nodeKey = block.id.removePrefix(AUTO_REVISION_ID_PREFIX)
                                    revisionItems.firstOrNull { it.nodeKey == nodeKey }?.let(onStartAutoRevision)
                                } else {
                                    onStartScheduled(block.id)
                                }
                            },
                            onReschedule = { onRequestReschedule(block) },
                        )
                    }
                }
                is DayTimelineEntry.Fixed -> {
                    val block = entry.block
                    TimelineRow(
                        time = formatClock(block.startMinuteOfDay),
                        lineColor = colors.border,
                        dashed = false,
                        showNode = true,
                        nodeColor = colors.textSecondary,
                        minContentHeight = studyBlockMinHeight(block.endMinuteOfDay - block.startMinuteOfDay),
                    ) {
                        FixedCommitmentContent(block)
                    }
                }
                is DayTimelineEntry.Gap -> {
                    GapSegment(
                        startMinute = entry.startMinuteOfDay,
                        endMinute = entry.endMinuteOfDay,
                        onAdd = { onOpenAddInGap(entry.startMinuteOfDay, entry.endMinuteOfDay) },
                    )
                }
                is DayTimelineEntry.NowMarker -> {
                    TimelineRow(
                        time = formatClock(entry.minuteOfDay),
                        lineColor = colors.brandSoft,
                        dashed = false,
                        showNode = true,
                        nodeColor = colors.brandSoft,
                        rowModifier = Modifier.bringIntoViewRequester(nowRequester),
                    ) {
                        NowMarkerContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedCommitmentContent(block: FixedCommitmentBlock) {
    Surface(shape = RoundedCornerShape(Radius.lg), color = AppTheme.colors.elevated) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(R.string.home_fixed_tag).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = block.customLabel ?: fixedCommitmentLabel(block.kind),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.text,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.home_time_range,
                    formatClock(block.startMinuteOfDay),
                    formatClock(block.endMinuteOfDay),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun fixedCommitmentLabel(kind: String): String = stringResource(
    when (kind) {
        "school" -> R.string.home_fixed_school
        "coaching" -> R.string.home_fixed_coaching
        "lecture" -> R.string.home_fixed_lecture
        "tuition" -> R.string.home_fixed_tuition
        "work" -> R.string.home_fixed_work
        "commute" -> R.string.home_fixed_commute
        "other" -> R.string.home_fixed_other
        else -> R.string.home_fixed_meal
    },
)

private const val AUTO_REVISION_ID_PREFIX = "auto-revision-"
