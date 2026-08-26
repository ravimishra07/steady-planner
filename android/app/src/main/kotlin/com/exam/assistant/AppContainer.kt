package com.exam.assistant

import android.content.Context
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.common.DefaultAppDispatchers
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.LocalRemoteConfig
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.RemoteConfig
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

    /**
     * For work that must outlive a screen — a running focus session. Screen-scoped
     * work belongs in viewModelScope. Never GlobalScope.
     */
    val appScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.default)
    }
}
