package com.exam.assistant.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService

/**
 * The session-end alarm. Exact when the system allows it; otherwise the
 * inexact idle-allowed alarm, which Doze may delay by a few minutes. The
 * session is still credited correctly either way — completion uses ends_at,
 * not the time the alarm fires.
 */
class FocusAlarm(private val context: Context) {

    fun arm(endsAtMs: Long) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent()
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pending)
            }
        } catch (_: SecurityException) {
            // Exact-alarm access was revoked between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pending)
        }
    }

    fun cancel() {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_END,
        Intent(context, FocusAlarmReceiver::class.java).setAction(FocusAlarmReceiver.ACTION_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_END = 4200
    }
}
