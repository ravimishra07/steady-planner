package com.steadyline.onboarding

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Dimens {
    val progress = 4.dp
    val hairline = 1.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val topBar = 64.dp
    val touch = 48.dp
    val cta = 56.dp
}

object Shapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val full = RoundedCornerShape(100.dp)
}

private data class AccentRoles(
    val lightPrimary: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val lightSecondaryContainer: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color,
)

private fun accentRoles(id: String) = when (id) {
    "blue" -> AccentRoles(Color(0xFF005CBB), Color(0xFFABC7FF), Color(0xFF002F65), Color(0xFFDAE2F9), Color(0xFF3E4759), Color(0xFFDAE2F9))
    "green" -> AccentRoles(Color(0xFF026E00), Color(0xFF02E600), Color(0xFF013A00), Color(0xFFD7E8CD), Color(0xFF3C4B37), Color(0xFFD7E8CD))
    "amber" -> AccentRoles(Color(0xFF964900), Color(0xFFFFB787), Color(0xFF502400), Color(0xFFFFDCC7), Color(0xFF5B4130), Color(0xFFFFDCC7))
    "rose" -> AccentRoles(Color(0xFFBA005C), Color(0xFFFFB1C5), Color(0xFF65002F), Color(0xFFFFD9E1), Color(0xFF5B3F46), Color(0xFFFFD9E1))
    else -> AccentRoles(Color(0xFF7D00FA), Color(0xFFD5BAFF), Color(0xFF42008A), Color(0xFFEADEF7), Color(0xFF4B4357), Color(0xFFEADEF7))
}

private fun darkScheme(accent: AccentRoles, slate: Boolean): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = Color(0xFF5F00C0),
    onPrimaryContainer = Color(0xFFECDCFF),
    secondaryContainer = accent.darkSecondaryContainer,
    onSecondaryContainer = accent.darkOnSecondaryContainer,
    tertiary = Color(0xFFBEC2FF),
    surface = if (slate) Color(0xFF111820) else Color(0xFF151316),
    surfaceContainerLowest = if (slate) Color(0xFF0C1218) else Color(0xFF0F0D11),
    surfaceContainerLow = if (slate) Color(0xFF18212A) else Color(0xFF1D1B1E),
    surfaceContainer = if (slate) Color(0xFF1A232D) else Color(0xFF211F22),
    surfaceContainerHigh = if (slate) Color(0xFF202B36) else Color(0xFF2B292D),
    surfaceContainerHighest = if (slate) Color(0xFF293542) else Color(0xFF363437),
    onSurface = if (slate) Color(0xFFF0F4F8) else Color(0xFFE6E1E6),
    onSurfaceVariant = Color(0xFFE8E0EB),
    outline = Color(0xFF958E99),
    outlineVariant = Color(0xFF49454E),
)

private fun lightScheme(accent: AccentRoles, grey: Boolean): ColorScheme = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECDCFF),
    onPrimaryContainer = Color(0xFF5F00C0),
    secondaryContainer = accent.lightSecondaryContainer,
    onSecondaryContainer = Color(0xFF4B4357),
    tertiary = Color(0xFF343DFF),
    surface = if (grey) Color(0xFFF3F4F6) else Color(0xFFFEF8FC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = if (grey) Color(0xFFEEF0F3) else Color(0xFFF8F2F6),
    surfaceContainer = if (grey) Color(0xFFE9EAEE) else Color(0xFFF2ECF1),
    surfaceContainerHigh = if (grey) Color(0xFFE2E4E9) else Color(0xFFEDE6EB),
    surfaceContainerHighest = if (grey) Color(0xFFDCDEE4) else Color(0xFFE6E1E6),
    onSurface = Color(0xFF1D1B1E),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCBC4CF),
)

@Composable
fun OnboardingTheme(accentId: String, appearanceId: String, content: @Composable () -> Unit) {
    val dark = appearanceId == "dark" || appearanceId == "slate" || (appearanceId == "system" && isSystemInDarkTheme())
    val accent = accentRoles(accentId)
    val scheme = if (dark) darkScheme(accent, appearanceId == "slate") else lightScheme(accent, appearanceId == "grey")
    MaterialTheme(colorScheme = scheme, shapes = androidx.compose.material3.Shapes(small = Shapes.small, medium = Shapes.medium, large = Shapes.large), content = content)
}
