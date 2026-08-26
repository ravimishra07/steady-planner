package com.exam.assistant

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
    data object SettingsDetail : Route { override val path = "settings/detail" }
    data object Policy : Route { override val path = "policy/{policyId}" }
}
