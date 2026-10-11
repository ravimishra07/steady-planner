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
    data object Organise : Route { override val path = "organise" }
    data object Focus : Route { override val path = "focus" }
    data object Progress : Route { override val path = "progress" }
    data object Settings : Route { override val path = "settings" }
    data object Appearance : Route { override val path = "settings/appearance" }
    data object SettingsDetail : Route { override val path = "settings/detail" }
    data object Policy : Route { override val path = "policy/{policyId}" }

    /** "What did I study" for one day; the argument is the date's epoch day. */
    data object DayLog : Route {
        const val ARG = "epochDay"
        override val path = "day/{$ARG}"
        fun pathFor(date: java.time.LocalDate) = "day/${date.toEpochDay()}"
    }

    /** Log an offline session, or edit one when [ARG_SESSION] is set (PRODUCTION_V1_PLAN §6.3). */
    data object LogSession : Route {
        const val ARG_SESSION = "sessionId"
        const val ARG_DAY = "epochDay"
        override val path = "log-session?$ARG_SESSION={$ARG_SESSION}&$ARG_DAY={$ARG_DAY}"
        fun newOn(date: java.time.LocalDate?) = "log-session?$ARG_DAY=${date?.toEpochDay() ?: NO_DAY}"
        fun edit(sessionId: String) = "log-session?$ARG_SESSION=$sessionId"
        const val NO_DAY = Long.MIN_VALUE
    }

    /** The mock and test log (PRODUCTION_V1_PLAN §8.1). */
    data object Tests : Route { override val path = "tests" }

    /** Add a mock, or edit one when [ARG] is set. */
    data object MockForm : Route {
        const val ARG = "mockId"
        override val path = "mock-form?$ARG={$ARG}"
        val new = "mock-form"
        fun edit(mockId: String) = "mock-form?$ARG=$mockId"
    }
}
