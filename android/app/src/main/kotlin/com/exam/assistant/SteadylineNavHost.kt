package com.exam.assistant

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.exam.assistant.focuslock.FocusLockService
import com.exam.assistant.feature.home.HomeRoute
import com.exam.assistant.feature.onboarding.OnboardingRoute
import com.exam.assistant.feature.progress.ProgressRoute
import com.exam.assistant.feature.settings.MoreRoute
import com.exam.assistant.feature.settings.PolicyScreen
import com.exam.assistant.feature.settings.SettingsDetailRoute
import com.exam.assistant.feature.syllabus.SyllabusRoute

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

    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            if (tab != null && tab != Tab.Focus) {
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
                OnboardingRoute(
                    planStore = container.planStore,
                    onFinished = {
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Onboarding.path) { inclusive = true }
                        }
                    },
                )
            }
            composable(Route.Home.path) {
                HomeRoute(
                    planStore = container.planStore,
                    syllabusRepository = container.syllabusRepository,
                    syllabusStore = container.syllabusStore,
                    studySessionStore = container.studySessionStore,
                    onSetupPlan = { navController.navigate(Route.Onboarding.path) },
                    onStartFocus = { session ->
                        val current = container.focusStore.load()
                        container.focusStore.save(
                            FocusSession(
                                status = FocusStatus.RUNNING,
                                durationSec = session.durationMinutes * 60,
                                remainingSec = session.durationMinutes * 60,
                                endsAtMs = session.runningEndsAtMs,
                                completedToday = current.completedToday,
                                block = FocusBlockRef(
                                    id = session.id,
                                    title = session.title,
                                    subtitle = if (session.isRevision) {
                                        "Revision · ${session.sectionName}"
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
                        FocusLockService.start(context)
                        navController.navigate(Route.Focus.path)
                    },
                    pendingSyllabusPick = container.pendingSyllabusPick,
                    onConsumedSyllabusPick = { container.pendingSyllabusPick.value = null },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(Route.Syllabus.path) {
                SyllabusRoute(
                    examPackRepository = container.examPackRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    attemptRepository = container.attemptRepository,
                    onStartTopic = { pick ->
                        container.pendingSyllabusPick.value = pick
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Home.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(Route.Focus.path) {
                FocusRoute(
                    focusStore = container.focusStore,
                    planStore = container.planStore,
                    settingsStore = container.settings,
                    studySessionStore = container.studySessionStore,
                    syllabusRepository = container.syllabusRepository,
                    examPackRepository = container.examPackRepository,
                    attemptRepository = container.attemptRepository,
                    topicProgressRepository = container.topicProgressRepository,
                    syllabusStore = container.syllabusStore,
                    focusLockStore = container.focusLockStore,
                    focusLockCapabilityChecker = container.focusLockCapabilityChecker,
                    installedAppProvider = container.installedAppProvider,
                    appScope = container.appScope,
                    onClose = {
                        navController.navigate(Route.Home.path) {
                            popUpTo(Route.Home.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onFocusLockStart = { FocusLockService.start(context) },
                    onFocusLockStop = { FocusLockService.stop(context) },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(Route.Progress.path) {
                ProgressRoute(
                    planStore = container.planStore,
                    syllabusRepository = container.syllabusRepository,
                    syllabusStore = container.syllabusStore,
                    studySessionStore = container.studySessionStore,
                    onOpenSettings = { navController.navigate(Route.Settings.path) },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(Route.Settings.path) {
                MoreRoute(
                    planStore = container.planStore,
                    background = background,
                    onBackground = onBackground,
                    accentPalette = accentPalette,
                    onAccentPalette = onAccentPalette,
                    onOpenSettings = { navController.navigate(Route.SettingsDetail.path) },
                    onRedoOnboarding = { navController.navigate(Route.Onboarding.path) },
                    onOpenPolicy = { id -> navController.navigate("policy/$id") },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(Route.SettingsDetail.path) {
                SettingsDetailRoute(
                    planStore = container.planStore,
                    settingsStore = container.settings,
                    focusStore = container.focusStore,
                    syllabusStore = container.syllabusStore,
                    studySessionStore = container.studySessionStore,
                    syllabusRepository = container.syllabusRepository,
                    onBack = { navController.popBackStack() },
                    onCleared = {
                        navController.navigate(Route.Onboarding.path) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(
                route = Route.Policy.path,
                arguments = listOf(navArgument("policyId") { type = NavType.StringType }),
            ) { entry ->
                PolicyScreen(
                    policyId = entry.arguments?.getString("policyId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
