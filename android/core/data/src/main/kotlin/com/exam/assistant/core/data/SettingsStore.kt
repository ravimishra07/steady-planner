package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// Internal so BackupRepository can export and restore it (one DataStore per file per process).
internal val Context.dataStore by preferencesDataStore(name = "settings")

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
    private val focusEndAlertKey = booleanPreferencesKey("focus_end_alert")
    private val notificationPromptKey = booleanPreferencesKey("notification_prompt_shown")
    private val dailyReminderEnabledKey = booleanPreferencesKey("daily_reminder_enabled")
    private val dailyReminderMinuteKey = intPreferencesKey("daily_reminder_minute")
    private val shareUsageDataKey = booleanPreferencesKey("share_usage_data")

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

    /** End-of-session sound and vibration. On by default. */
    val focusEndAlert: Flow<Boolean> = context.dataStore.data.map { it[focusEndAlertKey] ?: true }

    suspend fun focusEndAlertOnce(): Boolean = withContext(dispatchers.io) {
        context.dataStore.data.first()[focusEndAlertKey] ?: true
    }

    suspend fun setFocusEndAlert(enabled: Boolean) = withContext(dispatchers.io) {
        context.dataStore.edit { it[focusEndAlertKey] = enabled }
        Unit
    }

    /** Whether the notification permission has been asked for at a Focus start. */
    suspend fun notificationPromptShown(): Boolean = withContext(dispatchers.io) {
        context.dataStore.data.first()[notificationPromptKey] ?: false
    }

    suspend fun setNotificationPromptShown() = withContext(dispatchers.io) {
        context.dataStore.edit { it[notificationPromptKey] = true }
        Unit
    }

    /** Settings → "Share anonymous usage data" (analytics and crash reports, §11.3). On by default. */
    val shareUsageData: Flow<Boolean> = context.dataStore.data.map { it[shareUsageDataKey] ?: true }

    suspend fun setShareUsageData(enabled: Boolean) = withContext(dispatchers.io) {
        context.dataStore.edit { it[shareUsageDataKey] = enabled }
        Unit
    }

    /** The optional "Today's plan" reminder (PRODUCTION_V1_PLAN §10.1). Off by default. */
    val dailyReminder: Flow<DailyReminderSetting> = context.dataStore.data.map { it.toDailyReminder() }

    suspend fun dailyReminderOnce(): DailyReminderSetting = withContext(dispatchers.io) {
        context.dataStore.data.first().toDailyReminder()
    }

    suspend fun setDailyReminder(setting: DailyReminderSetting) = withContext(dispatchers.io) {
        context.dataStore.edit {
            it[dailyReminderEnabledKey] = setting.enabled
            it[dailyReminderMinuteKey] = setting.minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        }
        Unit
    }

    private fun androidx.datastore.preferences.core.Preferences.toDailyReminder() = DailyReminderSetting(
        enabled = this[dailyReminderEnabledKey] ?: false,
        minuteOfDay = this[dailyReminderMinuteKey] ?: DailyReminderSetting.DEFAULT_MINUTE_OF_DAY,
    )

    companion object {
        const val DEFAULT_FOCUS_DURATION_SEC = 50 * 60
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

private fun String.toBackgroundAppearance(): String = when (this) {
    "light" -> "light"
    "dark" -> "dark"
    // Existing System users keep a stable dark baseline, matching the shipped default.
    else -> "dark"
}

/** The daily "Today's plan" reminder: whether it is on, and when, as minutes after midnight. */
data class DailyReminderSetting(
    val enabled: Boolean = false,
    val minuteOfDay: Int = DEFAULT_MINUTE_OF_DAY,
) {
    companion object {
        const val DEFAULT_MINUTE_OF_DAY = 7 * 60
    }
}
