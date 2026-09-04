package com.exam.assistant.core.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppCardTone {
    Standard,
    Raised,
}

/**
 * The app's default content container. Ordinary cards are borderless: hierarchy
 * comes from surface contrast, spacing and grouping rather than coloured edges.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    tone: AppCardTone = AppCardTone.Standard,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val isRaised = tone == AppCardTone.Raised
    val container = if (isRaised) colors.elevated else colors.surfaceCard
    val tonalElevation = if (isRaised) Elevation.raisedTonal else Elevation.cardTonal
    val shadowElevation = if (isRaised) Elevation.raisedShadow else Elevation.cardShadow
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = AppShapes.card,
            color = container,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier,
            onClick = onClick,
            shape = AppShapes.card,
            color = container,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
        ) { Column(content = content) }
    }
}

/** A grouped Settings/Profile list. Use [AppListDivider] between related rows. */
@Composable
fun AppListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier, content = content)
}

@Composable
fun AppListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = AppTheme.colors.borderSubtle)
}

/** Shared Settings/Profile row, with one predictable touch target and hierarchy. */
@Composable
fun AppListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        color = colors.surfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = AppShapes.iconTile, color = colors.surfaceControl) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.brandDeep,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
            }
            trailing?.invoke() ?: Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
            )
        }
    }
}

/** Precise, accessible control for discrete values such as half-hour study budgets. */
@Composable
fun AppValueStepper(
    label: String,
    value: String,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.textSecondary,
        )
        AppStepperControl(
            value = value,
            decreaseContentDescription = decreaseContentDescription,
            increaseContentDescription = increaseContentDescription,
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            decreaseEnabled = decreaseEnabled,
            increaseEnabled = increaseEnabled,
        )
    }
}

@Composable
fun AppStepperControl(
    value: String,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.pill),
        color = colors.surfaceControl,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrease,
                enabled = decreaseEnabled,
                modifier = Modifier.size(Size.touchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = decreaseContentDescription,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }
            Text(
                text = value,
                modifier = Modifier.widthIn(min = Size.touchTarget),
                style = MaterialTheme.typography.titleMedium,
                color = colors.brandDeep,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(
                onClick = onIncrease,
                enabled = increaseEnabled,
                modifier = Modifier.size(Size.touchTarget),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = increaseContentDescription,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }
        }
    }
}

/** One shape vocabulary for reusable surfaces. Only feature-specific controls add their own shape. */
object AppShapes {
    val card = androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg)
    val iconTile = androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm)
}
