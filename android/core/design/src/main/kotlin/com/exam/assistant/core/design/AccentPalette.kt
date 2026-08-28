package com.exam.assistant.core.design

import androidx.compose.ui.graphics.Color

/** Five restrained, contrast-safe accent palettes for interactive UI. */
enum class AccentPalette(val id: String) {
    Blue("blue"), Purple("purple"), Green("green"), Amber("amber"), Rose("rose");

    val brand: Color get() = accents.brand
    val brandSoft: Color get() = accents.brandSoft
    val brandDeep: Color get() = accents.brandDeep

    internal val accents: AccentHues
        get() = when (this) {
            Blue -> AccentHues(Color(0xFF2563EB), Color(0xFF93C5FD), Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF122042))
            Purple -> AccentHues(Color(0xFF7C3AED), Color(0xFFC4B5FD), Color(0xFF6331C4), Color(0xFFEDE7FF), Color(0xFF24153F))
            Green -> AccentHues(Color(0xFF059669), Color(0xFF86E3BB), Color(0xFF087A56), Color(0xFFD8F5E6), Color(0xFF102A20))
            Amber -> AccentHues(Color(0xFFEA580C), Color(0xFFFDBA74), Color(0xFFC2410C), Color(0xFFFFE8D1), Color(0xFF341A0E))
            Rose -> AccentHues(Color(0xFFE11D48), Color(0xFFFDA4AF), Color(0xFFBE123C), Color(0xFFFFE3E7), Color(0xFF35121F))
        }

    companion object {
        val Default = Purple
        fun fromId(id: String?): AccentPalette = when (id) {
            "violet", "indigo" -> Purple
            "teal", "forest" -> Green
            "ocean" -> Blue
            "sunset" -> Amber
            else -> entries.firstOrNull { it.id == id } ?: Default
        }
    }
}

internal data class AccentHues(
    val brand: Color,
    val brandSoft: Color,
    val brandDeep: Color,
    val lightContainer: Color,
    val darkContainer: Color,
)
