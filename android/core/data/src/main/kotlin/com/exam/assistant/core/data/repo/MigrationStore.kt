package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.migrationDataStore by preferencesDataStore(name = "migration")

/** Tiny gate so the legacy-to-Room migration runs exactly once, even across app restarts mid-migration. */
class MigrationStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val versionKey = intPreferencesKey("legacy_migration_version")

    suspend fun currentVersion(): Int = withContext(dispatchers.io) {
        context.migrationDataStore.data.first()[versionKey] ?: 0
    }

    suspend fun markComplete(version: Int) = withContext(dispatchers.io) {
        context.migrationDataStore.edit { it[versionKey] = version }
    }
}
