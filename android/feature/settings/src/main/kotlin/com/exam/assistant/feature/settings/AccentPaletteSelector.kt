package com.exam.assistant.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPaletteSelector(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AccentPalette.entries.forEach { palette ->
            val isSelected = palette == selected
            val label = paletteLabel(palette)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = label
                    }
                    .clickable { onSelect(palette) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) colors.brandDeep else colors.border,
                            shape = CircleShape,
                        )
                        .padding(4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.brandSoft, palette.brandDeep),
                                ),
                            ),
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.onBrand,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.brandDeep else colors.textMuted,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun paletteLabel(palette: AccentPalette): String = when (palette) {
    AccentPalette.Violet -> stringResource(R.string.settings_palette_violet)
    AccentPalette.Indigo -> stringResource(R.string.settings_palette_indigo)
    AccentPalette.Teal -> stringResource(R.string.settings_palette_teal)
    AccentPalette.Ocean -> stringResource(R.string.settings_palette_ocean)
    AccentPalette.Forest -> stringResource(R.string.settings_palette_forest)
    AccentPalette.Sunset -> stringResource(R.string.settings_palette_sunset)
    AccentPalette.Rose -> stringResource(R.string.settings_palette_rose)
}
