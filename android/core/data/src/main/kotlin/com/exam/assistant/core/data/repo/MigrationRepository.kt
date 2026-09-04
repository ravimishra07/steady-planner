package com.exam.assistant.core.data.repo

import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.domain.NEET_EXAM_ID
import kotlinx.coroutines.withContext

/**
 * Enforces the NEET-only product boundary for installs that previously stored
 * another exam. Cross-exam plans cannot be converted safely, so inconsistent
 * or non-NEET state is removed and onboarding rebuilds a valid NEET plan.
 */
class MigrationRepository(
    private val dispatchers: AppDispatchers,
    private val planStore: PlanStore,
    private val studySessionStore: StudySessionStore,
    private val syllabusStore: SyllabusStore,
    private val migrationStore: MigrationStore,
    private val attemptRepository: AttemptRepository,
) {
    companion object {
        const val CURRENT_MIGRATION_VERSION = 2
    }

    suspend fun migrateIfNeeded() = withContext(dispatchers.io) {
        if (migrationStore.currentVersion() >= CURRENT_MIGRATION_VERSION) return@withContext

        val attempt = attemptRepository.activeAttempt()
        val plan = planStore.load()
        val validNeetState = attempt?.examId == NEET_EXAM_ID && plan?.examId == NEET_EXAM_ID

        if (!validNeetState) {
            attempt?.let { attemptRepository.deleteAttemptAndAllData(it.id) }
            planStore.clear()
            syllabusStore.clear()
            studySessionStore.clear()
        }

        migrationStore.markComplete(CURRENT_MIGRATION_VERSION)
        attemptRepository.refreshHasAttemptFlag()
    }
}
