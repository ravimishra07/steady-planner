package com.exam.assistant.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.CalendarMetrics
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.WeekDayStatus
import java.time.LocalDate

@Composable
internal fun HomeCalendarChrome(
    selectedDate: LocalDate,
    weekDays: List<WeekDayUi>,
    monthDays: List<WeekDayUi?>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val monthPattern = stringResource(R.string.home_month_pattern)
    val monthTitle = selectedDate.format(
        java.time.format.DateTimeFormatter.ofPattern(monthPattern, java.util.Locale.getDefault()),
    )
    val monthStateDescription = stringResource(
        if (expanded) R.string.home_calendar_state_expanded else R.string.home_calendar_state_collapsed,
        monthTitle,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = Size.touchTarget)
                .semantics(mergeDescendants = true) {
                    stateDescription = monthStateDescription
                    role = Role.Button
                }
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) R.string.home_calendar_collapse else R.string.home_calendar_expand,
                    ),
                    onClick = onToggleExpanded,
                )
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.home_calendar_collapse else R.string.home_calendar_expand,
                ),
                tint = colors.textSecondary,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            HomeMonthGrid(days = monthDays, onSelectDate = onSelectDate)
        } else {
            HomeWeekStrip(days = weekDays, onSelectDate = onSelectDate)
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(CalendarMetrics.gridGap),
    ) {
        listOf(
            R.string.home_weekday_monday,
            R.string.home_weekday_tuesday,
            R.string.home_weekday_wednesday,
            R.string.home_weekday_thursday,
            R.string.home_weekday_friday,
            R.string.home_weekday_saturday,
            R.string.home_weekday_sunday,
        ).forEach { labelRes ->
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeWeekStrip(
    days: List<WeekDayUi>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    Column {
        WeekdayHeaderRow()
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(CalendarMetrics.gridGap),
        ) {
            days.forEach { day ->
                DayCell(day = day, onSelectDate = onSelectDate, modifier = Modifier.weight(1f))
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(Stroke.hairline)
                .background(colors.hairlineSoft),
        )
    }
}

@Composable
private fun HomeMonthGrid(
    days: List<WeekDayUi?>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val colors = AppTheme.colors
    Column {
        WeekdayHeaderRow()
        Spacer(Modifier.height(Spacing.xs))
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(CalendarMetrics.gridGap),
            ) {
                week.forEach { day ->
                    if (day != null) {
                        DayCell(day = day, onSelectDate = onSelectDate, modifier = Modifier.weight(1f))
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(Stroke.hairline)
                .background(colors.hairlineSoft),
        )
    }
}

@Composable
private fun DayCell(
    day: WeekDayUi,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val datePattern = stringResource(R.string.home_accessibility_date_pattern)
    val fullDate = day.date.format(
        java.time.format.DateTimeFormatter.ofPattern(datePattern, java.util.Locale.getDefault()),
    )
    val status = stringResource(
        when (day.status) {
            WeekDayStatus.DONE -> R.string.home_calendar_status_done
            WeekDayStatus.PARTIAL -> R.string.home_calendar_status_partial
            WeekDayStatus.TODAY -> R.string.home_calendar_status_today
            WeekDayStatus.PLANNED -> R.string.home_calendar_status_planned
            WeekDayStatus.REST -> R.string.home_calendar_status_no_progress
        },
    )
    Column(
        modifier = modifier
            .heightIn(min = Size.touchTarget)
            .clip(RoundedCornerShape(CalendarMetrics.dayCorner))
            .background(if (day.selected) colors.selectionContainer else Color.Transparent)
            .semantics(mergeDescendants = true) {
                contentDescription = fullDate
                stateDescription = status
                selected = day.selected
                role = Role.Button
            }
            .clickable(
                onClickLabel = stringResource(R.string.home_calendar_select_date, fullDate),
                onClick = { onSelectDate(day.date) },
            )
            .padding(top = CalendarMetrics.dayTopPadding, bottom = CalendarMetrics.dayBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CalendarMetrics.dayGap),
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = if (day.selected) colors.onSelectionContainer else colors.text,
        )
        HomeWeekMeter(status = day.status, selected = day.selected)
    }
}

@Composable
private fun HomeWeekMeter(status: WeekDayStatus, selected: Boolean) {
    val colors = AppTheme.colors
    val fillFraction = when (status) {
        WeekDayStatus.DONE -> 1f
        WeekDayStatus.PARTIAL -> .5f
        WeekDayStatus.TODAY, WeekDayStatus.PLANNED -> .2f
        WeekDayStatus.REST -> 0f
    }
    Box(
        modifier = Modifier
            .width(CalendarMetrics.meterWidth)
            .height(CalendarMetrics.meterHeight)
            .clip(RoundedCornerShape(Radius.full))
            .background(colors.surface3),
    ) {
        if (fillFraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fillFraction)
                    .height(CalendarMetrics.meterHeight)
                    .background(if (selected) colors.onSelectionContainer else colors.primary),
            )
        }
    }
}

@Composable
internal fun HomeDayBar(
    selectedIsToday: Boolean,
    selectedDate: LocalDate,
) {
    val colors = AppTheme.colors
    val pattern = stringResource(R.string.home_selected_day_pattern)
    val selectedDayLabel = selectedDate.format(
        java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()),
    )
    Text(
        text = if (selectedIsToday) stringResource(R.string.home_today) else selectedDayLabel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.md, bottom = Spacing.xxs),
    )
}
