package com.steadyline

/**
 * Every destination in the app, in one place.
 *
 * Features expose an entry composable and nothing else; a feature never
 * navigates to another feature directly. Routing decisions live here so the
 * graph is readable without opening six modules.
 */
sealed interface Route {
    val path: String

    data object Onboarding : Route { override val path = "onboarding" }
    data object Home : Route { override val path = "home" }
    data object Syllabus : Route { override val path = "syllabus" }
    data object Focus : Route { override val path = "focus" }
    data object Progress : Route { override val path = "progress" }
    data object Settings : Route { override val path = "settings" }
}

/** The bottom bar. Order here is order on screen. */
enum class Tab(val route: Route, val label: String) {
    Home(Route.Home, "Today"),
    Syllabus(Route.Syllabus, "Syllabus"),
    Focus(Route.Focus, "Focus"),
    Progress(Route.Progress, "Progress"),
    Settings(Route.Settings, "More"),
}
