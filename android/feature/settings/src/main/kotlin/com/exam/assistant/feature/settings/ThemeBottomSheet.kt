package com.exam.assistant.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.resolveAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeBottomSheet(
    background: BackgroundAppearance,
    onBackground: (BackgroundAppearance) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            Text("Theme", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose an accent and background. Changes apply right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            ThemeSectionTitle("Accent color")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AccentPalette.entries.forEach { palette ->
                    AccentSwatch(
                        palette = palette,
                        selected = palette == accentPalette,
                        onClick = { onAccentPalette(palette) },
                    )
                }
            }
            ThemeSectionTitle("Background")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                BackgroundAppearance.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { option ->
                            BackgroundOption(
                                option = option,
                                palette = accentPalette,
                                selected = option == background,
                                onClick = { onBackground(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.text,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
    )
}

@Composable
private fun AccentSwatch(palette: AccentPalette, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val label = palette.label()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Size.themeSwatch)
            .clip(CircleShape)
            .background(palette.brand)
            .border(if (selected) Spacing.xs else Spacing.xs / 2, if (selected) colors.text else colors.border, CircleShape)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = label
            }
            .clickable(onClick = onClick),
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = colors.onBrand)
    }
}

@Composable
private fun BackgroundOption(
    option: BackgroundAppearance,
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = AppTheme.colors
    val preview = resolveAppColors(option, palette)
    Surface(
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = option.label()
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) current.brandContainer else current.surface,
        border = androidx.compose.foundation.BorderStroke(if (selected) Spacing.xs / 2 else Spacing.xs / 4, if (selected) current.brandDeep else current.border),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.themePreview)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(preview.bg)
                    .border(Spacing.xs / 4, preview.border, RoundedCornerShape(Radius.sm)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.sm)
                        .width(Size.touchTarget)
                        .height(Spacing.sm)
                        .clip(CircleShape)
                        .background(preview.brandDeep),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.sm)
                        .fillMaxWidth()
                        .height(Spacing.xl)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(preview.surfaceCard),
                )
            }
            Text(
                option.label(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) current.brandDeep else current.text,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

private fun AccentPalette.label(): String = when (this) {
    AccentPalette.Blue -> "Blue"
    AccentPalette.Purple -> "Purple"
    AccentPalette.Green -> "Green"
    AccentPalette.Amber -> "Amber"
    AccentPalette.Rose -> "Rose"
}

private fun BackgroundAppearance.label(): String = when (this) {
    BackgroundAppearance.Light -> "Light"
    BackgroundAppearance.Dark -> "Dark"
    BackgroundAppearance.Grey -> "Grey"
    BackgroundAppearance.Slate -> "Slate"
}
