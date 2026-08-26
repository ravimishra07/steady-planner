package com.steadyline

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.steadyline.core.design.AppTheme
import com.steadyline.core.design.ThemeChoice
import com.steadyline.feature.focus.FocusScreen
import com.steadyline.feature.home.HomeScreen
import com.steadyline.feature.onboarding.OnboardingScreen
import com.steadyline.feature.progress.ProgressScreen
import com.steadyline.feature.settings.SettingsScreen
import com.steadyline.feature.syllabus.SyllabusScreen

@Composable
fun SteadylineNavHost(
    startInOnboarding: Boolean,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentPath = backStackEntry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route.path == currentPath }

    Scaffold(
        containerColor = AppTheme.colors.bg,
        bottomBar = {
            // The bar is only present on tab destinations; onboarding is full screen.
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
            // No cross-fades between tabs: a transition costs frames and buys
            // nothing on a bottom bar. Enter/exit animations belong on
            // hierarchical pushes, which arrive with onboarding.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(Route.Onboarding.path) {
                Column(Modifier.fillMaxSize()) { OnboardingScreen() }
            }
            composable(Route.Home.path) { HomeScreen(Modifier.padding(padding)) }
            composable(Route.Syllabus.path) { SyllabusScreen(Modifier.padding(padding)) }
            composable(Route.Focus.path) { FocusScreen(Modifier.padding(padding)) }
            composable(Route.Progress.path) { ProgressScreen(Modifier.padding(padding)) }
            composable(Route.Settings.path) {
                SettingsScreen(
                    modifier = Modifier.padding(padding),
                    choice = themeChoice,
                    onChoose = onThemeChoice,
                )
            }
        }
    }
}
