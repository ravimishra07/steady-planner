package com.exam.assistant.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** What the user picked. Distinct from what is currently on screen. */
enum class ThemeChoice { System, Light, Dark }

@Composable
fun SteadylineTheme(
    choice: ThemeChoice = ThemeChoice.System,
    palette: AccentPalette = AccentPalette.Default,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val colors = resolveAppColors(dark, palette)

    // Material's scheme is filled from the same values so stock components
    // cannot drift from the palette.
    // Light brandContainer is pale lavender — brandSoft fails contrast on it; brandDeep passes.
    val onBrandContainer = colors.onBrandContainer

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.brandDeep,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandContainer,
            onPrimaryContainer = onBrandContainer,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            onError = colors.onBrand,
            outline = colors.border,
            outlineVariant = colors.hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.brandDeep,
            onPrimary = colors.onBrand,
            primaryContainer = colors.brandContainer,
            onPrimaryContainer = onBrandContainer,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            onError = colors.onBrand,
            outline = colors.border,
            outlineVariant = colors.hairline,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = appTypography, content = content)
    }
}

/** `AppTheme.colors.surfaceTinted` — the only way UI code reads a colour. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
