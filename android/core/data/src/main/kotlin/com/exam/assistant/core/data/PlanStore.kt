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
    private val targetDateKey = stringPreferencesKey("target_date_epoch_day")
    private val coachingKey = stringPreferencesKey("coaching_id")
    private val wakeMinuteKey = intPreferencesKey("wake_minute")
    private val sleepMinuteKey = intPreferencesKey("sleep_minute")
    private val useProvidedSyllabusKey = booleanPreferencesKey("use_provided_syllabus")
    private val coveredSubjectIdsKey = stringSetPreferencesKey("covered_subject_ids")
    private val commitmentsKey = stringSetPreferencesKey("fixed_commitments")

    suspend fun exists(): Boolean = withContext(dispatchers.io) {
        context.planDataStore.data.first()[existsKey] ?: false
    }

    /** A stale plan for another product must never route into the NEET app. */
    suspend fun existsForExam(examId: String): Boolean = withContext(dispatchers.io) {
        val prefs = context.planDataStore.data.first()
        prefs[existsKey] == true && prefs[examKey] == examId
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
            plan.targetDateEpochDay?.let { epochDay -> it[targetDateKey] = epochDay.toString() }
            it[coachingKey] = plan.coachingId
            it[wakeMinuteKey] = plan.wakeMinute
            it[sleepMinuteKey] = plan.sleepMinute
            it[useProvidedSyllabusKey] = plan.useProvidedSyllabus
            it[coveredSubjectIdsKey] = plan.coveredSubjectIds
            it[commitmentsKey] = plan.commitments
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
            targetDateEpochDay = prefs[targetDateKey]?.toLongOrNull(),
            coachingId = prefs[coachingKey].orEmpty(),
            wakeMinute = prefs[wakeMinuteKey] ?: 6 * 60,
            sleepMinute = prefs[sleepMinuteKey] ?: 23 * 60,
            useProvidedSyllabus = prefs[useProvidedSyllabusKey] ?: true,
            coveredSubjectIds = prefs[coveredSubjectIdsKey].orEmpty(),
            commitments = prefs[commitmentsKey].orEmpty(),
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
