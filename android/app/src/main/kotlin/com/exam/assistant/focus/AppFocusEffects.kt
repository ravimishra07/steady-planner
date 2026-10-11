package com.exam.assistant.focus

import com.exam.assistant.core.data.FocusEffects
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus

/** Maps a Focus state to its alarm and notifications. Idempotent: safe to apply the same state twice. */
class AppFocusEffects(
    private val notifier: FocusNotifier,
    private val alarm: FocusAlarm,
    private val settings: SettingsStore,
) : FocusEffects {

    override suspend fun onSessionChanged(session: FocusSession) {
        when (session.status) {
            FocusStatus.RUNNING -> {
                val endsAt = session.endsAtMs ?: return
                alarm.arm(endsAt)
                notifier.showRunning(session)
            }
            FocusStatus.PAUSED -> {
                alarm.cancel()
                notifier.showPaused(session)
            }
            FocusStatus.IDLE, FocusStatus.DONE -> {
                alarm.cancel()
                notifier.cancelOngoing()
            }
        }
    }

    override suspend fun onSessionCompleted(session: FocusSession) {
        notifier.showComplete(session, withSound = settings.focusEndAlertOnce())
    }
}
