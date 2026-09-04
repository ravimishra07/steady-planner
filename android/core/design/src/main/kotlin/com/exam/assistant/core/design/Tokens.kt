package com.exam.assistant.core.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing, radii and type sizes, transcribed from design/sam-tokens.css.
 *
 * UI code uses these; it never writes a raw number.
 */
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val icon = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val ml = 14.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal page margin. */
    val screen = 20.dp
    val section = 20.dp
}

object Radius {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 28.dp
    val summary = 24.dp
    val full = 9999.dp

    // Stable aliases used by existing components.
    val sm = small
    val md = medium
    val lg = large
    val pill = full
}

/**
 * Surface hierarchy, not decoration. Standard cards separate through tone;
 * only containers that sit above page content receive a small platform shadow.
 */
object Elevation {
    val cardTonal = 1.dp
    val cardShadow = 1.dp
    val raisedTonal = 2.dp
    val raisedShadow = 3.dp
    val floatingTonal = 3.dp
    val floatingShadow = 6.dp
}

object Opacity {
    const val modalScrim = 0.32f
}

object Stroke {
    val hairline = 1.dp
    val emphasis = 1.5.dp
    val selected = 2.dp
    val tabIndicator = 3.dp
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
    val topAppBarHeight = 64.dp
    val tabBarHeight = 56.dp
    val ctaHeight = 56.dp
    val progressHeight = 4.dp
    val compactControl = 32.dp
    val timeField = 40.dp
    val dayControl = 36.dp
    val selectionIndicator = 22.dp
    val standardIcon = 24.dp
    val smallIcon = 18.dp
    val compactIcon = 16.dp
    val swatch = 40.dp
    val planBar = 12.dp
    val summaryTrack = 10.dp
    val legendDot = 10.dp
    val focusDial = 240.dp
    val focusControl = 76.dp
    val focusTagHorizontal = 10.dp
    val focusTagVertical = 2.dp
    val progressRing = 112.dp
    val progressRingStroke = 8.dp
    val progressChart = 96.dp
    val syllabusRing = 56.dp
    val syllabusRingStroke = 5.dp
    val syllabusPrimaryAction = 56.dp
    val syllabusRowAction = 48.dp
    val syllabusStatus = 48.dp
    val syllabusSubjectTabHeight = 64.dp
    val syllabusRowMinHeight = 56.dp
    val syllabusBranchIndent = 32.dp
    val syllabusRailOffset = 10.dp
    val syllabusHookTop = 18.dp
    val syllabusHookHeight = 12.dp
    val syllabusHookWidth = 20.dp
    val syllabusFilterPopupWidth = 320.dp
    val syllabusFilterPopupTop = 52.dp
    val organiseDayWidth = 50.dp
    val organiseDayHeight = 72.dp
    val onboardingInput = 46.dp
    val lockStep = 28.dp
    val progressChartTall = 156.dp
    val progressBar = 6.dp
    val bottomClearance = 80.dp
    val themeSwatch = 48.dp
    val themePreview = 72.dp
    val appearanceModeSwatch = 36.dp
    val appearanceRow = 64.dp
}

/** Measured from the Angular mobile Today calendar. */
object CalendarMetrics {
    val monthControlHeight = 40.dp
    val dayCorner = 22.dp
    val dayTopPadding = 8.dp
    val dayBottomPadding = 10.dp
    val dayGap = 6.dp
    val gridGap = 2.dp
    val meterWidth = 20.dp
    val meterHeight = 3.dp
}

/** Measured geometry for the compact Today timeline. */
object TimelineMetrics {
    val railWidth = 52.dp
    val lineWidth = 2.dp
    val nodeSize = 10.dp
    val minimumBlockHeight = 64.dp
    val minimumDrawWidth = 2.dp
    val nowLineOvershoot = 2.dp
    val microOffset = 1.dp
    val dash = 5.dp
    val compactRail = 20.dp
    val tinyIcon = 13.dp
    val smallAction = 24.dp
    val trailingAction = 68.dp
}
