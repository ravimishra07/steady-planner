package com.exam.assistant.core.data.repo

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.AvailabilityOverride
import com.exam.assistant.domain.WeeklyAvailability
import kotlinx.coroutines.withContext

class AvailabilityRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val dao = PrepTrackerDatabase.get(context).availabilityDao()

    suspend fun weeklyFor(attemptId: String): List<WeeklyAvailability> =
        withContext(dispatchers.io) { dao.weeklyFor(attemptId).map { it.toDomain() } }

    suspend fun overridesFor(attemptId: String): List<AvailabilityOverride> =
        withContext(dispatchers.io) { dao.overridesFor(attemptId).map { it.toDomain() } }

    /** Replaces the whole weekly pattern for [attemptId] — used by onboarding/settings and legacy migration. */
    suspend fun replaceWeekly(attemptId: String, windows: List<WeeklyAvailability>) = withContext(dispatchers.io) {
        dao.deleteWeeklyForAttempt(attemptId)
        dao.upsertWeekly(windows.map { it.toEntity() })
    }

    suspend fun upsertOverride(override: AvailabilityOverride) = withContext(dispatchers.io) {
        dao.upsertOverride(override.toEntity())
    }
}
