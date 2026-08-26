package com.steadyline.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.steadyline.core.common.AppDispatchers
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

    /** Emits on every change, for the settings screen. */
    val themeChoice: Flow<String> =
        context.dataStore.data.map { it[themeKey] ?: SYSTEM }

    /** One-shot read for startup. */
    suspend fun themeChoiceOnce(): String = withContext(dispatchers.io) {
        context.dataStore.data.first()[themeKey] ?: SYSTEM
    }

    suspend fun setThemeChoice(value: String) = withContext(dispatchers.io) {
        context.dataStore.edit { it[themeKey] = value }
        Unit
    }

    companion object {
        const val SYSTEM = "system"
        const val LIGHT = "light"
        const val DARK = "dark"
    }
}
