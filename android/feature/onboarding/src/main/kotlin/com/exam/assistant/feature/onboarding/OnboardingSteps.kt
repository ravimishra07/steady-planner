package com.exam.assistant.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppType
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.Cushion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val ExamDateFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.Builder().setLanguage("en").setRegion("IN").build())

@Composable
internal fun OnboardingDateStep(
    daysUntilExam: Int,
    onUnannouncedDate: () -> Unit,
) {
    val colors = AppTheme.colors
    val weeks = daysUntilExam / 7
    val remainder = daysUntilExam % 7
    val examDate = formatExamDate(daysUntilExam)
    val scaleFilled = max(1, ((daysUntilExam / 180.0) * 12).roundToInt().coerceAtMost(12))

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surfaceTinted,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(modifier = Modifier.padding(Spacing.xxl)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = daysUntilExam.toString(),
                        style = AppType.countdown,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_date_days_left),
                        style = AppType.callout,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
                    )
                }
                Text(
                    text = stringResource(R.string.onboarding_date_weeks_format, weeks, remainder),
                    style = AppType.callout,
                    color = colors.textSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(12) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .padding(0.dp),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                shape = RoundedCornerShape(2.dp),
                                color = if (index < scaleFilled) colors.brandDeep else colors.elevated,
                            ) {}
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_date_today),
                        style = AppType.micro,
                        color = colors.textMuted,
                    )
                    Text(
                        text = examDate,
                        style = AppType.micro,
                        color = colors.textMuted,
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = examDate, style = AppType.headline, color = colors.text)
                Text(text = "›", style = AppType.subtitle, color = colors.textMuted)
            }
        }
        TextButton(
            onClick = onUnannouncedDate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.onboarding_date_not_announced),
                style = AppType.callout,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
internal fun OnboardingShapeStep(
    selectedWorkId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DAY_SHAPES.forEach { shape ->
            val selected = shape.id == selectedWorkId
            OnboardingSelectableCard(
                title = stringResource(shape.labelRes),
                selected = selected,
                onClick = { onSelect(shape.id) },
                trailing = {
                    OnboardingHourPills(
                        weekdayHours = shape.weekdayHours,
                        weekendHours = shape.weekendHours,
                        selected = selected,
                    )
                },
            )
        }
    }
}

@Composable
internal fun OnboardingHoursStep(
    weekdayHours: Float,
    weekendHours: Float,
    studyPlace: String,
    totalHours: Int,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onStudyPlaceChange: (String) -> Unit,
) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HoursSliderCard(
            label = stringResource(R.string.onboarding_hours_weekdays),
            value = weekdayHours,
            valueRange = 1f..14f,
            steps = 25,
            tickStart = stringResource(R.string.onboarding_hours_slider_min),
            tickMid = stringResource(R.string.onboarding_hours_slider_mid_weekday),
            tickEnd = stringResource(R.string.onboarding_hours_slider_max_weekday),
            onValueChange = onWeekdayChange,
        )
        HoursSliderCard(
            label = stringResource(R.string.onboarding_hours_weekends),
            value = weekendHours,
            valueRange = 1f..16f,
            steps = 29,
            tickStart = stringResource(R.string.onboarding_hours_slider_min),
            tickMid = stringResource(R.string.onboarding_hours_slider_mid_weekend),
            tickEnd = stringResource(R.string.onboarding_hours_slider_max_weekend),
            onValueChange = onWeekendChange,
        )
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.brandContainer,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = totalHours.toString(),
                    style = AppType.xxl,
                    color = colors.onBrandContainer,
                )
                Text(
                    text = stringResource(R.string.onboarding_hours_total_label),
                    style = AppType.sub,
                    color = colors.textSecondary,
                )
            }
        }
        OutlinedTextField(
            value = studyPlace,
            onValueChange = onStudyPlaceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_hours_study_spot)) },
            placeholder = { Text(stringResource(R.string.onboarding_hours_study_spot_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.lg),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.brandDeep,
                unfocusedBorderColor = colors.border,
                focusedLabelColor = colors.brandDeep,
                unfocusedLabelColor = colors.textMuted,
                cursorColor = colors.brandDeep,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
        )
    }
}

@Composable
private fun HoursSliderCard(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    tickStart: String,
    tickMid: String,
    tickEnd: String,
    onValueChange: (Float) -> Unit,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = AppType.callout, color = colors.text)
                Text(
                    text = stringResource(R.string.onboarding_hours_value, formatHour(value)),
                    style = AppType.subtitle,
                    color = colors.brand,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.padding(top = Spacing.sm),
                colors = SliderDefaults.colors(
                    thumbColor = colors.brand,
                    activeTrackColor = colors.brandDeep,
                    inactiveTrackColor = colors.elevated,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = tickStart, style = AppType.micro, color = colors.textMuted)
                Text(text = tickMid, style = AppType.micro, color = colors.textMuted)
                Text(text = tickEnd, style = AppType.micro, color = colors.textMuted)
            }
        }
    }
}

@Composable
internal fun OnboardingCushionStep(
    state: OnboardingUiState,
    cushion: Cushion,
) {
    val colors = AppTheme.colors
    val gapColor = if (cushion.isShort) colors.danger else colors.success
    val examDate = formatExamDate(state.daysUntilExam)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_plan_coverage, cushion.coverage, examDate),
            style = AppType.md,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = kotlin.math.abs(cushion.gap).toString(),
                style = AppType.hero,
                color = gapColor,
            )
            Text(
                text = stringResource(
                    if (cushion.isShort) R.string.onboarding_plan_hours_short
                    else R.string.onboarding_plan_hours_spare,
                ),
                style = AppType.lg,
                color = gapColor,
                modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs),
            )
        }
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_plan_hours_label),
                        style = AppType.eyebrow,
                        color = colors.textMuted,
                    )
                    Text(
                        text = stringResource(
                            R.string.onboarding_plan_hours_ratio,
                            cushion.have,
                            cushion.need,
                        ),
                        style = AppType.callout,
                        color = colors.text,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md)
                        .height(46.dp)
                        .clip(RoundedCornerShape(Radius.sm)),
                ) {
                    val covered = cushion.coverage.coerceIn(1, 100).toFloat()
                    val shortfall = (100 - cushion.coverage).coerceIn(1, 100).toFloat()
                    Box(
                        modifier = Modifier
                            .weight(covered)
                            .fillMaxHeight(),
                    ) {
                        Surface(color = colors.brandDeep, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.onboarding_plan_hours_have, cushion.have),
                                style = AppType.sm,
                                color = colors.onBrand,
                                modifier = Modifier.padding(start = Spacing.md, top = Spacing.md),
                            )
                        }
                    }
                    if (cushion.isShort) {
                        Box(
                            modifier = Modifier
                                .weight(shortfall)
                                .fillMaxHeight(),
                        ) {
                            Surface(color = colors.danger, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = stringResource(
                                        R.string.onboarding_plan_hours_gap,
                                        cushion.gap,
                                    ),
                                    style = AppType.sm,
                                    color = colors.onBrand,
                                    modifier = Modifier.padding(start = Spacing.sm, top = Spacing.md),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(
                if (cushion.isShort) R.string.onboarding_plan_close_gap
                else R.string.onboarding_plan_buffer,
            ),
            style = AppType.eyebrow,
            color = colors.textMuted,
        )
        if (cushion.isShort) {
            OnboardingFixCard(
                metric = "+${cushion.extraPerDay}",
                title = stringResource(R.string.onboarding_plan_fix_more_hours),
                subtitle = stringResource(
                    R.string.onboarding_plan_fix_more_hours_sub,
                    formatHour((state.weekdayHours + cushion.extraPerDay).toFloat()),
                ),
            )
            OnboardingFixCard(
                metric = "-${cushion.topicsToDrop}",
                title = stringResource(R.string.onboarding_plan_fix_drop_topics),
                subtitle = stringResource(R.string.onboarding_plan_fix_drop_topics_sub),
            )
            OnboardingFixCard(
                metric = "${cushion.daysToPush}d",
                title = stringResource(R.string.onboarding_plan_fix_later_date),
                subtitle = stringResource(R.string.onboarding_plan_fix_later_date_sub),
            )
        } else {
            OnboardingFixCard(
                metric = "2x",
                title = stringResource(R.string.onboarding_plan_fix_revision),
                subtitle = stringResource(R.string.onboarding_plan_fix_revision_sub),
            )
            OnboardingFixCard(
                metric = "${cushion.bufferDays}d",
                title = stringResource(R.string.onboarding_plan_fix_buffer_days),
                subtitle = stringResource(R.string.onboarding_plan_fix_buffer_days_sub),
            )
        }
    }
}

@Composable
private fun OnboardingFixCard(
    metric: String,
    title: String,
    subtitle: String,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = metric,
                style = AppType.headline,
                color = colors.onBrandContainer,
                modifier = Modifier.padding(end = Spacing.md),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = AppType.callout, color = colors.text)
                Text(text = subtitle, style = AppType.sub, color = colors.textMuted)
            }
            Text(text = "›", style = AppType.subtitle, color = colors.textMuted)
        }
    }
}

private fun formatExamDate(daysFromNow: Int): String =
    LocalDate.now().plusDays(daysFromNow.toLong()).format(ExamDateFormatter)

private fun formatHour(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
