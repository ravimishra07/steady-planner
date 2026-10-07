package com.exam.assistant.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.exam.assistant.MainActivity
import com.exam.assistant.R
import com.exam.assistant.SteadylineApp
import com.exam.assistant.core.data.DailyReminderSetting
import com.exam.assistant.today.TodaySnapshot
import com.exam.assistant.today.loadTodaySnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The optional daily "Today's plan" reminder (PRODUCTION_V1_PLAN §10.1).
 *
 * Inexact on purpose: a window alarm needs no exact-alarm grant and lets the
 * system batch it. It fires once and is re-armed for the next day by its
 * receiver, at boot and at every settings change, so a changed time or a
 * changed time zone is picked up without a repeating alarm drifting.
 */
class DailyReminder(private val context: Context) {

    fun apply(setting: DailyReminderSetting) {
        if (setting.enabled) arm(setting.minuteOfDay) else cancel()
    }

    private fun arm(minuteOfDay: Int) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        alarms.setWindow(AlarmManager.RTC_WAKEUP, nextTriggerAt(minuteOfDay), WINDOW_MS, pendingIntent())
    }

    fun cancel() {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent())
        NotificationManagerCompat.from(context).cancel(ID_REMINDER)
    }

    /** Posts the reminder. Skipped silently when notifications are not allowed. */
    @SuppressLint("MissingPermission") // checked in canPost()
    fun show(snapshot: TodaySnapshot, nowMinuteOfDay: Int) {
        if (!canPost()) return
        ensureChannel()
        val upcoming = snapshot.blocks.filter { it.endMinuteOfDay > nowMinuteOfDay }.ifEmpty { snapshot.blocks }
        val text = if (upcoming.isEmpty()) {
            context.getString(R.string.reminder_body_no_plan)
        } else {
            val first = upcoming.first().title.ifBlank { context.getString(R.string.focus_notif_default_title) }
            context.resources.getQuantityString(R.plurals.reminder_body, upcoming.size, upcoming.size, first)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openTodayIntent())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_REMINDER, notification) }
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannelCompat.Builder(CHANNEL_REMINDER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminder_channel))
            .setDescription(context.getString(R.string.reminder_channel_desc))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun openTodayIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, DailyReminderReceiver::class.java).setAction(DailyReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_REMINDER = "daily_plan"
        private const val ID_REMINDER = 4301
        private const val REQUEST_FIRE = 4300
        private const val REQUEST_OPEN = 4302
        private const val WINDOW_MS = 10 * 60_000L

        /** The next time [minuteOfDay] comes round, strictly after now. */
        fun nextTriggerAt(minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            val todayAt = now.toLocalDate().atTime(time)
            val next = if (todayAt.isAfter(now)) todayAt else LocalDate.from(now).plusDays(1).atTime(time)
            return next.atZone(zone).toInstant().toEpochMilli()
        }
    }
}

/** Fires once a day: post the reminder, arm tomorrow. Not exported. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val container = (context.applicationContext as SteadylineApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val setting = container.settings.dailyReminderOnce()
                if (setting.enabled) {
                    val now = LocalTime.now()
                    container.dailyReminder.show(container.loadTodaySnapshot(), now.hour * 60 + now.minute)
                }
                container.dailyReminder.apply(setting)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.exam.assistant.reminder.FIRE"
    }
}
