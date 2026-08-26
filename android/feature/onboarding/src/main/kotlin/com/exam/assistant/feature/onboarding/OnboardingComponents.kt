package com.exam.assistant.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppType
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing

@Composable
internal fun OnboardingSelectableCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interactive = enabled
    val background = when {
        !interactive -> colors.surface
        selected -> colors.brandContainer
        else -> colors.surface
    }
    val borderColor = when {
        !interactive -> colors.border
        selected -> colors.brandDeep
        else -> colors.border
    }
    val titleColor = when {
        !interactive -> colors.textDisabled
        else -> colors.text
    }
    Surface(
        onClick = onClick,
        enabled = interactive,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = background,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = titleStyle, color = titleColor)
                trailing?.invoke()
            }
            OnboardingSelectionIndicator(selected = selected && interactive, enabled = interactive)
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
        selected -> Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = colors.brandDeep,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onBrand,
                modifier = Modifier.padding(3.dp),
            )
        }
        enabled -> Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.5.dp, colors.border),
        ) {}
        else -> Surface(
            modifier = Modifier.size(22.dp),
            shape = CircleShape,
            color = colors.surface,
            border = BorderStroke(1.5.dp, colors.borderSubtle),
        ) {}
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
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(
            text = text,
            style = AppType.micro,
            color = textColor,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
        )
    }
}

private fun formatHour(value: Float): String =
    if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
