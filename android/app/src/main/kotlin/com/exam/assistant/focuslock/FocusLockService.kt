package com.exam.assistant.focuslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.exam.assistant.R
import com.exam.assistant.SteadylineApp
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.shouldBlockPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls the foreground app while a study session is active and, if it is one of the
 * student's chosen distractions, brings [BlockingActivity] to the front.
 *
 * Deliberately dumb: it owns no session/settings state itself. Every tick re-reads
 * [AppContainer.focusStore], [AppContainer.focusLockStore] and
 * [AppContainer.focusLockCapabilityChecker] fresh — the service is a poller acting on
 * the single source of truth, not a second copy of it. Started when a session goes
 * RUNNING and stopped when it ends; it does not decide that for itself.
 */
class FocusLockService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (job?.isActive != true) {
            job = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        val container = (application as? SteadylineApp)?.container ?: return
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        while (scope.isActive) {
            runCatching {
                val session = container.focusStore.load().withClockNow()
                val sessionActive = session.status == FocusStatus.RUNNING || session.status == FocusStatus.PAUSED
                if (!sessionActive) {
                    stopSelf()
                    return
                }
                val settings = container.focusLockStore.load()
                val capabilities = container.focusLockCapabilityChecker.current()
                val foregroundPackage = usageStats?.let { currentForegroundPackage(it) }
                if (foregroundPackage != null &&
                    shouldBlockPackage(settings, capabilities, foregroundPackage, packageName, System.currentTimeMillis())
                ) {
                    launchBlockingScreen(foregroundPackage, session)
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun currentForegroundPackage(usageStats: UsageStatsManager): String? {
        val end = System.currentTimeMillis()
        val begin = end - LOOKBACK_MS
        val events = usageStats.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForegroundEvent) lastPackage = event.packageName
        }
        return lastPackage
    }

    private fun launchBlockingScreen(blockedPackage: String, session: FocusSession) {
        val intent = Intent(this, BlockingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(BlockingActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(BlockingActivity.EXTRA_TOPIC_TITLE, session.block?.title.orEmpty())
            putExtra(BlockingActivity.EXTRA_REMAINING_SEC, session.remainingSec)
        }
        startActivity(intent)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.focus_lock_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.focus_lock_notification_title))
            .setContentText(getString(R.string.focus_lock_notification_body))
            .setSmallIcon(R.drawable.ic_focus_lock_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focus_lock"
        private const val NOTIFICATION_ID = 4201
        private const val POLL_INTERVAL_MS = 800L
        private const val LOOKBACK_MS = 10_000L

        fun start(context: Context) {
            context.startService(Intent(context, FocusLockService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusLockService::class.java))
        }
    }
}
