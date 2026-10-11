package com.exam.assistant.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun SteadylineTheme(
    background: BackgroundAppearance = BackgroundAppearance.Default,
    palette: AccentPalette = AccentPalette.Default,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val resolvedBackground = background.resolved(systemDark)
    val colors = resolveAppColors(resolvedBackground, palette, systemDark)
    val scheme = colors.toMaterialColorScheme()

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography,
            shapes = MaterialTheme.shapes.copy(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radius.extraSmall),
                small = androidx.compose.foundation.shape.RoundedCornerShape(Radius.small),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(Radius.medium),
                large = androidx.compose.foundation.shape.RoundedCornerShape(Radius.large),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radius.extraLarge),
            ),
            content = content,
        )
    }
}

private fun AppColors.toMaterialColorScheme(): ColorScheme {
    // Every role is supplied explicitly; the light/dark factory distinction
    // only controls defaults, so it cannot alter this measured scheme.
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}

/** `AppTheme.colors.surfaceContainer` is the semantic color entry point. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
