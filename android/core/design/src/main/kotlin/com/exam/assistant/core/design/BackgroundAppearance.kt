package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The five appearance choices exposed by the Angular reference. */
enum class BackgroundAppearance(val id: String) {
    System("system"), Light("light"), Dark("dark"), Grey("grey"), Slate("slate");

    /** Stable miniature colors used by the appearance picker in onboarding/settings. */
    val previewBackground: Color get() = when (this) {
        System -> Color(0xFFD8D7DD)
        Light -> Color(0xFFFCFCFF)
        Dark -> Color(0xFF0A0A0F)
        Grey -> Color(0xFFF3F4F6)
        Slate -> Color(0xFF111820)
    }

    val previewInk: Color get() = when (this) {
        System -> Color(0xFF343239)
        Light -> Color(0xFF191C25)
        Dark -> Color(0xFFF4F3F8)
        Grey -> Color(0xFF1B1C20)
        Slate -> Color(0xFFF0F4F8)
    }

    /** Fixed-dark appearances. System is resolved by [resolved]. */
    val isDark: Boolean get() = this == Dark || this == Slate

    fun resolved(systemDark: Boolean): BackgroundAppearance = when (this) {
        System -> if (systemDark) Dark else Light
        else -> this
    }

    companion object {
        val Default = Dark
        fun fromId(id: String?): BackgroundAppearance =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Exact Material 3 neutral/error roles measured from the Angular theme. */
@Immutable
internal data class NeutralRoles(
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
)

internal val BackgroundAppearance.neutralRoles: NeutralRoles
    get() = when (this) {
        BackgroundAppearance.System,
        BackgroundAppearance.Light -> lightNeutralRoles
        BackgroundAppearance.Dark -> darkNeutralRoles
        BackgroundAppearance.Grey -> lightNeutralRoles.copy(
            surface = Color(0xFFF3F4F6),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFEEF0F3),
            surfaceContainer = Color(0xFFE9EAEE),
            surfaceContainerHigh = Color(0xFFE2E4E9),
            surfaceContainerHighest = Color(0xFFDCDEE4),
        )
        BackgroundAppearance.Slate -> darkNeutralRoles.copy(
            surface = Color(0xFF111820),
            surfaceContainerLowest = Color(0xFF0C1218),
            surfaceContainerLow = Color(0xFF18212A),
            surfaceContainer = Color(0xFF1A232D),
            surfaceContainerHigh = Color(0xFF202B36),
            surfaceContainerHighest = Color(0xFF293542),
        )
    }

private val lightNeutralRoles = NeutralRoles(
    background = Color(0xFFFEF8FC),
    onBackground = Color(0xFF1D1B1E),
    surface = Color(0xFFFEF8FC),
    onSurface = Color(0xFF1D1B1E),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    inverseSurface = Color(0xFF323033),
    inverseOnSurface = Color(0xFFF5EFF4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCBC4CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF8FC),
    surfaceDim = Color(0xFFDED8DD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2F6),
    surfaceContainer = Color(0xFFF2ECF1),
    surfaceContainerHigh = Color(0xFFEDE6EB),
    surfaceContainerHighest = Color(0xFFE6E1E6),
)

private val darkNeutralRoles = NeutralRoles(
    background = Color(0xFF151316),
    onBackground = Color(0xFFE6E1E6),
    surface = Color(0xFF151316),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFE8E0EB),
    inverseSurface = Color(0xFFE6E1E6),
    inverseOnSurface = Color(0xFF323033),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF49454E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383C),
    surfaceDim = Color(0xFF151316),
    surfaceContainerLowest = Color(0xFF0F0D11),
    surfaceContainerLow = Color(0xFF1D1B1E),
    surfaceContainer = Color(0xFF211F22),
    surfaceContainerHigh = Color(0xFF2B292D),
    surfaceContainerHighest = Color(0xFF363437),
)
