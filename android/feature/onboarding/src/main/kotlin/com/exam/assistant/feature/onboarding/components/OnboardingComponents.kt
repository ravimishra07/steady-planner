package com.exam.assistant.feature.onboarding.components

import androidx.compose.foundation.BorderStroke
import com.exam.assistant.core.design.Stroke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppType
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.rememberHaptics
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.feature.onboarding.R

@Composable
internal fun OnboardingSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    showUnselectedIndicator: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interactive = enabled
    val background = when {
        !interactive -> colors.surfaceControl
        else -> colors.elevated
    }
    val titleColor = when {
        !interactive -> colors.textDisabled
        else -> colors.text
    }
    Surface(
        onClick = {
            haptics.select()
            onClick()
        },
        enabled = interactive,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Size.ctaHeight),
        shape = RoundedCornerShape(Radius.md),
        color = background,
        border = selectionBorder(selected && interactive),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = titleStyle, color = titleColor)
                trailing?.invoke()
            }
            if (selected || showUnselectedIndicator) {
                OnboardingSelectionIndicator(selected = selected && interactive, enabled = interactive)
            }
        }
    }
}

@Composable
internal fun OnboardingSelectionIndicator(
    selected: Boolean,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    when {
        selected -> Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Size.selectionIndicator),
        )
        enabled -> Icon(
            imageVector = Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(Size.selectionIndicator),
        )
        else -> Icon(
            imageVector = Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = colors.textDisabled,
            modifier = Modifier.size(Size.selectionIndicator),
        )
    }
}

@Composable
internal fun OnboardingHourPills(
    weekdayHours: Float,
    weekendHours: Float,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val pillBackground = colors.elevated
    val pillText = if (selected) colors.text else colors.textSecondary
    Row(
        modifier = modifier.padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        HourPill(
            text = stringResource(R.string.onboarding_shape_weekday_hours, formatHour(weekdayHours)),
            background = pillBackground,
            textColor = pillText,
        )
        HourPill(
            text = stringResource(R.string.onboarding_shape_weekend_hours, formatHour(weekendHours)),
            background = pillBackground,
            textColor = pillText,
        )
    }
}

@Composable
private fun HourPill(
    text: String,
    background: Color,
    textColor: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(
            text = text,
            style = AppType.micro,
            color = textColor,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

private fun formatHour(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

/** Selected = accent outline on the normal surface. Never a tinted fill with same-hue text. */
@Composable
internal fun selectionBorder(selected: Boolean): BorderStroke? =
    if (selected) BorderStroke(Stroke.selected, MaterialTheme.colorScheme.primary) else null
