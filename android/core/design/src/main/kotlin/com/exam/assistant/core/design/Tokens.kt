package com.exam.assistant.core.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing, radii and type sizes, transcribed from design/sam-tokens.css.
 *
 * UI code uses these; it never writes a raw number.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal page margin. */
    val screen = 20.dp
    val section = 20.dp
}

object Radius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val pill = 50.dp
}

object FontSize {
    val xxs = 9.sp
    val xs2 = 10.sp
    val xs = 11.sp
    val sm = 12.sp
    val sub = 13.sp
    val md = 14.sp
    val callout = 15.sp
    val lg = 16.sp
    val headline = 17.sp
    val subtitle = 18.sp
    val xl = 20.sp
    val xxl = 22.sp
    val title = 28.sp
    val hero = 34.sp
    val mega = 48.sp
    val countdown = 52.sp
    val display = 56.sp
}

object Size {
    /** Minimum touch target. */
    val touchTarget = 48.dp
    val tabBarHeight = 56.dp
    val ctaHeight = 54.dp
}
