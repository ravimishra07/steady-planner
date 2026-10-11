package com.exam.assistant.core.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * M3 motion tokens. Compose scales every duration by the system animator scale,
 * so "Remove animations" in accessibility settings turns these off for free.
 */
object Motion {
    const val short = 150
    const val medium = 300

    /** Things arriving on screen. */
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving the screen. */
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * The app's three haptics, through View.performHapticFeedback so the system
 * "Touch feedback" setting is respected. Kept to moments that matter; not every tap buzzes.
 */
@Stable
class Haptics(private val view: View) {
    /** Picking an option: chip, card, day, menu item. */
    fun select() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Moving forward or finishing something: Continue, session done, purchase. */
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    /** A blocked action: Continue while something is wrong. */
    fun reject() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
