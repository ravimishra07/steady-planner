package com.exam.assistant.focus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.exam.assistant.MainActivity
import com.exam.assistant.R
import com.exam.assistant.domain.FocusSession
import com.exam.assistant.domain.formatFocusClock
import java.util.Date

/**
 * The Focus notifications. No foreground service: the running timer is an
 * ongoing notification with a system-drawn countdown, and the end is an alarm.
 * Every post is skipped silently when notifications are not allowed — the
 * timer itself never depends on them.
 */
class FocusNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun showRunning(session: FocusSession) {
        val endsAt = session.endsAtMs ?: return
        ensureChannels()
        val time = DateFormat.getTimeFormat(context).format(Date(endsAt))
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.focus_notif_running, label(session), time))
            .setContentText(session.block?.subtitle?.takeIf { it.isNotBlank() })
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openFocusIntent())
            .addAction(0, context.getString(R.string.focus_action_pause), actionIntent(FocusActionReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.focus_action_stop), actionIntent(FocusActionReceiver.ACTION_STOP))
        // Safety net if the end alarm is delayed: never leave a countdown below zero for long.
        val untilEnd = endsAt - System.currentTimeMillis()
        if (untilEnd > 0) builder.setTimeoutAfter(untilEnd + TIMEOUT_GRACE_MS)
        post(ID_ONGOING, builder.build())
    }

    fun showPaused(session: FocusSession) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.focus_notif_paused, formatFocusClock(session.remainingSec)))
            .setContentText(label(session))
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openFocusIntent())
            .addAction(0, context.getString(R.string.focus_action_resume), actionIntent(FocusActionReceiver.ACTION_RESUME))
            .addAction(0, context.getString(R.string.focus_action_stop), actionIntent(FocusActionReceiver.ACTION_STOP))
            .build()
        post(ID_ONGOING, notification)
    }

    fun cancelOngoing() {
        manager.cancel(ID_ONGOING)
    }

    fun showComplete(session: FocusSession, withSound: Boolean) {
        ensureChannels()
        val builder = NotificationCompat.Builder(context, if (withSound) CHANNEL_END else CHANNEL_END_SILENT)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.focus_notif_complete))
            .setContentText(context.getString(R.string.focus_notif_complete_body, label(session)))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openFocusIntent())
        // Below Android 8 there are no channels; the builder carries the alert itself.
        if (withSound) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT).setSilent(true)
        }
        post(ID_COMPLETE, builder.build())
    }

    private fun label(session: FocusSession): String =
        session.block?.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.focus_notif_default_title)

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val running = NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.focus_channel_running))
            .setDescription(context.getString(R.string.focus_channel_running_desc))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        val end = NotificationChannelCompat.Builder(CHANNEL_END, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.focus_channel_end))
            .setDescription(context.getString(R.string.focus_channel_end_desc))
            .setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setVibrationEnabled(true)
            .build()
        val endSilent = NotificationChannelCompat.Builder(CHANNEL_END_SILENT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.focus_channel_end_silent))
            .setDescription(context.getString(R.string.focus_channel_end_silent_desc))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .build()
        manager.createNotificationChannelsCompat(listOf(running, end, endSilent))
    }

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(id: Int, notification: Notification) {
        if (!canPost()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun openFocusIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_FOCUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, FocusActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_RUNNING = "focus_running"
        const val CHANNEL_END = "focus_end"
        const val CHANNEL_END_SILENT = "focus_end_silent"
        private const val ID_ONGOING = 4101
        private const val ID_COMPLETE = 4102
        private const val REQUEST_OPEN = 4100
        private const val TIMEOUT_GRACE_MS = 10 * 60_000L
    }
}
