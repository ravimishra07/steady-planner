package com.exam.assistant.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Five Material 3 accent families, matching Angular Material's generated roles. */
enum class AccentPalette(val id: String) {
    Blue("blue"), Purple("purple"), Green("green"), Amber("amber"), Rose("rose");

    val brand: Color get() = roles.light.primary
    val brandSoft: Color get() = roles.dark.primary
    val brandDeep: Color get() = roles.light.primary
    val onSwatch: Color get() = Color.White

    internal val roles: AccentRolePair
        get() = when (this) {
            Blue -> blueRoles
            Purple -> purpleRoles
            Green -> greenRoles
            Amber -> amberRoles
            Rose -> roseRoles
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

@Immutable
internal data class AccentRoles(
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
)

@Immutable
internal data class AccentRolePair(val light: AccentRoles, val dark: AccentRoles)

private val blueRoles = AccentRolePair(
    light = AccentRoles(
        primary = Color(0xFF005CBB), onPrimary = Color.White,
        primaryContainer = Color(0xFFD7E3FF), onPrimaryContainer = Color(0xFF00458F),
        inversePrimary = Color(0xFFABC7FF), primaryFixed = Color(0xFFD7E3FF),
        primaryFixedDim = Color(0xFFABC7FF), onPrimaryFixed = Color(0xFF001B3F),
        onPrimaryFixedVariant = Color(0xFF00458F), secondary = Color(0xFF565E71),
        onSecondary = Color.White, secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF3E4759), tertiary = Color(0xFF006A6A),
        onTertiary = Color.White, tertiaryContainer = Color(0xFF00FBFB),
        onTertiaryContainer = Color(0xFF004F4F),
    ),
    dark = AccentRoles(
        primary = Color(0xFFABC7FF), onPrimary = Color(0xFF002F65),
        primaryContainer = Color(0xFF00458F), onPrimaryContainer = Color(0xFFD7E3FF),
        inversePrimary = Color(0xFF005CBB), primaryFixed = Color(0xFFD7E3FF),
        primaryFixedDim = Color(0xFFABC7FF), onPrimaryFixed = Color(0xFF001B3F),
        onPrimaryFixedVariant = Color(0xFF00458F), secondary = Color(0xFFBEC6DC),
        onSecondary = Color(0xFF283041), secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9), tertiary = Color(0xFF00DDDD),
        onTertiary = Color(0xFF003737), tertiaryContainer = Color(0xFF004F4F),
        onTertiaryContainer = Color(0xFF00FBFB),
    ),
)

private val purpleRoles = AccentRolePair(
    light = AccentRoles(
        primary = Color(0xFF7D00FA), onPrimary = Color.White,
        primaryContainer = Color(0xFFECDCFF), onPrimaryContainer = Color(0xFF5F00C0),
        inversePrimary = Color(0xFFD5BAFF), primaryFixed = Color(0xFFECDCFF),
        primaryFixedDim = Color(0xFFD5BAFF), onPrimaryFixed = Color(0xFF270057),
        onPrimaryFixedVariant = Color(0xFF5F00C0), secondary = Color(0xFF645B70),
        onSecondary = Color.White, secondaryContainer = Color(0xFFEADEF7),
        onSecondaryContainer = Color(0xFF4B4357), tertiary = Color(0xFF343DFF),
        onTertiary = Color.White, tertiaryContainer = Color(0xFFE0E0FF),
        onTertiaryContainer = Color(0xFF0000EF),
    ),
    dark = AccentRoles(
        primary = Color(0xFFD5BAFF), onPrimary = Color(0xFF42008A),
        primaryContainer = Color(0xFF5F00C0), onPrimaryContainer = Color(0xFFECDCFF),
        inversePrimary = Color(0xFF7D00FA), primaryFixed = Color(0xFFECDCFF),
        primaryFixedDim = Color(0xFFD5BAFF), onPrimaryFixed = Color(0xFF270057),
        onPrimaryFixedVariant = Color(0xFF5F00C0), secondary = Color(0xFFCEC2DB),
        onSecondary = Color(0xFF352D40), secondaryContainer = Color(0xFF4B4357),
        onSecondaryContainer = Color(0xFFEADEF7), tertiary = Color(0xFFBEC2FF),
        onTertiary = Color(0xFF0001AC), tertiaryContainer = Color(0xFF0000EF),
        onTertiaryContainer = Color(0xFFE0E0FF),
    ),
)

private val greenRoles = AccentRolePair(
    light = AccentRoles(
        primary = Color(0xFF026E00), onPrimary = Color.White,
        primaryContainer = Color(0xFF77FF61), onPrimaryContainer = Color(0xFF015300),
        inversePrimary = Color(0xFF02E600), primaryFixed = Color(0xFF77FF61),
        primaryFixedDim = Color(0xFF02E600), onPrimaryFixed = Color(0xFF002200),
        onPrimaryFixedVariant = Color(0xFF015300), secondary = Color(0xFF54634D),
        onSecondary = Color.White, secondaryContainer = Color(0xFFD7E8CD),
        onSecondaryContainer = Color(0xFF3C4B37), tertiary = Color(0xFF006D33),
        onTertiary = Color.White, tertiaryContainer = Color(0xFF63FF94),
        onTertiaryContainer = Color(0xFF005225),
    ),
    dark = AccentRoles(
        primary = Color(0xFF02E600), onPrimary = Color(0xFF013A00),
        primaryContainer = Color(0xFF015300), onPrimaryContainer = Color(0xFF77FF61),
        inversePrimary = Color(0xFF026E00), primaryFixed = Color(0xFF77FF61),
        primaryFixedDim = Color(0xFF02E600), onPrimaryFixed = Color(0xFF002200),
        onPrimaryFixedVariant = Color(0xFF015300), secondary = Color(0xFFBBCBB2),
        onSecondary = Color(0xFF263422), secondaryContainer = Color(0xFF3C4B37),
        onSecondaryContainer = Color(0xFFD7E8CD), tertiary = Color(0xFF00E472),
        onTertiary = Color(0xFF003917), tertiaryContainer = Color(0xFF005225),
        onTertiaryContainer = Color(0xFF63FF94),
    ),
)

private val amberRoles = AccentRolePair(
    light = AccentRoles(
        primary = Color(0xFF964900), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDCC7), onPrimaryContainer = Color(0xFF723600),
        inversePrimary = Color(0xFFFFB787), primaryFixed = Color(0xFFFFDCC7),
        primaryFixedDim = Color(0xFFFFB787), onPrimaryFixed = Color(0xFF311300),
        onPrimaryFixedVariant = Color(0xFF723600), secondary = Color(0xFF755846),
        onSecondary = Color.White, secondaryContainer = Color(0xFFFFDCC7),
        onSecondaryContainer = Color(0xFF5B4130), tertiary = Color(0xFF626200),
        onTertiary = Color.White, tertiaryContainer = Color(0xFFEAEA00),
        onTertiaryContainer = Color(0xFF494900),
    ),
    dark = AccentRoles(
        primary = Color(0xFFFFB787), onPrimary = Color(0xFF502400),
        primaryContainer = Color(0xFF723600), onPrimaryContainer = Color(0xFFFFDCC7),
        inversePrimary = Color(0xFF964900), primaryFixed = Color(0xFFFFDCC7),
        primaryFixedDim = Color(0xFFFFB787), onPrimaryFixed = Color(0xFF311300),
        onPrimaryFixedVariant = Color(0xFF723600), secondary = Color(0xFFE5BFA8),
        onSecondary = Color(0xFF422B1B), secondaryContainer = Color(0xFF5B4130),
        onSecondaryContainer = Color(0xFFFFDCC7), tertiary = Color(0xFFCDCD00),
        onTertiary = Color(0xFF323200), tertiaryContainer = Color(0xFF494900),
        onTertiaryContainer = Color(0xFFEAEA00),
    ),
)

private val roseRoles = AccentRolePair(
    light = AccentRoles(
        primary = Color(0xFFBA005C), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF8F0045),
        inversePrimary = Color(0xFFFFB1C5), primaryFixed = Color(0xFFFFD9E1),
        primaryFixedDim = Color(0xFFFFB1C5), onPrimaryFixed = Color(0xFF3F001B),
        onPrimaryFixedVariant = Color(0xFF8F0045), secondary = Color(0xFF74565D),
        onSecondary = Color.White, secondaryContainer = Color(0xFFFFD9E1),
        onSecondaryContainer = Color(0xFF5B3F46), tertiary = Color(0xFFA900A9),
        onTertiary = Color.White, tertiaryContainer = Color(0xFFFFD7F5),
        onTertiaryContainer = Color(0xFF810081),
    ),
    dark = AccentRoles(
        primary = Color(0xFFFFB1C5), onPrimary = Color(0xFF65002F),
        primaryContainer = Color(0xFF8F0045), onPrimaryContainer = Color(0xFFFFD9E1),
        inversePrimary = Color(0xFFBA005C), primaryFixed = Color(0xFFFFD9E1),
        primaryFixedDim = Color(0xFFFFB1C5), onPrimaryFixed = Color(0xFF3F001B),
        onPrimaryFixedVariant = Color(0xFF8F0045), secondary = Color(0xFFE3BDC5),
        onSecondary = Color(0xFF422930), secondaryContainer = Color(0xFF5B3F46),
        onSecondaryContainer = Color(0xFFFFD9E1), tertiary = Color(0xFFFFABF3),
        onTertiary = Color(0xFF5B005B), tertiaryContainer = Color(0xFF810081),
        onTertiaryContainer = Color(0xFFFFD7F5),
    ),
)
