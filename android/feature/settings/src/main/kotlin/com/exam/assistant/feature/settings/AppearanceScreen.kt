package com.exam.assistant.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppShapes
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.core.design.resolveAppColors

/** Native, inset-safe counterpart of the prototype's dedicated Appearance page. */
@Composable
fun AppearanceScreen(
    background: BackgroundAppearance,
    onBackground: (BackgroundAppearance) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val systemDark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.topAppBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = colors.text,
                )
            }
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }

        AppearanceSectionTitle(stringResource(R.string.settings_theme_title))
        Surface(
            shape = AppShapes.card,
            color = colors.surfaceCard,
            tonalElevation = com.exam.assistant.core.design.Elevation.cardTonal,
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            Column {
                BackgroundAppearance.entries.forEachIndexed { index, option ->
                    BackgroundAppearanceRow(
                        option = option,
                        selected = option == background,
                        accentPalette = accentPalette,
                        systemDark = systemDark,
                        onClick = { onBackground(option) },
                    )
                    if (index != BackgroundAppearance.entries.lastIndex) {
                        HorizontalDivider(color = colors.borderSubtle)
                    }
                }
            }
        }

        AppearanceSectionTitle(stringResource(R.string.settings_theme_color))
        Surface(
            shape = AppShapes.card,
            color = colors.surfaceCard,
            tonalElevation = com.exam.assistant.core.design.Elevation.cardTonal,
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccentPalette.entries.forEach { palette ->
                    AccentChoice(
                        palette = palette,
                        selected = palette == accentPalette,
                        onClick = { onAccentPalette(palette) },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.settings_theme_description),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xxl),
        )
    }
}

@Composable
private fun AppearanceSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.text,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
    )
}

@Composable
private fun BackgroundAppearanceRow(
    option: BackgroundAppearance,
    selected: Boolean,
    accentPalette: AccentPalette,
    systemDark: Boolean,
    onClick: () -> Unit,
) {
    val current = AppTheme.colors
    val preview = resolveAppColors(option, accentPalette, systemDark)
    val label = stringResource(option.labelRes())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.appearanceRow)
            .background(if (selected) current.selectionContainer else current.surfaceCard)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Size.appearanceModeSwatch)
                .clip(RoundedCornerShape(Radius.sm))
                .background(preview.bg)
                .border(Stroke.hairline, preview.border, RoundedCornerShape(Radius.sm)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) current.onSelectionContainer else current.text,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = current.brandDeep,
            )
        } else {
            Spacer(Modifier.size(Size.standardIcon))
        }
    }
}

@Composable
private fun AccentChoice(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val label = stringResource(palette.labelRes())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Size.themeSwatch)
            .clip(CircleShape)
            .background(palette.brand)
            .border(
                width = if (selected) Stroke.selected else Stroke.hairline,
                color = if (selected) colors.text else colors.border,
                shape = CircleShape,
            )
            .semantics { contentDescription = label }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onBrand,
            )
        }
    }
}

@StringRes
private fun AccentPalette.labelRes(): Int = when (this) {
    AccentPalette.Blue -> R.string.settings_palette_blue
    AccentPalette.Purple -> R.string.settings_palette_purple
    AccentPalette.Green -> R.string.settings_palette_green
    AccentPalette.Amber -> R.string.settings_palette_amber
    AccentPalette.Rose -> R.string.settings_palette_rose
}

@StringRes
private fun BackgroundAppearance.labelRes(): Int = when (this) {
    BackgroundAppearance.System -> R.string.settings_theme_system
    BackgroundAppearance.Light -> R.string.settings_theme_light
    BackgroundAppearance.Dark -> R.string.settings_theme_dark
    BackgroundAppearance.Grey -> R.string.settings_background_grey
    BackgroundAppearance.Slate -> R.string.settings_background_slate
}
