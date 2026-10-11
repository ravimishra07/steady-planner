package com.exam.assistant.domain

/** Minutes a "Need access?" tap temporarily unblocks one app for. */
const val FOCUS_LOCK_TEMP_ALLOWANCE_MINUTES = 5

/**
 * What the student has configured. Persisted verbatim (see `FocusLockStore`).
 *
 * [temporaryAllowances] maps package name -> epoch-millis when the temporary
 * unblock granted from the blocking screen expires.
 */
data class FocusLockSettings(
    val enabled: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val temporaryAllowances: Map<String, Long> = emptyMap(),
) {
    /** The student has picked at least one app — setup is past the "choose apps" step. */
    val configured: Boolean get() = blockedPackages.isNotEmpty()
}

/** Real, freshly-read Android permission state — never assumed, never cached across a screen. */
data class FocusLockCapabilities(
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
) {
    val allGranted: Boolean get() = usageAccessGranted && overlayGranted
}

data class ActiveStudySessionInfo(
    val topicTitle: String,
    val remainingSec: Int,
)

/** What the Focus tab should show. One function, one source of truth for the whole screen. */
sealed interface FocusLockDisplayState {
    data object Off : FocusLockDisplayState
    data object NeedsSetup : FocusLockDisplayState
    data object NeedsAttention : FocusLockDisplayState
    data class Ready(val blockedCount: Int) : FocusLockDisplayState
    data class Active(val blockedCount: Int, val session: ActiveStudySessionInfo) : FocusLockDisplayState
}

/**
 * focusLockActive = enabled AND configured AND capabilities granted AND a session is running.
 * Anything less than all four and the student sees why, not a silently-broken blocker.
 */
fun focusLockDisplayState(
    settings: FocusLockSettings,
    capabilities: FocusLockCapabilities,
    activeSession: ActiveStudySessionInfo?,
): FocusLockDisplayState = when {
    !settings.enabled -> FocusLockDisplayState.Off
    !settings.configured -> FocusLockDisplayState.NeedsSetup
    !capabilities.allGranted -> FocusLockDisplayState.NeedsAttention
    activeSession != null -> FocusLockDisplayState.Active(settings.blockedPackages.size, activeSession)
    else -> FocusLockDisplayState.Ready(settings.blockedPackages.size)
}

fun isTemporarilyAllowed(settings: FocusLockSettings, packageName: String, nowMs: Long): Boolean {
    val expiry = settings.temporaryAllowances[packageName] ?: return false
    return nowMs < expiry
}

/** The single predicate the blocking service acts on. */
fun shouldBlockPackage(
    settings: FocusLockSettings,
    capabilities: FocusLockCapabilities,
    packageName: String,
    ownPackageName: String,
    nowMs: Long,
): Boolean {
    if (!settings.enabled || !capabilities.allGranted) return false
    if (packageName == ownPackageName) return false
    if (packageName !in settings.blockedPackages) return false
    if (isTemporarilyAllowed(settings, packageName, nowMs)) return false
    return true
}

fun withTemporaryAllowance(settings: FocusLockSettings, packageName: String, nowMs: Long): FocusLockSettings =
    settings.copy(
        temporaryAllowances = settings.temporaryAllowances + (packageName to nowMs + FOCUS_LOCK_TEMP_ALLOWANCE_MINUTES * 60_000L),
    )

fun clearExpiredAllowances(settings: FocusLockSettings, nowMs: Long): FocusLockSettings {
    val stillValid = settings.temporaryAllowances.filterValues { it > nowMs }
    return if (stillValid.size == settings.temporaryAllowances.size) settings else settings.copy(temporaryAllowances = stillValid)
}

/**
 * Packages that must never be blockable regardless of what the student picks — recovery
 * surfaces and phone/emergency functionality. Best-effort by design: the launcher and our
 * own package are excluded dynamically (via PackageManager) where this list is applied.
 */
val FOCUS_LOCK_PROTECTED_PACKAGE_PREFIXES = listOf(
    "com.android.systemui",
    "com.android.settings",
    "com.android.phone",
    "com.android.server.telecom",
    "com.android.dialer",
    "com.android.emergency",
    "com.google.android.dialer",
    "com.google.android.permissioncontroller",
    "com.android.permissioncontroller",
    "com.android.providers",
)

fun isProtectedPackage(packageName: String): Boolean =
    FOCUS_LOCK_PROTECTED_PACKAGE_PREFIXES.any { packageName == it || packageName.startsWith("$it.") }

/**
 * Common distraction package names, offered as a "Suggested" shortcut in the app
 * picker. Best-effort and hardcoded — there is no on-device signal for "popular app"
 * — so this only ever surfaces entries the student actually has installed.
 */
val FOCUS_LOCK_SUGGESTED_PACKAGES = listOf(
    "com.instagram.android",
    "com.whatsapp",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.google.android.youtube",
    "com.facebook.katana",
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "org.telegram.messenger",
    "com.netflix.mediaclient",
    "com.spotify.music",
    "com.pinterest",
    "com.discord",
    "com.linkedin.android",
)
