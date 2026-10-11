package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.FocusLockSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Context.focusLockDataStore by preferencesDataStore(name = "focus_lock")

class FocusLockStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val blockedKey = stringSetPreferencesKey("blocked_packages")
    private val allowancesKey = stringPreferencesKey("temp_allowances_json")

    suspend fun load(): FocusLockSettings = withContext(dispatchers.io) {
        val prefs = context.focusLockDataStore.data.first()
        FocusLockSettings(
            enabled = prefs[enabledKey] ?: false,
            blockedPackages = prefs[blockedKey].orEmpty(),
            temporaryAllowances = parseAllowances(prefs[allowancesKey]),
        )
    }

    suspend fun save(settings: FocusLockSettings) = withContext(dispatchers.io) {
        context.focusLockDataStore.edit { prefs ->
            prefs[enabledKey] = settings.enabled
            prefs[blockedKey] = settings.blockedPackages
            prefs[allowancesKey] = serializeAllowances(settings.temporaryAllowances)
        }
        Unit
    }

    private fun parseAllowances(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, Long>()
        obj.keys().forEach { key -> result[key] = obj.optLong(key) }
        return result
    }

    private fun serializeAllowances(allowances: Map<String, Long>): String {
        val obj = JSONObject()
        allowances.forEach { (pkg, expiry) -> obj.put(pkg, expiry) }
        return obj.toString()
    }
}
