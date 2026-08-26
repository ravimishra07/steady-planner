package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour Material's ColorScheme has no slot for.
 *
 * Without this the surface tints, hairlines and semantic containers end up
 * inlined in widgets, and a theme change stops reaching them.
 *
 * `@Immutable` matters: it lets Compose skip recomposition of anything that
 * only reads colours.
 */
@Immutable
data class AppColors(
    // surfaces
    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surfaceTinted: Color,
    val surfaceCard: Color,
    val surfaceControl: Color,
    val surfaceInk: Color,
    val elevated: Color,
    val surface3: Color,
    // lines
    val border: Color,
    val borderSubtle: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val glassTint: Color,
    val glassStroke: Color,
    val ctaBorder: Color,
    // text
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    // tabs
    val tabBg: Color,
    val tabUnselected: Color,
    // semantic containers
    val brandContainer: Color,
    val successContainer: Color,
    val dangerContainer: Color,
    val dangerSoft: Color,
    val dangerStripe: Color,
    val infoTint: Color,
    val warningTint: Color,
    val warningRow: Color,
    val onSuccess: Color,
    /** Foreground on [brandContainer]. Dark: brandSoft; light: brandDeep. */
    val onBrandContainer: Color,
    // accent — swapped per [AccentPalette]
    val brand: Color,
    val brandSoft: Color,
    val brandDeep: Color,
    val onBrand: Color,
    val tabSelected: Color,
) {
    val success: Color get() = SharedHues.success
    val successStrong: Color get() = SharedHues.successStrong
    val warning: Color get() = SharedHues.warning
    val danger: Color get() = SharedHues.danger
    val info: Color get() = SharedHues.info
    val accentCyan: Color get() = SharedHues.accentCyan
}

private fun baseDarkAppColors(palette: AccentPalette): AppColors {
    val accent = palette.accents
    return AppColors(
        bg = DarkPalette.bg,
        bgDeep = DarkPalette.bgDeep,
        surface = DarkPalette.surface,
        surfaceTinted = accent.darkSurfaceTinted,
        surfaceCard = DarkPalette.surfaceCard,
        surfaceControl = DarkPalette.surfaceControl,
        surfaceInk = DarkPalette.surfaceInk,
        elevated = DarkPalette.elevated,
        surface3 = DarkPalette.surface3,
        border = DarkPalette.border,
        borderSubtle = DarkPalette.borderSubtle,
        hairline = DarkPalette.hairline,
        hairlineSoft = DarkPalette.hairlineSoft,
        glassTint = DarkPalette.glassTint,
        glassStroke = DarkPalette.glassStroke,
        ctaBorder = accent.darkCtaBorder,
        text = DarkPalette.text,
        textSecondary = DarkPalette.textSecondary,
        textMuted = DarkPalette.textMuted,
        textDisabled = DarkPalette.textDisabled,
        tabBg = DarkPalette.tabBg,
        tabUnselected = DarkPalette.tabUnselected,
        brandContainer = accent.darkBrandContainer,
        successContainer = DarkPalette.successContainer,
        dangerContainer = DarkPalette.dangerContainer,
        dangerSoft = DarkPalette.dangerSoft,
        dangerStripe = DarkPalette.dangerStripe,
        infoTint = accent.darkInfoTint,
        warningTint = DarkPalette.warningTint,
        warningRow = DarkPalette.warningRow,
        onSuccess = DarkPalette.onSuccess,
        onBrandContainer = accent.brandSoft,
        brand = accent.brand,
        brandSoft = accent.brandSoft,
        brandDeep = accent.brandDeep,
        onBrand = SharedHues.onBrand,
        tabSelected = accent.brandDeep,
    )
}

private fun baseLightAppColors(palette: AccentPalette): AppColors {
    val accent = palette.accents
    return AppColors(
        bg = LightPalette.bg,
        bgDeep = LightPalette.bgDeep,
        surface = LightPalette.surface,
        surfaceTinted = accent.lightSurfaceTinted,
        surfaceCard = LightPalette.surfaceCard,
        surfaceControl = LightPalette.surfaceControl,
        surfaceInk = LightPalette.surfaceInk,
        elevated = LightPalette.elevated,
        surface3 = LightPalette.surface3,
        border = LightPalette.border,
        borderSubtle = LightPalette.borderSubtle,
        hairline = LightPalette.hairline,
        hairlineSoft = LightPalette.hairlineSoft,
        glassTint = LightPalette.glassTint,
        glassStroke = LightPalette.glassStroke,
        ctaBorder = accent.lightCtaBorder,
        text = LightPalette.text,
        textSecondary = LightPalette.textSecondary,
        textMuted = LightPalette.textMuted,
        textDisabled = LightPalette.textDisabled,
        tabBg = LightPalette.tabBg,
        tabUnselected = LightPalette.tabUnselected,
        brandContainer = accent.lightBrandContainer,
        successContainer = LightPalette.successContainer,
        dangerContainer = LightPalette.dangerContainer,
        dangerSoft = LightPalette.dangerSoft,
        dangerStripe = LightPalette.dangerStripe,
        infoTint = accent.lightInfoTint,
        warningTint = LightPalette.warningTint,
        warningRow = LightPalette.warningRow,
        onSuccess = LightPalette.onSuccess,
        onBrandContainer = accent.brandDeep,
        brand = accent.brand,
        brandSoft = accent.brandSoft,
        brandDeep = accent.brandDeep,
        onBrand = SharedHues.onBrand,
        tabSelected = accent.brandDeep,
    )
}

/** Resolves the full palette for the current mode + accent choice. */
fun resolveAppColors(isDark: Boolean, palette: AccentPalette): AppColors =
    if (isDark) baseDarkAppColors(palette) else baseLightAppColors(palette)

internal val darkAppColors = baseDarkAppColors(AccentPalette.Violet)

internal val lightAppColors = baseLightAppColors(AccentPalette.Violet)

/**
 * Static rather than dynamic: the palette swaps wholesale on a theme change, so
 * Compose does not need to track reads of it individually.
 */
internal val LocalAppColors = staticCompositionLocalOf { darkAppColors }
