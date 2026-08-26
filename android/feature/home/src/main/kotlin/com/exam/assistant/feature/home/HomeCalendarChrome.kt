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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.WeekDayStatus
import java.time.LocalDate

@Composable
internal fun HomeCalendarChrome(
    monthTitle: String,
    weekDays: List<WeekDayUi>,
    monthDays: List<WeekDayUi?>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
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
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
            Text(
                text = label,
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
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            days.forEach { day ->
                DayCell(day = day, onSelectDate = onSelectDate, modifier = Modifier.weight(1f))
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
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
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                .height(1.dp)
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onSelectDate(day.date) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (day.selected) colors.brandDeep else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (day.selected) colors.onBrand else colors.text,
            )
        }
        HomeWeekDot(status = day.status)
    }
}

@Composable
private fun HomeWeekDot(status: WeekDayStatus) {
    val colors = AppTheme.colors
    val dotColor = when (status) {
        WeekDayStatus.DONE -> colors.success
        WeekDayStatus.PARTIAL -> colors.warning
        WeekDayStatus.TODAY, WeekDayStatus.PLANNED -> colors.brandSoft
        WeekDayStatus.REST -> colors.elevated
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(dotColor),
    )
}

@Composable
internal fun HomeDayBar(
    selectedIsToday: Boolean,
    selectedDayLabel: String,
) {
    val colors = AppTheme.colors
    Text(
        text = if (selectedIsToday) stringResource(R.string.home_today) else selectedDayLabel,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.md, bottom = 2.dp),
    )
}
