package com.exam.assistant.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.domain.isProtectedPackage
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Launchable, non-system apps the student could plausibly want blocked. Uses the
 * launcher-intent query, which is exempt from Android's package-visibility
 * restrictions — no QUERY_ALL_PACKAGES permission needed.
 */
class InstalledAppProvider(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) {
    suspend fun launchableApps(): List<InstalledAppInfo> = withContext(dispatchers.default) {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val defaultLauncher = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ownPackage }
            .filter { it != defaultLauncher }
            .filter { !isProtectedPackage(it) }
            .mapNotNull { pkg ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    InstalledAppInfo(
                        packageName = pkg,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
