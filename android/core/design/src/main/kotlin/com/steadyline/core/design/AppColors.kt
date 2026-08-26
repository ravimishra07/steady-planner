package com.steadyline.core.design

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
) {
    // Shared across both themes.
    val brand: Color get() = SharedHues.brand
    val brandSoft: Color get() = SharedHues.brandSoft
    val brandDeep: Color get() = SharedHues.brandDeep
    val success: Color get() = SharedHues.success
    val successStrong: Color get() = SharedHues.successStrong
    val warning: Color get() = SharedHues.warning
    val danger: Color get() = SharedHues.danger
    val info: Color get() = SharedHues.info
    val accentCyan: Color get() = SharedHues.accentCyan
    val onBrand: Color get() = SharedHues.onBrand
    val tabSelected: Color get() = SharedHues.tabSelected
}

internal val darkAppColors = AppColors(
    bg = DarkPalette.bg,
    bgDeep = DarkPalette.bgDeep,
    surface = DarkPalette.surface,
    surfaceTinted = DarkPalette.surfaceTinted,
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
    ctaBorder = DarkPalette.ctaBorder,
    text = DarkPalette.text,
    textSecondary = DarkPalette.textSecondary,
    textMuted = DarkPalette.textMuted,
    textDisabled = DarkPalette.textDisabled,
    tabBg = DarkPalette.tabBg,
    tabUnselected = DarkPalette.tabUnselected,
    brandContainer = DarkPalette.brandContainer,
    successContainer = DarkPalette.successContainer,
    dangerContainer = DarkPalette.dangerContainer,
    dangerSoft = DarkPalette.dangerSoft,
    dangerStripe = DarkPalette.dangerStripe,
    infoTint = DarkPalette.infoTint,
    warningTint = DarkPalette.warningTint,
    warningRow = DarkPalette.warningRow,
    onSuccess = DarkPalette.onSuccess,
)

internal val lightAppColors = AppColors(
    bg = LightPalette.bg,
    bgDeep = LightPalette.bgDeep,
    surface = LightPalette.surface,
    surfaceTinted = LightPalette.surfaceTinted,
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
    ctaBorder = LightPalette.ctaBorder,
    text = LightPalette.text,
    textSecondary = LightPalette.textSecondary,
    textMuted = LightPalette.textMuted,
    textDisabled = LightPalette.textDisabled,
    tabBg = LightPalette.tabBg,
    tabUnselected = LightPalette.tabUnselected,
    brandContainer = LightPalette.brandContainer,
    successContainer = LightPalette.successContainer,
    dangerContainer = LightPalette.dangerContainer,
    dangerSoft = LightPalette.dangerSoft,
    dangerStripe = LightPalette.dangerStripe,
    infoTint = LightPalette.infoTint,
    warningTint = LightPalette.warningTint,
    warningRow = LightPalette.warningRow,
    onSuccess = LightPalette.onSuccess,
)

/**
 * Static rather than dynamic: the palette swaps wholesale on a theme change, so
 * Compose does not need to track reads of it individually.
 */
internal val LocalAppColors = staticCompositionLocalOf { darkAppColors }
