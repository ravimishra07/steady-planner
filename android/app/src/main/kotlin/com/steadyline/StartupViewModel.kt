package com.steadyline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.steadyline.core.data.PlanStore
import com.steadyline.core.data.SettingsStore
import com.steadyline.core.design.ThemeChoice
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the first frame needs, and nothing else. */
data class StartupState(
    val ready: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.System,
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
    private val planStore: PlanStore,
) : ViewModel() {

    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            coroutineScope {
                val theme = async { settings.themeChoiceOnce() }
                val hasPlan = async { planStore.exists() }
                _state.value = StartupState(
                    ready = true,
                    theme = theme.await().toThemeChoice(),
                    hasPlan = hasPlan.await(),
                )
            }
        }
    }

    /** Persists the choice; the theme flow pushes the new value back into state. */
    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch {
            settings.setThemeChoice(
                when (choice) {
                    ThemeChoice.Light -> SettingsStore.LIGHT
                    ThemeChoice.Dark -> SettingsStore.DARK
                    ThemeChoice.System -> SettingsStore.SYSTEM
                }
            )
        }
    }

    /** Live updates once the app is running, e.g. from Settings. */
    fun observeTheme() {
        viewModelScope.launch {
            settings.themeChoice.collect { value ->
                _state.value = _state.value.copy(theme = value.toThemeChoice())
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StartupViewModel(container.settings, container.planStore) as T
    }
}

private fun String.toThemeChoice(): ThemeChoice = when (this) {
    SettingsStore.LIGHT -> ThemeChoice.Light
    SettingsStore.DARK -> ThemeChoice.Dark
    else -> ThemeChoice.System
}
