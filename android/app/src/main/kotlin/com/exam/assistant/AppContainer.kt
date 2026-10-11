package com.exam.assistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.exam.assistant.core.data.backup.BackupRepository
import kotlinx.coroutines.withContext
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.common.DefaultAppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.FocusController
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.LocalRemoteConfig
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.analytics.FirebaseAppAnalytics
import com.exam.assistant.core.common.Analytics
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.data.RemoteConfig
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StageLabelStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.ChapterStageRepository
import com.exam.assistant.core.data.repo.MigrationRepository
import com.exam.assistant.core.data.repo.MigrationStore
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.PendingSyllabusPick
import com.exam.assistant.feature.onboarding.model.OnboardingStep
import com.exam.assistant.focus.AppFocusEffects
import com.exam.assistant.focus.FocusAlarm
import com.exam.assistant.focus.FocusNotifier
import com.exam.assistant.reminder.DailyReminder
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.today.loadTodaySnapshot
import com.exam.assistant.widget.AppWidgets
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    val syllabusStore: SyllabusStore by lazy { SyllabusStore(context, dispatchers) }

    val focusStore: FocusStore by lazy { FocusStore(context, dispatchers) }

    val studySessionStore: StudySessionStore by lazy { StudySessionStore(context, dispatchers) }

    val remoteConfig: RemoteConfig by lazy { LocalRemoteConfig() }

    /** Firebase Analytics + Crashlytics when google-services.json was present at build time, else a no-op. */
    val analytics: Analytics by lazy { FirebaseAppAnalytics.createOrNoOp(context) }

    /** Pro entitlement (RevenueCat, cached). The SDK is configured on first use, never at startup. */
    val proRepository: ProRepository by lazy {
        ProRepository(
            context, dispatchers, remoteConfig, appScope,
            isDebugBuild = BuildConfig.DEBUG, apiKey = BuildConfig.REVENUECAT_API_KEY, analytics = analytics,
        )
    }

    /** One-shot: something asked for the paywall. The nav host drops it during onboarding and Focus. */
    val paywallRequest = MutableStateFlow(false)

    /** Opens the paywall, unless the paywall is switched off remotely (then nothing is locked). */
    fun openPaywall() {
        if (proRepository.paywallEnabled) paywallRequest.value = true
    }

    /** Set when onboarding finishes; the nav host opens the paywall once the first screen after setup is up. */
    val paywallAfterSetup = MutableStateFlow(false)

    /** The one path for Focus session changes: screen, notification actions, end alarm, boot, cold start. */
    val focusController: FocusController by lazy {
        FocusController(
            focusStore = focusStore,
            attemptRepository = attemptRepository,
            planRepository = planRepository,
            studySessionRepository = studySessionRepository,
            rollingPlanRepository = rollingPlanRepository,
            effects = AppFocusEffects(FocusNotifier(context), FocusAlarm(context), settings),
            analytics = analytics,
        )
    }

    // Canonical Room architecture. Legacy stores remain migration/rollback inputs only.
    val examPackRepository: ExamPackRepository by lazy { ExamPackRepository(context, dispatchers) }
    val attemptRepository: AttemptRepository by lazy { AttemptRepository(context, dispatchers) }
    val planRepository: PlanRepository by lazy { PlanRepository(context, dispatchers, analytics) }
    val studySessionRepository: StudySessionRepository by lazy { StudySessionRepository(context, dispatchers) }
    val topicProgressRepository: TopicProgressRepository by lazy { TopicProgressRepository(context, dispatchers) }
    val revisionRepository: RevisionRepository by lazy { RevisionRepository(context, dispatchers) }
    val chapterStageRepository: ChapterStageRepository by lazy { ChapterStageRepository(context, dispatchers, analytics) }
    val mockTestRepository: MockTestRepository by lazy { MockTestRepository(context, dispatchers, analytics) }
    val stageLabelStore: StageLabelStore by lazy { StageLabelStore(context, dispatchers) }
    val rollingPlanRepository: RollingPlanRepository by lazy {
        RollingPlanRepository(context, dispatchers, examPackRepository, analytics)
    }
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
            migrationStore = migrationStore,
            attemptRepository = attemptRepository,
            examPackRepository = examPackRepository,
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

    /** One-shot: a notification tap asked for the Focus screen. */
    val openFocusRequest = MutableStateFlow(false)

    /** One-shot: the daily reminder tap asked for Today. */
    val openTodayRequest = MutableStateFlow(false)

    /** One-shot: Today's "adjust plan" opens the setup flow on its Plan step. */
    val onboardingStartAt = MutableStateFlow<OnboardingStep?>(null)

    /** Runs the one-time legacy-store migration off the critical path. Safe to call every launch — no-ops once done. */
    fun runMigrationInBackground() {
        appScope.launch {
            migrationRepository.migrateIfNeeded()
            reconcileExpiredFocus()
            rollingPlanRepository.replenish()
            refreshWidgetsOnChange()
        }
        appScope.launch {
            // Settings → "Share anonymous usage data" drives Firebase Analytics and Crashlytics (both persist it).
            settings.shareUsageData.distinctUntilChanged().collect { enabled -> analytics.setCollectionEnabled(enabled) }
        }
        appScope.launch {
            // Restore on launch: picks up a purchase from another device, or a refund.
            runCatching { proRepository.start() }
        }
        appScope.launch {
            // The medium widget locks and unlocks with Pro.
            proRepository.unlocked.drop(1).collect { runCatching { AppWidgets.updateAll(context) } }
        }
    }

    /**
     * Widgets follow today's plan and coverage: any write to either (a replan, a
     * finished session, a ticked chapter) redraws them, debounced. Lives as long
     * as the process; with no widget placed each emission is a cheap no-op.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private suspend fun refreshWidgetsOnChange() {
        attemptRepository.observeActiveAttempt()
            .flatMapLatest { attempt ->
                if (attempt == null) {
                    flowOf(Unit)
                } else {
                    merge(
                        planRepository.observePlanForDate(attempt.id, LocalDate.now()).map { },
                        topicProgressRepository.observeAll(attempt.id).map { },
                    )
                }
            }
            .debounce(WIDGET_REFRESH_DEBOUNCE_MS)
            .collect { runCatching { AppWidgets.updateAll(context) } }
    }

    /**
     * The medium widget's Start: begin today's planned [blockId] in Focus, the
     * way Today's Start does, then open Focus. A session already running or
     * paused is never replaced — Focus just opens on it.
     */
    fun startPlannedBlock(blockId: String) {
        appScope.launch {
            runCatching {
                val current = focusController.load()
                if (current.status == FocusStatus.RUNNING || current.status == FocusStatus.PAUSED) return@runCatching
                val block = loadTodaySnapshot().blocks.firstOrNull { it.id == blockId } ?: return@runCatching
                val seconds = block.plannedMinutes * 60
                focusController.start(
                    FocusSession(
                        status = FocusStatus.RUNNING,
                        durationSec = seconds,
                        remainingSec = seconds,
                        endsAtMs = System.currentTimeMillis() + seconds * 1000L,
                        completedToday = current.completedToday,
                        block = FocusBlockRef(
                            id = block.id,
                            title = block.title.ifBlank { context.getString(R.string.focus_notif_default_title) },
                            subtitle = if (block.isRevision) {
                                context.getString(R.string.focus_revision_title, block.subjectName)
                            } else {
                                block.subjectName
                            },
                            tag = if (block.isRevision) BlockTag.REVISE else BlockTag.READ,
                            sessionId = block.id,
                            nodeKey = block.nodeId,
                            isRevision = block.isRevision,
                        ),
                    ),
                )
            }
            openFocusRequest.value = true
        }
    }

    /** Cold-start recovery: an expired timer earns credit exactly once; a live one gets its alarm and notification back. */
    private suspend fun reconcileExpiredFocus() {
        runCatching { focusController.reconcile() }
    }

    /** BOOT_COMPLETED and MY_PACKAGE_REPLACED: both wipe this app's alarms. */
    suspend fun onBootOrUpdate() {
        runCatching { focusController.reconcile() }
        rearmDailyReminder()
    }

    val dailyReminder: DailyReminder by lazy { DailyReminder(context) }

    val backupRepository: BackupRepository by lazy { BackupRepository(context, dispatchers, attemptRepository, analytics) }

    /**
     * After an import replaced every table and store: drop this device's live
     * timer (its alarm and notification with it), re-arm the reminder from the
     * imported setting, then relaunch so every lazy repository and cached flow
     * starts again on the new data.
     */
    suspend fun onBackupImported() {
        runCatching {
            focusStore.clear()
            focusController.reconcile()
        }
        rearmDailyReminder()
        withContext(dispatchers.main) { restartProcess() }
    }

    /**
     * Settings → Log out. There is no account: this wipes everything on the phone,
     * drops the live timer and reminder, then relaunches into onboarding.
     */
    suspend fun logOut() {
        backupRepository.wipeAll()
        runCatching {
            focusStore.clear()
            studySessionStore.clear()
            syllabusStore.clear()
            focusController.reconcile()
        }
        rearmDailyReminder()
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
        withContext(dispatchers.main) { restartProcess() }
    }

    private fun restartProcess() {
        val intent = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** Arms or cancels the daily "Today's plan" reminder from the saved setting. Boot, update and every settings change. */
    suspend fun rearmDailyReminder() {
        runCatching { dailyReminder.apply(settings.dailyReminderOnce()) }
    }

    private companion object {
        const val WIDGET_REFRESH_DEBOUNCE_MS = 1_000L
    }
}
