package com.exam.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.ConfigKeys
import com.exam.assistant.core.data.RemoteConfig
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.domain.NEET_EXAM_ID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** What the first frame needs, and nothing else. */
data class StartupState(
    val ready: Boolean = false,
    val background: BackgroundAppearance = BackgroundAppearance.Default,
    val palette: AccentPalette = AccentPalette.Default,
    val hasPlan: Boolean = false,
)

/**
 * Resolves the two things the first frame depends on.
 *
 * They run in parallel because neither depends on the other; series would just
 * add the two latencies together for no reason.
 */
class StartupViewModel(
    private val settings: SettingsStore,
    private val attemptRepository: AttemptRepository,
    private val remoteConfig: RemoteConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = runCatching {
                withTimeout(STARTUP_TIMEOUT_MS) {
                    coroutineScope {
                        val background = async { settings.backgroundAppearanceOnce() }
                        val palette = async { settings.accentPaletteOnce() }
                        val hasPlan = async { attemptRepository.hasAttemptFast() }
                        StartupState(
                            ready = true,
                            background = BackgroundAppearance.fromId(
                                background.await() ?: remoteConfig.string(
                                    ConfigKeys.DEFAULT_BACKGROUND_THEME,
                                    BackgroundAppearance.Default.id,
                                ),
                            ),
                            palette = AccentPalette.fromId(
                                palette.await() ?: remoteConfig.string(
                                    ConfigKeys.DEFAULT_ACCENT_THEME,
                                    AccentPalette.Default.id,
                                ),
                            ),
                            hasPlan = hasPlan.await(),
                        )
                    }
                }
            }.getOrElse {
                // A damaged preference file must never trap the student on the splash forever.
                StartupState(ready = true)
            }
        }
    }

    /** Persists the choice; the theme flow pushes the new value back into state. */
    fun setBackground(background: BackgroundAppearance) {
        viewModelScope.launch {
            settings.setBackgroundAppearance(background.id)
        }
    }

    /** Live updates once the app is running, e.g. from Settings. */
    fun observeTheme() {
        viewModelScope.launch {
            settings.backgroundAppearance.collect { value ->
                if (value != null) _state.value = _state.value.copy(background = BackgroundAppearance.fromId(value))
            }
        }
        viewModelScope.launch {
            settings.accentPalette.collect { value ->
                if (value != null) _state.value = _state.value.copy(palette = AccentPalette.fromId(value))
            }
        }
    }

    fun setPalette(palette: AccentPalette) {
        viewModelScope.launch {
            settings.setAccentPalette(palette.id)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StartupViewModel(container.settings, container.attemptRepository, container.remoteConfig) as T
    }

    private companion object {
        const val STARTUP_TIMEOUT_MS = 5_000L
    }
}
