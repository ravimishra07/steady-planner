package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.planDataStore by preferencesDataStore(name = "plan")

/**
 * Whether a plan exists and the fields onboarding saves.
 * Read on the critical path is [exists] only; full plan loads after first frame.
 */
class PlanStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val existsKey = booleanPreferencesKey("plan_exists")
    private val examKey = stringPreferencesKey("exam_id")
    private val daysKey = intPreferencesKey("days_until_exam")
    private val workKey = stringPreferencesKey("work_id")
    private val weekdayKey = floatPreferencesKey("weekday_hours")
    private val weekendKey = floatPreferencesKey("weekend_hours")
    private val placeKey = stringPreferencesKey("study_place")
    private val activeSubjectsKey = stringSetPreferencesKey("active_subjects")
    private val blocksDoneKey = stringSetPreferencesKey("blocks_done")

    suspend fun exists(): Boolean = withContext(dispatchers.io) {
        context.planDataStore.data.first()[existsKey] ?: false
    }

    suspend fun save(plan: SavedPlan) = withContext(dispatchers.io) {
        context.planDataStore.edit {
            it[existsKey] = true
            it[examKey] = plan.examId
            it[daysKey] = plan.daysUntilExam
            it[workKey] = plan.workId
            it[weekdayKey] = plan.weekdayHours
            it[weekendKey] = plan.weekendHours
            it[placeKey] = plan.studyPlace
        }
        Unit
    }

    suspend fun load(): SavedPlan? = withContext(dispatchers.io) {
        val prefs = context.planDataStore.data.first()
        if (prefs[existsKey] != true) return@withContext null
        SavedPlan(
            examId = prefs[examKey] ?: return@withContext null,
            daysUntilExam = prefs[daysKey] ?: return@withContext null,
            workId = prefs[workKey] ?: return@withContext null,
            weekdayHours = prefs[weekdayKey] ?: return@withContext null,
            weekendHours = prefs[weekendKey] ?: return@withContext null,
            studyPlace = prefs[placeKey].orEmpty(),
        )
    }

    suspend fun clear() = withContext(dispatchers.io) {
        context.planDataStore.edit { it.clear() }
        Unit
    }

    suspend fun loadTodayPrefs(): TodayPrefs = withContext(dispatchers.io) {
        val prefs = context.planDataStore.data.first()
        TodayPrefs(
            activeSubjects = prefs[activeSubjectsKey].orEmpty(),
            blocksDone = prefs[blocksDoneKey].orEmpty(),
        )
    }

    suspend fun saveActiveSubjects(subjects: Set<String>) = withContext(dispatchers.io) {
        context.planDataStore.edit { it[activeSubjectsKey] = subjects }
        Unit
    }

    suspend fun saveBlocksDone(done: Set<String>) = withContext(dispatchers.io) {
        context.planDataStore.edit { it[blocksDoneKey] = done }
        Unit
    }

    suspend fun updateHours(weekdayHours: Float, weekendHours: Float, studyPlace: String) =
        withContext(dispatchers.io) {
            val prefs = context.planDataStore.data.first()
            if (prefs[existsKey] != true) return@withContext
            context.planDataStore.edit {
                it[weekdayKey] = weekdayHours
                it[weekendKey] = weekendHours
                it[placeKey] = studyPlace
            }
            Unit
        }
}
