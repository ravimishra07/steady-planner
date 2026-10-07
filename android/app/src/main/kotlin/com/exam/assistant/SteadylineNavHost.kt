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
import com.exam.assistant.feature.progress.ProgressRoute
import com.exam.assistant.feature.settings.MoreRoute
import com.exam.assistant.feature.settings.AppearanceScreen
import com.exam.assistant.feature.settings.PolicyScreen
import com.exam.assistant.feature.settings.SettingsDetailRoute
import com.exam.assistant.feature.syllabus.SyllabusRoute
import com.exam.assistant.feature.syllabus.OrganiseRoute
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
                    onOpenSettings = { navController.navigate(Route.Settings.path) },
                    onGoToday = { navController.navigate(Route.Home.path) { launchSingleTop = true } },
                    onOpenDay = { date -> navController.navigate(Route.DayLog.pathFor(date)) },
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
