package com.exam.assistant

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.common.DefaultAppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.FocusLockCapabilityChecker
import com.exam.assistant.core.data.FocusLockStore
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.InstalledAppProvider
import com.exam.assistant.core.data.LocalRemoteConfig
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.RemoteConfig
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.MigrationRepository
import com.exam.assistant.core.data.repo.MigrationStore
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.PendingSyllabusPick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The whole dependency graph, on one screen.
 *
 * Manual rather than Hilt: every field is `by lazy`, so constructing this costs
 * nothing and nothing is built until first use. There is no generated code to
 * read around, and this app has one Activity and no dynamic feature modules —
 * the problems a DI framework solves are not present.
 *
 * If this grows past roughly twenty entries, revisit and record the decision in
 * ARCHITECTURE.md.
 */
class AppContainer(private val context: Context) {

    val dispatchers: AppDispatchers by lazy { DefaultAppDispatchers() }

    val settings: SettingsStore by lazy { SettingsStore(context, dispatchers) }

    val planStore: PlanStore by lazy { PlanStore(context, dispatchers) }

    val syllabusRepository: SyllabusRepository by lazy { SyllabusRepository(context, dispatchers) }

    val syllabusStore: SyllabusStore by lazy { SyllabusStore(context, dispatchers) }

    val focusStore: FocusStore by lazy { FocusStore(context, dispatchers) }

    val studySessionStore: StudySessionStore by lazy { StudySessionStore(context, dispatchers) }

    val remoteConfig: RemoteConfig by lazy { LocalRemoteConfig() }

    val focusLockStore: FocusLockStore by lazy { FocusLockStore(context, dispatchers) }

    val focusLockCapabilityChecker: FocusLockCapabilityChecker by lazy { FocusLockCapabilityChecker(context) }

    val installedAppProvider: InstalledAppProvider by lazy { InstalledAppProvider(context, dispatchers) }

    // New domain/data architecture (replaces planStore/studySessionStore/syllabusStore as
    // the live source of truth once every feature is rewired — see HANDOFF.md).
    val examPackRepository: ExamPackRepository by lazy { ExamPackRepository(context, dispatchers) }
    val attemptRepository: AttemptRepository by lazy { AttemptRepository(context, dispatchers) }
    val planRepository: PlanRepository by lazy { PlanRepository(context, dispatchers) }
    val studySessionRepository: StudySessionRepository by lazy { StudySessionRepository(context, dispatchers) }
    val topicProgressRepository: TopicProgressRepository by lazy { TopicProgressRepository(context, dispatchers) }
    val revisionRepository: RevisionRepository by lazy { RevisionRepository(context, dispatchers) }
    val targetSyllabusRepository: TargetSyllabusRepository by lazy { TargetSyllabusRepository(context, dispatchers) }
    val availabilityRepository: AvailabilityRepository by lazy { AvailabilityRepository(context, dispatchers) }
    val studyPreferenceRepository: StudyPreferenceRepository by lazy { StudyPreferenceRepository(context, dispatchers) }
    private val migrationStore: MigrationStore by lazy { MigrationStore(context, dispatchers) }
    val migrationRepository: MigrationRepository by lazy {
        MigrationRepository(
            context = context,
            dispatchers = dispatchers,
            planStore = planStore,
            studySessionStore = studySessionStore,
            syllabusStore = syllabusStore,
            legacySyllabusRepository = syllabusRepository,
            examPackRepository = examPackRepository,
            migrationStore = migrationStore,
            attemptRepository = attemptRepository,
        )
    }

    /**
     * For work that must outlive a screen — a running focus session. Screen-scoped
     * work belongs in viewModelScope. Never GlobalScope.
     */
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.default)
    }

    val pendingSyllabusPick = MutableStateFlow<PendingSyllabusPick?>(null)

    /** Runs the one-time legacy-store migration off the critical path. Safe to call every launch — no-ops once done. */
    fun runMigrationInBackground() {
        appScope.launch { migrationRepository.migrateIfNeeded() }
    }
}
