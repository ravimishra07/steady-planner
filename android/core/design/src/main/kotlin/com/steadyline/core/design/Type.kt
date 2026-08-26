package com.steadyline.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle

/**
 * The type ramp by name. UI code uses these; it never writes a raw sp value.
 *
 * The family is the platform default (Roboto) — no bundled font, so nothing is
 * added to the APK and nothing is downloaded at runtime. Swapping to a bundled
 * face later is a change here and nowhere else.
 */
object AppType {
    val family: FontFamily = FontFamily.Default

    private val trim = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    private fun style(
        size: androidx.compose.ui.unit.TextUnit,
        weight: FontWeight,
        heightMultiple: Double,
        letterSpacingEm: Double = 0.0,
    ) = TextStyle(
        fontFamily = family,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * heightMultiple.toFloat(),
        letterSpacing = size * letterSpacingEm.toFloat(),
        lineHeightStyle = trim,
    )

    // display and titles
    val display = style(FontSize.display, FontWeight.Bold, 1.12, -0.03)
    val countdown = style(FontSize.countdown, FontWeight.Bold, 1.12, -0.03)
    val mega = style(FontSize.mega, FontWeight.Bold, 1.12, -0.03)
    val hero = style(FontSize.hero, FontWeight.Bold, 1.12, -0.03)
    val title = style(FontSize.title, FontWeight.Bold, 1.12, -0.02)
    val xxl = style(FontSize.xxl, FontWeight.SemiBold, 1.28, -0.02)
    val xl = style(FontSize.xl, FontWeight.SemiBold, 1.28, -0.01)

    // body
    val subtitle = style(FontSize.subtitle, FontWeight.SemiBold, 1.28)
    val headline = style(FontSize.headline, FontWeight.SemiBold, 1.28)
    val lg = style(FontSize.lg, FontWeight.Medium, 1.45)
    val lgRegular = style(FontSize.lg, FontWeight.Normal, 1.5)
    val callout = style(FontSize.callout, FontWeight.Medium, 1.28)
    val md = style(FontSize.md, FontWeight.Normal, 1.5)
    val sub = style(FontSize.sub, FontWeight.Normal, 1.45)
    val sm = style(FontSize.sm, FontWeight.Normal, 1.45)

    // labels
    val eyebrow = style(FontSize.xs, FontWeight.SemiBold, 1.45, 0.08)
    val tabLabel = style(FontSize.xs2, FontWeight.Medium, 1.12)
    val micro = style(FontSize.xxs, FontWeight.SemiBold, 1.12, 0.09)
}

/**
 * Material's Typography, so stock components inherit the ramp instead of
 * falling back to defaults.
 */
internal val appTypography = Typography(
    displayLarge = AppType.display,
    displayMedium = AppType.hero,
    headlineLarge = AppType.title,
    headlineMedium = AppType.xxl,
    headlineSmall = AppType.xl,
    titleLarge = AppType.subtitle,
    titleMedium = AppType.headline,
    titleSmall = AppType.callout,
    bodyLarge = AppType.lgRegular,
    bodyMedium = AppType.md,
    bodySmall = AppType.sub,
    labelLarge = AppType.callout,
    labelMedium = AppType.tabLabel,
    labelSmall = AppType.micro,
)
