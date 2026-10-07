package com.exam.assistant.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.exam.assistant.AppContainer
import com.exam.assistant.SteadylineApp
import kotlinx.coroutines.launch

/** Runs [block] on the app scope while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (AppContainer) -> Unit) {
    val container = (context.applicationContext as SteadylineApp).container
    val pending = goAsync()
    container.appScope.launch {
        try {
            block(container)
        } finally {
            pending.finish()
        }
    }
}

/** Pause, Resume and Stop from the ongoing notification. Not exported: only our own PendingIntents reach it. */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        runAsync(context) { container ->
            val controller = container.focusController
            when (action) {
                ACTION_PAUSE -> controller.pause()
                ACTION_RESUME -> controller.resume()
                // Stop from the shade keeps the time studied; discarding needs the in-app confirmation.
                ACTION_STOP -> controller.stopSavingPartial()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.exam.assistant.focus.PAUSE"
        const val ACTION_RESUME = "com.exam.assistant.focus.RESUME"
        const val ACTION_STOP = "com.exam.assistant.focus.STOP"
    }
}

/** The session-end alarm. Completes an expired session, or re-arms if the stored end moved. Not exported. */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END) return
        runAsync(context) { container -> container.focusController.reconcile() }
    }

    companion object {
        const val ACTION_END = "com.exam.assistant.focus.END"
    }
}

/**
 * Boot and app update clear alarms. Exported only because the system delivers
 * these; both actions are protected broadcasts that no other app can send.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                runAsync(context) { container -> container.onBootOrUpdate() }
        }
    }
}
