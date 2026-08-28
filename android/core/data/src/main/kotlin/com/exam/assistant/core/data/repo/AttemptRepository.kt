package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.ExamAttempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.attemptFlagDataStore by preferencesDataStore(name = "attempt_flag")
private val hasAttemptKey = booleanPreferencesKey("has_attempt")

/** The exam attempt is the central object everything else hangs off. */
class AttemptRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val db = PrepTrackerDatabase.get(context)
    private val dao = db.examAttemptDao()

    /**
     * A tiny DataStore boolean, not a Room query — startup reads this to
     * decide onboarding vs Home before the first frame, without paying for
     * Room's init cost on the critical path (spec §8).
     */
    suspend fun hasAttemptFast(): Boolean = withContext(dispatchers.io) {
        context.attemptFlagDataStore.data.first()[hasAttemptKey] ?: false
    }

    private suspend fun markHasAttempt(value: Boolean) = withContext(dispatchers.io) {
        context.attemptFlagDataStore.edit { it[hasAttemptKey] = value }
    }

    /** Re-syncs the fast flag from Room — call after a write that bypassed [upsert], e.g. migration. */
    suspend fun refreshHasAttemptFlag() {
        markHasAttempt(activeAttempt() != null)
    }

    fun observeActiveAttempt(): Flow<ExamAttempt?> = dao.observeActive().map { it?.toDomain() }

    suspend fun activeAttempt(): ExamAttempt? = withContext(dispatchers.io) { dao.activeOnce()?.toDomain() }

    suspend fun upsert(attempt: ExamAttempt) = withContext(dispatchers.io) {
        dao.upsert(attempt.toEntity())
        markHasAttempt(true)
    }

    suspend fun byId(id: String): ExamAttempt? = withContext(dispatchers.io) { dao.byId(id)?.toDomain() }

    /** Deletes every attempt-scoped row across every table, atomically. Used by "clear plan". */
    suspend fun deleteAttemptAndAllData(attemptId: String) = withContext(dispatchers.io) {
        db.withTransaction {
            db.studySessionDao().deleteAllForAttempt(attemptId)
            db.studyPlanBlockDao().deleteForAttempt(attemptId)
            db.topicProgressDao().deleteForAttempt(attemptId)
            db.revisionStateDao().deleteForAttempt(attemptId)
            db.targetNodeOverrideDao().deleteForAttempt(attemptId)
            db.availabilityDao().deleteWeeklyForAttempt(attemptId)
            db.availabilityDao().deleteOverridesForAttempt(attemptId)
            db.studyPreferenceDao().deletePreferencesForAttempt(attemptId)
            db.studyPreferenceDao().deleteSubjectPreferencesForAttempt(attemptId)
            db.studyPreferenceDao().deleteWindowsForAttempt(attemptId)
            dao.deleteAttempt(attemptId)
        }
        markHasAttempt(false)
    }
}
