package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * User settings. Small by design: this is the only thing allowed to delay the
 * first frame, so it must stay a fast read.
 */
class SettingsStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val backgroundKey = stringPreferencesKey("background_appearance")
    private val legacyThemeKey = stringPreferencesKey("theme_choice")
    private val paletteKey = stringPreferencesKey("accent_palette")
    private val focusDurationKey = intPreferencesKey("focus_duration_sec")

    /** Emits on every change, for the settings screen. */
    /** Null means no personal choice yet, so Remote Config defaults may be used. */
    val backgroundAppearance: Flow<String?> =
        context.dataStore.data.map { preferences ->
            preferences[backgroundKey] ?: preferences[legacyThemeKey]?.toBackgroundAppearance()
        }

    val accentPalette: Flow<String?> = context.dataStore.data.map { it[paletteKey] }

    /** One-shot read for startup. */
    suspend fun backgroundAppearanceOnce(): String? = withContext(dispatchers.io) {
        context.dataStore.data.first().let { preferences ->
            preferences[backgroundKey] ?: preferences[legacyThemeKey]?.toBackgroundAppearance()
        }
    }

    suspend fun setBackgroundAppearance(value: String) = withContext(dispatchers.io) {
        context.dataStore.edit { preferences -> preferences[backgroundKey] = value }
        Unit
    }

    suspend fun accentPaletteOnce(): String? = withContext(dispatchers.io) {
        context.dataStore.data.first()[paletteKey]
    }

    suspend fun setAccentPalette(value: String) = withContext(dispatchers.io) {
        context.dataStore.edit { it[paletteKey] = value }
        Unit
    }

    suspend fun focusDurationSec(): Int = withContext(dispatchers.io) {
        context.dataStore.data.first()[focusDurationKey] ?: DEFAULT_FOCUS_DURATION_SEC
    }

    suspend fun setFocusDurationSec(seconds: Int) = withContext(dispatchers.io) {
        context.dataStore.edit { it[focusDurationKey] = seconds }
        Unit
    }

    companion object {
        const val DEFAULT_FOCUS_DURATION_SEC = 50 * 60
    }
}

private fun String.toBackgroundAppearance(): String = when (this) {
    "light" -> "light"
    "dark" -> "dark"
    // Existing System users keep a stable dark baseline, matching the shipped default.
    else -> "dark"
}
