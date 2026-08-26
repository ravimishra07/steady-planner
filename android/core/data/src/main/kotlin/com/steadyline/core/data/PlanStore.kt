package com.steadyline.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.steadyline.core.common.AppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.planDataStore by preferencesDataStore(name = "plan")

/**
 * Whether the user has finished onboarding. Read on the critical path, so it
 * holds one boolean and nothing else — the plan itself loads later.
 */
class PlanStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val existsKey = booleanPreferencesKey("plan_exists")

    suspend fun exists(): Boolean = withContext(dispatchers.io) {
        context.planDataStore.data.first()[existsKey] ?: false
    }

    suspend fun markCreated() = withContext(dispatchers.io) {
        context.planDataStore.edit { it[existsKey] = true }
        Unit
    }

    suspend fun clear() = withContext(dispatchers.io) {
        context.planDataStore.edit { it.clear() }
        Unit
    }
}
