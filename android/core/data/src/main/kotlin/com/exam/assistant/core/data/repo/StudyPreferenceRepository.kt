package com.exam.assistant.core.data.repo

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toDomain
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.SubjectPreferredWindow
import com.exam.assistant.domain.SubjectStudyPreference
import kotlinx.coroutines.withContext

class StudyPreferenceRepository(
    context: Context,
    private val dispatchers: AppDispatchers,
) {
    private val dao = PrepTrackerDatabase.get(context).studyPreferenceDao()

    suspend fun forAttempt(attemptId: String): StudyPreferences? =
        withContext(dispatchers.io) { dao.forAttempt(attemptId)?.toDomain() }

    suspend fun upsert(preferences: StudyPreferences) = withContext(dispatchers.io) { dao.upsert(preferences.toEntity()) }

    suspend fun subjectPreferences(attemptId: String): List<SubjectStudyPreference> =
        withContext(dispatchers.io) { dao.subjectPreferencesFor(attemptId).map { it.toDomain() } }

    suspend fun upsertSubjectPreference(preference: SubjectStudyPreference) = withContext(dispatchers.io) {
        dao.upsertSubject(preference.toEntity())
    }

    suspend fun preferredWindows(attemptId: String): List<SubjectPreferredWindow> =
        withContext(dispatchers.io) { dao.windowsFor(attemptId).map { it.toDomain() } }

    suspend fun upsertWindow(window: SubjectPreferredWindow) = withContext(dispatchers.io) { dao.upsertWindow(window.toEntity()) }
}
