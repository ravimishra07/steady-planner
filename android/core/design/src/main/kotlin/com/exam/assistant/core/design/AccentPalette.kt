package com.exam.assistant.core.design

import androidx.compose.ui.graphics.Color

/**
 * Accent colour presets for the app shell.
 *
 * Each palette is a monochromatic trio (brand / soft / deep) plus tinted surfaces
 * tuned for WCAG-friendly contrast in both light and dark mode — the pattern
 * productivity apps (Notion, Linear, etc.) use instead of rainbow accents.
 */
enum class AccentPalette(val id: String) {
    Violet("violet"),
    Indigo("indigo"),
    Teal("teal"),
    Ocean("ocean"),
    Forest("forest"),
    Sunset("sunset"),
    Rose("rose"),
    ;

    val brand: Color
        get() = accents.brand

    val brandSoft: Color
        get() = accents.brandSoft

    val brandDeep: Color
        get() = accents.brandDeep

    internal val accents: AccentHues
        get() = when (this) {
            Violet -> AccentHues(
                brand = Color(0xFF7C3AED),
                brandSoft = Color(0xFFB098FB),
                brandDeep = Color(0xFF6D35C8),
                lightBrandContainer = Color(0xFFEDE7FF),
                darkBrandContainer = Color(0xFF1A1530),
                lightSurfaceTinted = Color(0xFFEDE7FF),
                darkSurfaceTinted = Color(0xFF151519),
                lightInfoTint = Color(0x1A6D35C8),
                darkInfoTint = Color(0x248B95D6),
                lightCtaBorder = Color(0x336D35C8),
                darkCtaBorder = Color(0x3DB098FB),
            )
            Indigo -> AccentHues(
                brand = Color(0xFF4F46E5),
                brandSoft = Color(0xFFA5B4FC),
                brandDeep = Color(0xFF4338CA),
                lightBrandContainer = Color(0xFFEEF2FF),
                darkBrandContainer = Color(0xFF151A30),
                lightSurfaceTinted = Color(0xFFEEF2FF),
                darkSurfaceTinted = Color(0xFF12141C),
                lightInfoTint = Color(0x1A4338CA),
                darkInfoTint = Color(0x24A5B4FC),
                lightCtaBorder = Color(0x334338CA),
                darkCtaBorder = Color(0x3DA5B4FC),
            )
            Teal -> AccentHues(
                brand = Color(0xFF0D9488),
                brandSoft = Color(0xFF5EEAD4),
                brandDeep = Color(0xFF0F766E),
                lightBrandContainer = Color(0xFFCCFBF1),
                darkBrandContainer = Color(0xFF0F1F1E),
                lightSurfaceTinted = Color(0xFFCCFBF1),
                darkSurfaceTinted = Color(0xFF101816),
                lightInfoTint = Color(0x1A0F766E),
                darkInfoTint = Color(0x245EEAD4),
                lightCtaBorder = Color(0x330F766E),
                darkCtaBorder = Color(0x3D5EEAD4),
            )
            Ocean -> AccentHues(
                brand = Color(0xFF2563EB),
                brandSoft = Color(0xFF93C5FD),
                brandDeep = Color(0xFF1D4ED8),
                lightBrandContainer = Color(0xFFDBEAFE),
                darkBrandContainer = Color(0xFF121A2E),
                lightSurfaceTinted = Color(0xFFDBEAFE),
                darkSurfaceTinted = Color(0xFF10141C),
                lightInfoTint = Color(0x1A1D4ED8),
                darkInfoTint = Color(0x2493C5FD),
                lightCtaBorder = Color(0x331D4ED8),
                darkCtaBorder = Color(0x3D93C5FD),
            )
            Forest -> AccentHues(
                brand = Color(0xFF059669),
                brandSoft = Color(0xFF6EE7B7),
                brandDeep = Color(0xFF047857),
                lightBrandContainer = Color(0xFFD1FAE5),
                darkBrandContainer = Color(0xFF0F1F18),
                lightSurfaceTinted = Color(0xFFD1FAE5),
                darkSurfaceTinted = Color(0xFF101614),
                lightInfoTint = Color(0x1A047857),
                darkInfoTint = Color(0x246EE7B7),
                lightCtaBorder = Color(0x33047857),
                darkCtaBorder = Color(0x3D6EE7B7),
            )
            Sunset -> AccentHues(
                brand = Color(0xFFEA580C),
                brandSoft = Color(0xFFFDBA74),
                brandDeep = Color(0xFFC2410C),
                lightBrandContainer = Color(0xFFFFEDD5),
                darkBrandContainer = Color(0xFF221510),
                lightSurfaceTinted = Color(0xFFFFEDD5),
                darkSurfaceTinted = Color(0xFF181210),
                lightInfoTint = Color(0x1AC2410C),
                darkInfoTint = Color(0x24FDBA74),
                lightCtaBorder = Color(0x33C2410C),
                darkCtaBorder = Color(0x3DFDBA74),
            )
            Rose -> AccentHues(
                brand = Color(0xFFE11D48),
                brandSoft = Color(0xFFFDA4AF),
                brandDeep = Color(0xFFBE123C),
                lightBrandContainer = Color(0xFFFFE4E6),
                darkBrandContainer = Color(0xFF221018),
                lightSurfaceTinted = Color(0xFFFFE4E6),
                darkSurfaceTinted = Color(0xFF181014),
                lightInfoTint = Color(0x1ABE123C),
                darkInfoTint = Color(0x24FDA4AF),
                lightCtaBorder = Color(0x33BE123C),
                darkCtaBorder = Color(0x3DFDA4AF),
            )
        }

    companion object {
        val Default = Violet

        fun fromId(id: String?): AccentPalette =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

internal data class AccentHues(
    val brand: Color,
    val brandSoft: Color,
    val brandDeep: Color,
    val lightBrandContainer: Color,
    val darkBrandContainer: Color,
    val lightSurfaceTinted: Color,
    val darkSurfaceTinted: Color,
    val lightInfoTint: Color,
    val darkInfoTint: Color,
    val lightCtaBorder: Color,
    val darkCtaBorder: Color,
)
