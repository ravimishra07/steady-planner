package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The app-wide surface treatment selected by the user. */
enum class BackgroundAppearance(val id: String) {
    Light("light"), Dark("dark"), Grey("grey"), Slate("slate");

    val isDark: Boolean get() = this == Dark || this == Slate

    companion object {
        val Default = Dark
        fun fromId(id: String?): BackgroundAppearance = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Centralized semantic surface values, kept separate from selectable accent hues. */
@Immutable
internal data class SurfaceHues(
    val bg: Color, val bgDeep: Color, val surface: Color, val surfaceCard: Color, val surfaceControl: Color,
    val surfaceInk: Color, val elevated: Color, val surface3: Color, val border: Color, val borderSubtle: Color,
    val hairline: Color, val hairlineSoft: Color, val glassTint: Color, val glassStroke: Color, val tabBg: Color,
    val tabUnselected: Color, val text: Color, val textSecondary: Color, val textMuted: Color, val textDisabled: Color,
    val successContainer: Color, val dangerContainer: Color, val dangerSoft: Color, val dangerStripe: Color,
    val warningTint: Color, val warningRow: Color, val onSuccess: Color,
)

internal val BackgroundAppearance.surfaces: SurfaceHues
    get() = when (this) {
        BackgroundAppearance.Light -> lightSurfaces
        BackgroundAppearance.Dark -> darkSurfaces
        BackgroundAppearance.Grey -> greySurfaces
        BackgroundAppearance.Slate -> slateSurfaces
    }

private val darkSurfaces = SurfaceHues(
    Color(0xFF0A0A0F), Color(0xFF080511), Color(0xFF1C1C24), Color(0xFF1E1E28), Color(0xFF1A1B22), Color(0xFF121216), Color(0xFF24242E), Color(0xFF2A2A36), Color(0xFF3A3A46), Color(0xFF252530), Color(0x14FFFFFF), Color(0x0FFFFFFF), Color(0x0EFFFFFF), Color(0x24FFFFFF), Color(0xF20A0A0F), Color(0x8FFFFFFF), Color(0xFFF4F3F8), Color(0xC7FFFFFF), Color(0x94FFFFFF), Color(0x61FFFFFF), Color(0xFF1B2D1B), Color(0xFF3A1F24), Color(0xFFFFB4BE), Color(0xFFDC2626), Color(0x1FFFB74D), Color(0x0FFFB74D), Color(0xFF0A0A0F),
)
private val lightSurfaces = SurfaceHues(
    Color(0xFFFCFCFF), Color(0xFFF4F4F8), Color(0xFFF7F7FB), Color(0xFFFFFFFF), Color(0xFFECECF2), Color(0xFFF4F4F8), Color(0xFFE7E7EF), Color(0xFFDEDEE8), Color(0xFFC6C6D1), Color(0xFFE0E0E8), Color(0x14000000), Color(0x0F000000), Color(0x0A000000), Color(0x1A000000), Color(0xF7FCFCFF), Color(0x61191C25), Color(0xFF191C25), Color(0xFF444651), Color(0xFF5D5F6A), Color(0xFF838590), Color(0xFFE6F6EB), Color(0xFFFFEAEC), Color(0xFFB11B35), Color(0xFFDC2626), Color(0x33FFB74D), Color(0x1AFFB74D), Color(0xFF0A0A0F),
)
private val greySurfaces = SurfaceHues(
    Color(0xFFF3F4F6), Color(0xFFE9EAEE), Color(0xFFFAFAFC), Color(0xFFFFFFFF), Color(0xFFE5E7EB), Color(0xFFEFF0F3), Color(0xFFDEE0E5), Color(0xFFD3D5DB), Color(0xFFBEC1C8), Color(0xFFDDE0E5), Color(0x14000000), Color(0x0F000000), Color(0x0A000000), Color(0x1A000000), Color(0xF7F3F4F6), Color(0x61404448), Color(0xFF1B1C20), Color(0xFF47484E), Color(0xFF62636B), Color(0xFF878991), Color(0xFFE6F5EA), Color(0xFFFFEAEC), Color(0xFFB11B35), Color(0xFFDC2626), Color(0x33FFB74D), Color(0x1AFFB74D), Color(0xFF0A0A0F),
)
private val slateSurfaces = SurfaceHues(
    Color(0xFF111820), Color(0xFF0C1218), Color(0xFF1A232D), Color(0xFF202B36), Color(0xFF18212A), Color(0xFF141C24), Color(0xFF293542), Color(0xFF34414F), Color(0xFF45515F), Color(0xFF2A3540), Color(0x14FFFFFF), Color(0x0FFFFFFF), Color(0x0EFFFFFF), Color(0x24FFFFFF), Color(0xF2111820), Color(0x8FFFFFFF), Color(0xFFF0F4F8), Color(0xC7FFFFFF), Color(0x94FFFFFF), Color(0x61FFFFFF), Color(0xFF1B342B), Color(0xFF3B222A), Color(0xFFFFB4BE), Color(0xFFDC2626), Color(0x1FFFB74D), Color(0x0FFFB74D), Color(0xFF0C1218),
)
