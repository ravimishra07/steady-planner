package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors shared by every feature. UI code must consume these rather than raw colors. */
@Immutable
data class AppColors(
    val bg: Color, val bgDeep: Color, val surface: Color, val surfaceTinted: Color, val surfaceCard: Color,
    val surfaceControl: Color, val surfaceInk: Color, val elevated: Color, val surface3: Color,
    val border: Color, val borderSubtle: Color, val hairline: Color, val hairlineSoft: Color,
    val glassTint: Color, val glassStroke: Color, val ctaBorder: Color,
    val text: Color, val textSecondary: Color, val textMuted: Color, val textDisabled: Color,
    val tabBg: Color, val tabUnselected: Color,
    val brandContainer: Color, val successContainer: Color, val dangerContainer: Color, val dangerSoft: Color,
    val dangerStripe: Color, val infoTint: Color, val warningTint: Color, val warningRow: Color, val onSuccess: Color,
    val onBrandContainer: Color, val brand: Color, val brandSoft: Color, val brandDeep: Color,
    val onBrand: Color, val tabSelected: Color,
) {
    val success: Color get() = SharedHues.success
    val successStrong: Color get() = SharedHues.successStrong
    val warning: Color get() = SharedHues.warning
    val danger: Color get() = SharedHues.danger
    val info: Color get() = SharedHues.info
    val accentCyan: Color get() = SharedHues.accentCyan
}

fun resolveAppColors(background: BackgroundAppearance, palette: AccentPalette): AppColors {
    val surface = background.surfaces
    val accent = palette.accents
    return AppColors(
        bg = surface.bg, bgDeep = surface.bgDeep, surface = surface.surface,
        surfaceTinted = if (background.isDark) accent.darkContainer else accent.lightContainer,
        surfaceCard = surface.surfaceCard, surfaceControl = surface.surfaceControl, surfaceInk = surface.surfaceInk,
        elevated = surface.elevated, surface3 = surface.surface3, border = surface.border, borderSubtle = surface.borderSubtle,
        hairline = surface.hairline, hairlineSoft = surface.hairlineSoft, glassTint = surface.glassTint,
        glassStroke = surface.glassStroke, ctaBorder = accent.brand.copy(alpha = if (background.isDark) .42f else .26f),
        text = surface.text, textSecondary = surface.textSecondary, textMuted = surface.textMuted, textDisabled = surface.textDisabled,
        tabBg = surface.tabBg, tabUnselected = surface.tabUnselected,
        brandContainer = if (background.isDark) accent.darkContainer else accent.lightContainer,
        successContainer = surface.successContainer, dangerContainer = surface.dangerContainer, dangerSoft = surface.dangerSoft,
        dangerStripe = surface.dangerStripe, infoTint = accent.brand.copy(alpha = if (background.isDark) .18f else .10f),
        warningTint = surface.warningTint, warningRow = surface.warningRow, onSuccess = surface.onSuccess,
        onBrandContainer = if (background.isDark) accent.brandSoft else accent.brandDeep,
        brand = accent.brand, brandSoft = accent.brandSoft, brandDeep = accent.brandDeep,
        onBrand = SharedHues.onBrand, tabSelected = accent.brandDeep,
    )
}

internal val defaultAppColors = resolveAppColors(BackgroundAppearance.Default, AccentPalette.Default)
internal val LocalAppColors = staticCompositionLocalOf { defaultAppColors }
