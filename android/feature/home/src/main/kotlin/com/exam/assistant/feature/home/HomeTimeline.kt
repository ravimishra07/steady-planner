package com.exam.assistant.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.DayBlock
import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.RevisionSuggestion
import com.exam.assistant.domain.formatGap
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockPattern = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val railWidth = 52.dp
private val lineWidth = 2.dp
private val nodeSize = 10.dp

internal fun formatClock(minuteOfDay: Int): String {
    val clamped = ((minuteOfDay % 1440) + 1440) % 1440
    return LocalTime.of(clamped / 60, clamped % 60).format(clockPattern)
}

internal fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
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
    axisHeight(minutes).coerceAtLeast(64.dp)

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
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.home_days_to_exam, daysUntilExam),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.home_syllabus_progress, syllabusPercent),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun DashedVerticalLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val dash = 5.dp.toPx()
        val gap = 5.dp.toPx()
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
    minContentHeight: androidx.compose.ui.unit.Dp = 0.dp,
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
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Box(
            modifier = Modifier.width(20.dp).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (drawLineBelow) {
                val lineModifier = Modifier
                    .fillMaxHeight()
                    .width(lineWidth)
                    .padding(top = 8.dp)
                if (dashed) {
                    DashedVerticalLine(color = lineColor, modifier = lineModifier)
                } else {
                    Box(modifier = lineModifier.background(lineColor))
                }
            }
            if (showNode) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
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

    val canExpand = block.subtopics.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = containerColor,
        modifier = (if (canExpand || block.completed) Modifier.clickable(onClick = onToggleExpand) else Modifier)
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.brandSoft,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (block.isRevision) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = null,
                        tint = colors.brandSoft,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (isMissed) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
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
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 2.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        style = if (isRunning) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (expanded && canExpand) {
                            Text(
                                text = "${formatClock(block.startMinuteOfDay)} – ${formatClock(block.endMinuteOfDay)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (canExpand) {
                                    val topics = if (block.subtopics.size == 1) {
                                        stringResource(R.string.home_topic_count_one)
                                    } else {
                                        stringResource(R.string.home_topics_count, block.subtopics.size)
                                    }
                                    "${formatDuration(block.durationMinutes)} · $topics"
                                } else {
                                    formatDuration(block.durationMinutes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary,
                            )
                        }
                        if (canExpand) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    val lastStudiedDaysAgo = block.lastStudiedDaysAgo
                    if (block.isRevision && lastStudiedDaysAgo != null) {
                        Text(
                            text = if (lastStudiedDaysAgo == 1) {
                                stringResource(R.string.home_last_studied_day_one)
                            } else {
                                stringResource(R.string.home_last_studied_days, lastStudiedDaysAgo)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                when {
                    block.completed -> Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.successContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.successStrong,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    else -> Button(
                        onClick = if (isMissed) onReschedule else onStart,
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandDeep,
                            contentColor = colors.onBrand,
                        ),
                        shape = RoundedCornerShape(Radius.pill),
                    ) {
                        Text(
                            text = when {
                                isMissed -> stringResource(R.string.home_reschedule)
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (canExpand && expanded) {
                Column(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    block.subtopics.forEach { subtopic ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatClock(subtopic.startMinuteOfDay),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                                modifier = Modifier.width(68.dp),
                            )
                            Text(
                                text = subtopic.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
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
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${block.subjectLabel} · ${block.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = if (block.subtopics.isNotEmpty()) {
                val topics = if (block.subtopics.size == 1) {
                    stringResource(R.string.home_topic_count_one)
                } else {
                    stringResource(R.string.home_topics_count, block.subtopics.size)
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
private fun GapContent(minutes: Int, modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.home_gap_free, formatGap(minutes)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = colors.brandSoft,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.home_add_something),
                style = MaterialTheme.typography.labelMedium,
                color = colors.brandSoft,
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd),
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
                    contentBottomPadding = 0.dp,
                ) {}
            }
        }
        GapContent(
            minutes = endMinute - startMinute,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun NowMarkerContent() {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
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
                .height(2.dp)
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
    onStartAutoRevision: (RevisionSuggestion) -> Unit,
    onRequestReschedule: (DayBlock) -> Unit,
    onOpenAdd: () -> Unit,
    onOpenAddInGap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val hasAnyBlock = entries.any { it is DayTimelineEntry.Study }
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
                            block.completed && !expandedNow -> 0.dp
                            block.subtopics.isNotEmpty() && !expandedNow -> 0.dp
                            else -> studyBlockMinHeight(block.durationMinutes)
                        },
                    ) {
                        StudyBlockContent(
                            block = block,
                            isRunning = isRunning,
                            isMissed = isMissed,
                            remainingLabel = if (isRunning) formatRemaining(activeSprint?.remainingSec ?: 0) else null,
                            expanded = expandedNow,
                            onToggleExpand = { expandedIds[block.id] = !(expandedIds[block.id] ?: false) },
                            onStart = {
                                if (block.id.startsWith(AUTO_REVISION_ID_PREFIX)) {
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

private const val AUTO_REVISION_ID_PREFIX = "auto-revision-"
