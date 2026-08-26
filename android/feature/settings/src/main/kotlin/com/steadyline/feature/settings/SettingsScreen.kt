package com.steadyline.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.steadyline.core.design.AppTheme
import com.steadyline.core.design.AppType
import com.steadyline.core.design.Radius
import com.steadyline.core.design.Size
import com.steadyline.core.design.Spacing
import com.steadyline.core.design.ThemeChoice

/**
 * Phase 0: a working theme switch and a swatch strip.
 *
 * The point is to make the theme layer visible — every colour here comes from
 * AppTheme, so flipping the choice repaints all of it. The real Settings screen
 * arrives in its own phase.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    choice: ThemeChoice = ThemeChoice.System,
    onChoose: (ThemeChoice) -> Unit = {},
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text("More", style = AppType.title, color = colors.text)
        Text(
            "Phase 0 — the shell, the theme and navigation. Screens land one phase at a time.",
            style = AppType.md,
            color = colors.textSecondary,
        )

        Text("THEME", style = AppType.eyebrow, color = colors.textMuted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(colors.surfaceControl)
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ThemeChoice.entries.forEach { option ->
                val selected = option == choice
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = Size.touchTarget)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (selected) colors.brandDeep else colors.surfaceControl)
                        .clickable { onChoose(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.name,
                        style = AppType.callout,
                        color = if (selected) colors.onBrand else colors.textSecondary,
                    )
                }
            }
        }

        Text("PALETTE", style = AppType.eyebrow, color = colors.textMuted)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(
                "surface" to colors.surface,
                "elevated" to colors.elevated,
                "brand" to colors.brand,
                "success" to colors.success,
                "warning" to colors.warning,
                "danger" to colors.danger,
            ).forEach { (name, colour) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        Modifier
                            .heightIn(min = Spacing.xxl)
                            .weight(0.2f)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colour)
                            .border(0.45.dp, colors.hairline, RoundedCornerShape(Radius.sm)),
                    )
                    Text(name, style = AppType.sub, color = colors.textSecondary, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}
