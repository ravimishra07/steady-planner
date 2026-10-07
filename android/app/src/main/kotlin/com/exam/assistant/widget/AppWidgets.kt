package com.exam.assistant.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.exam.assistant.MainActivity
import com.exam.assistant.SteadylineApp
import com.exam.assistant.today.TodaySnapshot
import com.exam.assistant.today.loadTodaySnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The home-screen widgets (PRODUCTION_V1_PLAN §10.3) and when they refresh:
 * on any write to today's plan (replan, session complete — observed in
 * AppContainer), at day start, at the end of the next block, and when the
 * clock or time zone changes. Every path is a no-op while no widget is placed.
 */
object AppWidgets {

    suspend fun hasAny(context: Context): Boolean = runCatching {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(StatusWidget::class.java).isNotEmpty() ||
            manager.getGlanceIds(NextBlockWidget::class.java).isNotEmpty()
    }.getOrDefault(false)

    /** Redraws every placed widget and arms the next refresh tick. */
    suspend fun updateAll(context: Context) {
        if (!hasAny(context)) {
            cancelTick(context)
            return
        }
        runCatching {
            StatusWidget().updateAll(context)
            NextBlockWidget().updateAll(context)
        }
        val container = (context.applicationContext as SteadylineApp).container
        armTick(context, container.loadTodaySnapshot())
    }

    /**
     * One non-waking alarm: the end of the next block today, else the start of
     * tomorrow. Non-waking because a widget only needs to be right when the
     * screen is on; the system delivers it at the next wake.
     */
    fun armTick(context: Context, snapshot: TodaySnapshot, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val nowMinute = now.hour * 60 + now.minute
        val next = snapshot.nextBlock(nowMinute)
            ?.let { now.toLocalDate().atStartOfDay().plusMinutes(it.endMinuteOfDay.toLong()) }
            ?: LocalDate.from(now).plusDays(1).atStartOfDay().plusMinutes(1)
        alarms.set(AlarmManager.RTC, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), tickIntent(context))
    }

    fun cancelTick(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(tickIntent(context))
    }

    /** Opens Focus and starts [blockId] there, through the same intent path as the Focus notification. */
    fun startBlockIntent(context: Context, blockId: String): Intent =
        Intent(context, MainActivity::class.java)
            // Distinct actions keep the two widget PendingIntents from replacing each other's extras.
            .setAction(ACTION_START_BLOCK)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_FOCUS)
            .putExtra(MainActivity.EXTRA_BLOCK_ID, blockId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openTodayIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TODAY)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_TICK,
        Intent(context, WidgetTickReceiver::class.java).setAction(WidgetTickReceiver.ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_TICK = 4400
    private const val ACTION_START_BLOCK = "com.exam.assistant.widget.START_BLOCK"
    private const val ACTION_OPEN_TODAY = "com.exam.assistant.widget.OPEN_TODAY"
}

/**
 * Day start and next-block-end ticks, plus clock and time-zone changes (both
 * exempt from the implicit-broadcast limits). A clock change also moves the
 * daily reminder, so it is re-armed here too.
 */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val container = (context.applicationContext as SteadylineApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (intent.action != ACTION_TICK) container.rearmDailyReminder()
                AppWidgets.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.exam.assistant.widget.TICK"
    }
}
