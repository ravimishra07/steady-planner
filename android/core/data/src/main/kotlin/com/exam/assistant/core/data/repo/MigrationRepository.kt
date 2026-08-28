package com.exam.assistant.core.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.db.PrepTrackerDatabase
import com.exam.assistant.core.data.db.toEntity
import com.exam.assistant.domain.LEGACY_ATTEMPT_ID
import com.exam.assistant.domain.buildLegacyNodeIdMap
import com.exam.assistant.domain.buildLegacySectionIdMap
import com.exam.assistant.domain.defaultWeeklyAvailability
import com.exam.assistant.domain.migrateExamAttempt
import com.exam.assistant.domain.migrateStudyPreferences
import com.exam.assistant.domain.migrateStudySessionRecord
import com.exam.assistant.domain.migrateTargetOverrides
import com.exam.assistant.domain.migrateTopicProgress
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One-time legacy DataStore -> Room migration (spec §28-31). Legacy stores
 * are read-only here and never cleared — they stay on disk, unused, until
 * every feature has been rewired off them (a later checkpoint deletes their
 * live AppContainer wiring, not this repository's job).
 *
 * Idempotent by construction: every id this produces is deterministic from
 * the legacy record's own id, so re-running upserts the same rows rather
 * than duplicating. [MigrationStore]'s version marker is a fast-path, not
 * the sole idempotency guarantee.
 */
class MigrationRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val planStore: PlanStore,
    private val studySessionStore: StudySessionStore,
    private val syllabusStore: SyllabusStore,
    private val legacySyllabusRepository: SyllabusRepository,
    private val examPackRepository: ExamPackRepository,
    private val migrationStore: MigrationStore,
    private val attemptRepository: AttemptRepository,
) {
    private val db = PrepTrackerDatabase.get(context)

    companion object {
        const val CURRENT_MIGRATION_VERSION = 1
    }

    suspend fun migrateIfNeeded() = withContext(dispatchers.io) {
        if (migrationStore.currentVersion() >= CURRENT_MIGRATION_VERSION) return@withContext
        val plan = planStore.load() ?: run {
            // Nothing to migrate — fresh install. Mark done so this check is skipped from now on.
            migrationStore.markComplete(CURRENT_MIGRATION_VERSION)
            return@withContext
        }

        val examPack = examPackRepository.examPack()
        val legacySections = legacySyllabusRepository.tier1Sections()
        val nodeIdMap = buildLegacyNodeIdMap(examPack, legacySections)
        val sectionIdMap = buildLegacySectionIdMap(examPack)
        val storedSyllabus = syllabusStore.load()
        val legacySessions = studySessionStore.loadAll()

        val nowMs = System.currentTimeMillis()
        val attempt = migrateExamAttempt(
            examId = plan.examId,
            daysUntilExam = plan.daysUntilExam,
            migrationDate = LocalDate.now(),
            nowMs = nowMs,
        ).copy(syllabusVersion = examPack.syllabusVersion)

        val preferences = migrateStudyPreferences(LEGACY_ATTEMPT_ID, plan.studyPlace)
        val weeklyAvailability = defaultWeeklyAvailability(LEGACY_ATTEMPT_ID, plan.weekdayHours, plan.weekendHours)
        val topicProgress = migrateTopicProgress(LEGACY_ATTEMPT_ID, storedSyllabus.doneLeaves, nodeIdMap, nowMs)
        val targetOverrides = migrateTargetOverrides(LEGACY_ATTEMPT_ID, storedSyllabus.excludedSectionKeys, sectionIdMap, nowMs)
        val recordMigrations = legacySessions.map { migrateStudySessionRecord(it, LEGACY_ATTEMPT_ID, nodeIdMap, nowMs) }

        db.withTransaction {
            db.examAttemptDao().upsert(attempt.toEntity())
            db.studyPreferenceDao().upsert(preferences.toEntity())
            db.availabilityDao().deleteWeeklyForAttempt(LEGACY_ATTEMPT_ID)
            db.availabilityDao().upsertWeekly(weeklyAvailability.map { it.toEntity() })
            db.topicProgressDao().upsertAll(topicProgress.map { it.toEntity() })
            targetOverrides.forEach { db.targetNodeOverrideDao().upsert(it.toEntity()) }
            recordMigrations.forEach { migration ->
                db.studyPlanBlockDao().upsert(migration.planBlock.toEntity())
                migration.session?.let { db.studySessionDao().upsert(it.toEntity()) }
            }
        }

        migrationStore.markComplete(CURRENT_MIGRATION_VERSION)
        attemptRepository.refreshHasAttemptFlag()
    }
}
