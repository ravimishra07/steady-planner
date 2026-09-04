package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Complete Material 3 color roles plus stable semantic aliases used by features. */
@Immutable
data class AppColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val scrim: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
) {
    // Product semantics. These aliases ensure feature code never invents a
    // color while all values still resolve to the measured M3 scheme.
    val bg: Color get() = surface
    val bgDeep: Color get() = surfaceContainerLowest
    val surfaceTinted: Color get() = primaryContainer
    val surfaceCard: Color get() = surfaceContainerLow
    val surfaceControl: Color get() = surfaceContainer
    val surfaceInk: Color get() = surfaceContainerLowest
    val elevated: Color get() = surfaceContainerHigh
    val surface3: Color get() = surfaceContainerHighest
    val border: Color get() = outlineVariant
    val borderSubtle: Color get() = outlineVariant.copy(alpha = 0.62f)
    val hairline: Color get() = outlineVariant.copy(alpha = 0.45f)
    val hairlineSoft: Color get() = outlineVariant.copy(alpha = 0.28f)
    val glassTint: Color get() = onSurface.copy(alpha = 0.06f)
    val glassStroke: Color get() = onSurface.copy(alpha = 0.14f)
    val ctaBorder: Color get() = primary.copy(alpha = 0.32f)
    val text: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val textMuted: Color get() = onSurfaceVariant.copy(alpha = 0.80f)
    val textDisabled: Color get() = onSurface.copy(alpha = 0.38f)
    val tabBg: Color get() = surfaceContainer
    val tabUnselected: Color get() = onSurfaceVariant
    /** Selected rows, chips, and navigation indicators in the Angular M3 reference. */
    val selectionContainer: Color get() = secondaryContainer
    val onSelectionContainer: Color get() = onSecondaryContainer
    val brandContainer: Color get() = primaryContainer
    val successContainer: Color get() = tertiaryContainer
    val dangerContainer: Color get() = errorContainer
    val dangerSoft: Color get() = onErrorContainer
    val dangerStripe: Color get() = error
    val infoTint: Color get() = tertiaryContainer.copy(alpha = 0.55f)
    val warningTint: Color get() = secondaryContainer.copy(alpha = 0.62f)
    val warningRow: Color get() = secondaryContainer.copy(alpha = 0.35f)
    val onSuccess: Color get() = onTertiaryContainer
    val onBrandContainer: Color get() = onPrimaryContainer
    val brand: Color get() = primary
    val brandSoft: Color get() = primaryFixedDim
    val brandDeep: Color get() = primary
    val onBrand: Color get() = onPrimary
    val tabSelected: Color get() = primary
    val success: Color get() = tertiary
    val successStrong: Color get() = tertiary
    val warning: Color get() = secondary
    val danger: Color get() = error
    val info: Color get() = tertiary
    val accentCyan: Color get() = tertiary
}

fun resolveAppColors(
    background: BackgroundAppearance,
    palette: AccentPalette,
    systemDark: Boolean = false,
): AppColors {
    val resolvedBackground = background.resolved(systemDark)
    val neutral = resolvedBackground.neutralRoles
    val accent = if (resolvedBackground.isDark) palette.roles.dark else palette.roles.light
    return AppColors(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        inversePrimary = accent.inversePrimary,
        primaryFixed = accent.primaryFixed,
        primaryFixedDim = accent.primaryFixedDim,
        onPrimaryFixed = accent.onPrimaryFixed,
        onPrimaryFixedVariant = accent.onPrimaryFixedVariant,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer,
        onTertiaryContainer = accent.onTertiaryContainer,
        background = neutral.background,
        onBackground = neutral.onBackground,
        surface = neutral.surface,
        onSurface = neutral.onSurface,
        surfaceVariant = neutral.surfaceVariant,
        onSurfaceVariant = neutral.onSurfaceVariant,
        inverseSurface = neutral.inverseSurface,
        inverseOnSurface = neutral.inverseOnSurface,
        error = neutral.error,
        onError = neutral.onError,
        errorContainer = neutral.errorContainer,
        onErrorContainer = neutral.onErrorContainer,
        outline = neutral.outline,
        outlineVariant = neutral.outlineVariant,
        scrim = neutral.scrim,
        surfaceBright = neutral.surfaceBright,
        surfaceDim = neutral.surfaceDim,
        surfaceContainerLowest = neutral.surfaceContainerLowest,
        surfaceContainerLow = neutral.surfaceContainerLow,
        surfaceContainer = neutral.surfaceContainer,
        surfaceContainerHigh = neutral.surfaceContainerHigh,
        surfaceContainerHighest = neutral.surfaceContainerHighest,
    )
}

internal val defaultAppColors = resolveAppColors(BackgroundAppearance.Default, AccentPalette.Default)
internal val LocalAppColors = staticCompositionLocalOf { defaultAppColors }
