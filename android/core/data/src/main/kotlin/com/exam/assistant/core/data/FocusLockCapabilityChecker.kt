package com.exam.assistant.core.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.exam.assistant.domain.FocusLockCapabilities

/**
 * Reads real, current Android permission state. Never cached — both permissions are
 * special ones the user grants outside the app and can revoke at any time, so every
 * read goes straight to the system.
 */
class FocusLockCapabilityChecker(private val context: Context) {

    fun current(): FocusLockCapabilities = FocusLockCapabilities(
        usageAccessGranted = hasUsageAccess(),
        overlayGranted = Settings.canDrawOverlays(context),
    )

    fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
