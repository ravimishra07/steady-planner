package com.exam.assistant

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusBlockRef
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.feature.focus.FocusRoute
import com.exam.assistant.core.data.neetExamDate
import com.exam.assistant.core.data.neetExamDateOfficial
import com.exam.assistant.feature.home.HomeRoute
import com.exam.assistant.feature.onboarding.OnboardingRoute
import com.exam.assistant.feature.progress.DayLogRoute
import com.exam.assistant.feature.progress.LogSessionRoute
import com.exam.assistant.feature.progress.MockFormRoute
import com.exam.assistant.feature.progress.TestsRoute
import com.exam.assistant.feature.progress.ProgressRoute
import com.exam.assistant.feature.settings.MoreRoute
import com.exam.assistant.feature.settings.AppearanceScreen
import com.exam.assistant.feature.settings.PolicyScreen
import com.exam.assistant.feature.settings.SettingsDetailRoute
import com.exam.assistant.feature.syllabus.SyllabusRoute
import com.exam.assistant.feature.syllabus.OrganiseRoute
import com.exam.assistant.paywall.PaywallHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SteadylineNavHost(
    container: AppContainer,
    startInOnboarding: Boolean,
    background: BackgroundAppearance,
    onBackground: (BackgroundAppearance) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentPath = backStackEntry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route.path == currentPath }
    val context = LocalContext.current
    val openFocus by container.openFocusRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openFocus) {
        if (openFocus) {
            container.openFocusRequest.value = false
            // Wait for the graph: on a cold start from the notification it is not set yet.
            navController.currentBackStackEntryFlow.first()
            navController.navigate(Route.Focus.path) { launchSingleTop = true }
        }
    }
    val openToday by container.openTodayRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openToday) {
        if (openToday) {
            container.openTodayRequest.value = false
            val entry = navController.currentBackStackEntryFlow.first()
            // Still in setup there is no Today to show yet.
            if (entry.destination.route != Route.Onboarding.path) {
                navController.navigate(Route.Home.path) {
                    popUpTo(Route.Home.path) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Never over onboarding or a Focus session (§11.2): a request there is dropped, not queued.
    val paywallRequested by container.paywallRequest.collectAsStateWithLifecycle()
    val paywallBlocked = currentPath == null || currentPath == Route.Onboarding.path || currentPath == Route.Focus.path
    LaunchedEffect(paywallRequested, paywallBlocked) {
        if (paywallRequested && paywallBlocked && currentPath != null) container.paywallRequest.value = false
    }
    if (paywallRequested && !paywallBlocked) {
        PaywallHost(proRepository = container.proRepository, analytics = container.analytics, onDismiss = { container.paywallRequest.value = false })
    }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (tab != null) {
                SteadylineBottomBar(
                    selected = tab,
                    onSelect = { target ->
                        navController.navigate(target.route.path) {
                            popUpTo(Route.Home.path) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startInOnboarding) Route.Onboarding.path else Route.Home.path,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(Route.Onboarding.path) {
                val startAtPlan = remember { container.onboardingStartAtPlan.value }
                LaunchedEffect(Unit) { container.onboardingStartAtPlan.value = false }
                OnboardingRoute(
                    planStore = container.planStore,
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    availabilityRepository = container.availabilityRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    planRepository = container.planRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    dispatchers = container.dispatchers,
                    examDate = container.remoteConfig.neetExamDate(),
                    examDateOfficial = container.remoteConfig.neetExamDateOfficial(),
                    startAtPlan = startAtPlan,
                    background = background,
                    onBackground = onBackground,
                    accentPalette = accentPalette,
                    onAccentPalette = onAccentPalette,
                    analytics = container.analytics,
                    onFinished = { openOrganise ->
                        navController.navigate(if (openOrganise) Route.Organise.path else Route.Home.path) {
                            popUpTo(Route.Onboarding.path) { inclusive = true }
                        }
                    },
                )
            }
            composable(Route.Home.path) {
                HomeRoute(
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    availabilityRepository = container.availabilityRepository,
                    planRepository = container.planRepository,
                    studySessionRepository = container.studySessionRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    revisionRepository = container.revisionRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    chapterStageRepository = container.chapterStageRepository,
                    onSetupPlan = { navController.navigate(Route.Onboarding.path) },
                    onAdjustPlan = {
                        container.onboardingStartAtPlan.value = true
                        navController.navigate(Route.Onboarding.path)
                    },
                    onEditPlan = { navController.navigate(Route.Organise.path) },
                    onOpenDay = { date -> navController.navigate(Route.DayLog.pathFor(date)) },
                    onStartFocus = { session ->
                        val current = container.focusController.load()
                        container.focusController.start(
                            FocusSession(
                                status = FocusStatus.RUNNING,
                                durationSec = session.durationMinutes * 60,
                                remainingSec = session.durationMinutes * 60,
                                endsAtMs = session.runningEndsAtMs
                                    ?: (System.currentTimeMillis() + session.durationMinutes * 60_000L),
                                completedToday = current.completedToday,
                                block = FocusBlockRef(
                                    id = session.id,
                                    title = session.title,
                                    subtitle = if (session.isRevision) {
                                        context.getString(R.string.focus_revision_title, session.sectionName)
                                    } else {
                                        session.sectionName
                                    },
                                    tag = if (session.isRevision) BlockTag.REVISE else BlockTag.READ,
                                    sessionId = session.id,
                                    nodeKey = session.nodeKey,
                                    isRevision = session.isRevision,
                                ),
                            ),
                        )
                        navController.navigate(Route.Focus.path)
                    },
                    pendingSyllabusPick = container.pendingSyllabusPick,
                    onConsumedSyllabusPick = { container.pendingSyllabusPick.value = null },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Syllabus.path) {
                SyllabusRoute(
                    examPackRepository = container.examPackRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    attemptRepository = container.attemptRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    chapterStageRepository = container.chapterStageRepository,
                    stageLabelStore = container.stageLabelStore,
                    proRepository = container.proRepository,
                    onOpenPaywall = container::openPaywall,
                    onStartTopic = { pick ->
                        container.pendingSyllabusPick.value = pick
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Home.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOrganise = { navController.navigate(Route.Organise.path) },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Organise.path) {
                OrganiseRoute(
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    planRepository = container.planRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Focus.path) {
                FocusRoute(
                    focusController = container.focusController,
                    settingsStore = container.settings,
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    planRepository = container.planRepository,
                    studySessionRepository = container.studySessionRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    onClose = {
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Home.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Progress.path) {
                ProgressRoute(
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    planRepository = container.planRepository,
                    studySessionRepository = container.studySessionRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    revisionRepository = container.revisionRepository,
                    availabilityRepository = container.availabilityRepository,
                    chapterStageRepository = container.chapterStageRepository,
                    mockTestRepository = container.mockTestRepository,
                    proRepository = container.proRepository,
                    onOpenPaywall = container::openPaywall,
                    onOpenSettings = { navController.navigate(Route.Settings.path) },
                    onGoToday = { navController.navigate(Route.Home.path) { launchSingleTop = true } },
                    onOpenDay = { date -> navController.navigate(Route.DayLog.pathFor(date)) },
                    onLogStudy = { navController.navigate(Route.LogSession.newOn(null)) },
                    onOpenTests = { navController.navigate(Route.Tests.path) },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Settings.path) {
                MoreRoute(
                    planStore = container.planStore,
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    targetSyllabusRepository = container.targetSyllabusRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    chapterStageRepository = container.chapterStageRepository,
                    examDateOfficial = container.remoteConfig.neetExamDateOfficial(),
                    appVersion = BuildConfig.VERSION_NAME,
                    background = background,
                    onOpenAppearance = { navController.navigate(Route.Appearance.path) },
                    onOpenSettings = { navController.navigate(Route.SettingsDetail.path) },
                    onRedoOnboarding = { navController.navigate(Route.Onboarding.path) },
                    onOpenPolicy = { id -> navController.navigate("policy/$id") },
                    proRepository = container.proRepository,
                    isDebugBuild = BuildConfig.DEBUG,
                    onOpenPaywall = container::openPaywall,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Appearance.path) {
                AppearanceScreen(
                    background = background,
                    onBackground = onBackground,
                    accentPalette = accentPalette,
                    onAccentPalette = onAccentPalette,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.SettingsDetail.path) {
                SettingsDetailRoute(
                    planStore = container.planStore,
                    settingsStore = container.settings,
                    focusStore = container.focusStore,
                    syllabusStore = container.syllabusStore,
                    studySessionStore = container.studySessionStore,
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    studyPreferenceRepository = container.studyPreferenceRepository,
                    rollingPlanRepository = container.rollingPlanRepository,
                    configExamDate = container.remoteConfig.neetExamDate(),
                    examDateOfficial = container.remoteConfig.neetExamDateOfficial(),
                    onBack = { navController.popBackStack() },
                    onCleared = {
                        // Clearing all data wiped the session; drop its alarm and notification too.
                        container.appScope.launch { container.focusController.reconcile() }
                        navController.navigate(Route.Onboarding.path) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onReminderChanged = { container.rearmDailyReminder() },
                    backupRepository = container.backupRepository,
                    onImported = { container.appScope.launch { container.onBackupImported() } },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(
                route = Route.DayLog.path,
                arguments = listOf(navArgument(Route.DayLog.ARG) { type = NavType.LongType }),
            ) { entry ->
                DayLogRoute(
                    date = java.time.LocalDate.ofEpochDay(entry.arguments?.getLong(Route.DayLog.ARG) ?: java.time.LocalDate.now().toEpochDay()),
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    studySessionRepository = container.studySessionRepository,
                    planRepository = container.planRepository,
                    onBack = { navController.popBackStack() },
                    onLogStudy = { date -> navController.navigate(Route.LogSession.newOn(date)) },
                    onEditSession = { id -> navController.navigate(Route.LogSession.edit(id)) },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(Route.Tests.path) {
                TestsRoute(
                    attemptRepository = container.attemptRepository,
                    mockTestRepository = container.mockTestRepository,
                    proRepository = container.proRepository,
                    onOpenPaywall = container::openPaywall,
                    onBack = { navController.popBackStack() },
                    onAddMock = { navController.navigate(Route.MockForm.new) },
                    onEditMock = { id -> navController.navigate(Route.MockForm.edit(id)) },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(
                route = Route.MockForm.path,
                arguments = listOf(navArgument(Route.MockForm.ARG) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                MockFormRoute(
                    mockId = entry.arguments?.getString(Route.MockForm.ARG),
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    mockTestRepository = container.mockTestRepository,
                    proRepository = container.proRepository,
                    onOpenPaywall = container::openPaywall,
                    onDone = { navController.popBackStack() },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(
                route = Route.LogSession.path,
                arguments = listOf(
                    navArgument(Route.LogSession.ARG_SESSION) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(Route.LogSession.ARG_DAY) { type = NavType.LongType; defaultValue = Route.LogSession.NO_DAY },
                ),
            ) { entry ->
                val day = entry.arguments?.getLong(Route.LogSession.ARG_DAY) ?: Route.LogSession.NO_DAY
                LogSessionRoute(
                    sessionId = entry.arguments?.getString(Route.LogSession.ARG_SESSION),
                    date = if (day == Route.LogSession.NO_DAY) null else java.time.LocalDate.ofEpochDay(day),
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    studySessionRepository = container.studySessionRepository,
                    onDone = { navController.popBackStack() },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
            composable(
                route = Route.Policy.path,
                arguments = listOf(navArgument("policyId") { type = NavType.StringType }),
            ) { entry ->
                PolicyScreen(
                    policyId = entry.arguments?.getString("policyId").orEmpty(),
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                )
            }
        }
    }
}
