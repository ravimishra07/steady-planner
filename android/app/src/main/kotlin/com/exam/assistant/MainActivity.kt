package com.exam.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

        // Holds only until theme and plan-presence resolve — two small DataStore
        // reads running in parallel. If this ever feels long the fix is smaller
        // reads, not a longer splash.
        splash.setKeepOnScreenCondition { !viewModel.state.value.ready }

        viewModel.observeTheme()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SteadylineTheme(choice = state.theme, palette = state.palette) {
                if (state.ready) {
                    SteadylineNavHost(
                        startInOnboarding = !state.hasPlan,
                        themeChoice = state.theme,
                        onThemeChoice = viewModel::setTheme,
                        accentPalette = state.palette,
                        onAccentPalette = viewModel::setPalette,
                        container = (application as SteadylineApp).container,
                    )
                }
            }
        }
    }
}
