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
    private val themeKey = stringPreferencesKey("theme_choice")
    private val paletteKey = stringPreferencesKey("accent_palette")
    private val focusDurationKey = intPreferencesKey("focus_duration_sec")

    /** Emits on every change, for the settings screen. */
    val themeChoice: Flow<String> =
        context.dataStore.data.map { it[themeKey] ?: SYSTEM }

    val accentPalette: Flow<String> =
        context.dataStore.data.map { it[paletteKey] ?: PALETTE_VIOLET }

    /** One-shot read for startup. */
    suspend fun themeChoiceOnce(): String = withContext(dispatchers.io) {
        context.dataStore.data.first()[themeKey] ?: SYSTEM
    }

    suspend fun setThemeChoice(value: String) = withContext(dispatchers.io) {
        context.dataStore.edit { it[themeKey] = value }
        Unit
    }

    suspend fun accentPaletteOnce(): String = withContext(dispatchers.io) {
        context.dataStore.data.first()[paletteKey] ?: PALETTE_VIOLET
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
        const val SYSTEM = "system"
        const val LIGHT = "light"
        const val DARK = "dark"
        const val PALETTE_VIOLET = "violet"
        const val DEFAULT_FOCUS_DURATION_SEC = 50 * 60
    }
}
