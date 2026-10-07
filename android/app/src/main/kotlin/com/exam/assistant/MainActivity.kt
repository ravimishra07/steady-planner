package com.exam.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exam.assistant.core.design.SteadylineTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StartupViewModel by viewModels {
        StartupViewModel.Factory((application as SteadylineApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installed before super.onCreate so the system splash owns the window
        // from the first moment; there is no separate splash Activity.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The app draws the correct themed surface behind both system bars.
            // Android's three-button contrast scrim would otherwise make the
            // navigation area look like a separate black or white strip.
            window.isNavigationBarContrastEnforced = false
        }

        // Holds only until theme and plan-presence resolve — two small DataStore
        // reads running in parallel. If this ever feels long the fix is smaller
        // reads, not a longer splash.
        splash.setKeepOnScreenCondition { !viewModel.state.value.ready }

        viewModel.observeTheme()
        if (savedInstanceState == null) handleOpenRequest(intent)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SteadylineTheme(background = state.background, palette = state.palette) {
                val colors = com.exam.assistant.core.design.AppTheme.colors
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = colors.bg.luminance() > 0.5f
                        isAppearanceLightNavigationBars = colors.bg.luminance() > 0.5f
                    }
                }
                if (state.ready) {
                    SteadylineNavHost(
                        startInOnboarding = !state.hasPlan,
                        background = state.background,
                        onBackground = viewModel::setBackground,
                        accentPalette = state.palette,
                        onAccentPalette = viewModel::setPalette,
                        container = (application as SteadylineApp).container,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenRequest(intent)
    }

    /** A Focus notification or daily reminder tap lands here; the nav host does the navigation. */
    private fun handleOpenRequest(intent: Intent?) {
        val container = (application as SteadylineApp).container
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_FOCUS -> container.openFocusRequest.value = true
            OPEN_TODAY -> container.openTodayRequest.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN = "com.exam.assistant.extra.OPEN"
        const val OPEN_FOCUS = "focus"
        const val OPEN_TODAY = "today"
    }
}
