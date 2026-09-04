package com.exam.assistant.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The Material 3 type scale used by the Angular reference.
 *
 * Android's platform sans-serif is Roboto, so no font file is downloaded or
 * bundled. A future font change remains isolated to this file.
 */
object AppType {
    val family: FontFamily = FontFamily.Default

    val displayLarge = m3Style(57, 64, FontWeight.Normal, -0.25f)
    val displayMedium = m3Style(45, 52, FontWeight.Normal)
    val displaySmall = m3Style(36, 44, FontWeight.Normal)
    val headlineLarge = m3Style(32, 40, FontWeight.Normal)
    val headlineMedium = m3Style(28, 36, FontWeight.Normal)
    val headlineSmall = m3Style(24, 32, FontWeight.Normal)
    val titleLarge = m3Style(22, 28, FontWeight.Normal)
    val titleMedium = m3Style(16, 24, FontWeight.Medium, 0.15f)
    val titleSmall = m3Style(14, 20, FontWeight.Medium, 0.10f)
    val bodyLarge = m3Style(16, 24, FontWeight.Normal, 0.50f)
    val bodyMedium = m3Style(14, 20, FontWeight.Normal, 0.25f)
    val bodySmall = m3Style(12, 16, FontWeight.Normal, 0.40f)
    val labelLarge = m3Style(14, 20, FontWeight.Medium, 0.10f)
    val labelMedium = m3Style(12, 16, FontWeight.Medium, 0.50f)
    val labelSmall = m3Style(11, 16, FontWeight.Medium, 0.50f)

    // Compatibility aliases for feature code. They all resolve to the same
    // measured M3 scale; no feature owns an independent type size.
    val display = displayLarge
    val countdown = displayLarge
    val mega = displayLarge
    val hero = displaySmall
    val title = headlineLarge
    val xxl = headlineMedium
    val xl = headlineSmall
    val subtitle = titleLarge
    val headline = titleMedium
    val lg = bodyLarge.copy(fontWeight = FontWeight.Medium)
    val lgRegular = bodyLarge
    val callout = labelLarge
    val md = bodyMedium
    val sub = bodySmall
    val sm = bodySmall
    val eyebrow = labelSmall
    val tabLabel = labelMedium
    val micro = labelSmall
}

private fun m3Style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = AppType.family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

internal val appTypography = Typography(
    displayLarge = AppType.displayLarge,
    displayMedium = AppType.displayMedium,
    displaySmall = AppType.displaySmall,
    headlineLarge = AppType.headlineLarge,
    headlineMedium = AppType.headlineMedium,
    headlineSmall = AppType.headlineSmall,
    titleLarge = AppType.titleLarge,
    titleMedium = AppType.titleMedium,
    titleSmall = AppType.titleSmall,
    bodyLarge = AppType.bodyLarge,
    bodyMedium = AppType.bodyMedium,
    bodySmall = AppType.bodySmall,
    labelLarge = AppType.labelLarge,
    labelMedium = AppType.labelMedium,
    labelSmall = AppType.labelSmall,
)
