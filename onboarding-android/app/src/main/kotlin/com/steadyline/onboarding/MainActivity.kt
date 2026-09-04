package com.steadyline.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: OnboardingViewModel = viewModel()
            val state = viewModel.state.collectAsStateWithLifecycle().value
            OnboardingTheme(state.accentId, state.appearanceId) {
                OnboardingApp(state, viewModel)
            }
        }
    }
}
