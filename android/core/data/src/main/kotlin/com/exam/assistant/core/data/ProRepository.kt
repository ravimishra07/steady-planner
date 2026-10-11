package com.exam.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.common.Analytics
import com.exam.assistant.core.common.AnalyticsEvent
import com.exam.assistant.core.common.AnalyticsParam
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Deliberately not in BackupRepository: an imported file must never grant Pro. RevenueCat is the source of truth.
private val Context.proDataStore by preferencesDataStore(name = "pro")

/** What Settings → Pro shows while a restore runs. The paywall itself is RevenueCat's. */
enum class ProStatus {
    IDLE,

    /** Talking to RevenueCat: restoring. */
    BUSY,

    /** Kept for Settings' status line; RevenueCat completes pending purchases on its own. */
    PENDING,

    /** A purchase from the paywall completed. */
    PURCHASED,

    /** Restore found Pro. */
    RESTORED,

    /** Restore succeeded but this account has not bought Pro. */
    NOTHING_TO_RESTORE,

    /** No API key, Play Store missing, or offline. Cached Pro still works. */
    UNAVAILABLE,

    ERROR,
}

/**
 * Pro (PRODUCTION_V1_PLAN §11.2) through RevenueCat: the [ENTITLEMENT_ID] entitlement unlocks Pro.
 * Products, price and the paywall design live in the RevenueCat dashboard (offering "default").
 *
 * No account: RevenueCat uses an anonymous app user id. A purchase belongs to the Google Play account,
 * so Restore (Settings or the paywall) brings Pro back after a reinstall or on a new phone. The
 * entitlement is cached in its own DataStore so Pro works offline; every CustomerInfo RevenueCat
 * delivers rewrites the cache, so a refund takes Pro away.
 *
 * Nothing here runs on the critical path: [start] configures the SDK after the first frame,
 * never in Application.onCreate.
 */
class ProRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val remoteConfig: RemoteConfig,
    private val scope: CoroutineScope,
    private val isDebugBuild: Boolean,
    private val apiKey: String,
    private val analytics: Analytics = Analytics.NoOp,
) {
    private val isProKey = booleanPreferencesKey("is_pro")
    private val debugForceKey = booleanPreferencesKey("debug_force_pro")
    private val softPromptShownKey = booleanPreferencesKey("soft_prompt_shown")

    private val configureMutex = Mutex()

    private val _status = MutableStateFlow(ProStatus.IDLE)
    val status: StateFlow<ProStatus> = _status.asStateFlow()

    /** False only when remote config turns the paywall off; then everything is unlocked. */
    val paywallEnabled: Boolean get() = remoteConfig.bool(ConfigKeys.PAYWALL_ENABLED, true)

    /** True once [start] configured the SDK; the paywall can only be shown then. */
    val isConfigured: Boolean get() = Purchases.isConfigured

    /** The cached entitlement (plus the debug-only override). */
    val isPro: Flow<Boolean> = context.proDataStore.data
        .map { prefs -> prefs[isProKey] == true || (isDebugBuild && prefs[debugForceKey] == true) }
        .distinctUntilChanged()

    /** What gates read: Pro, or the paywall switched off remotely. */
    val unlocked: Flow<Boolean> = isPro.map { it || !paywallEnabled }.distinctUntilChanged()

    /** Debug builds only: Settings switch that forces Pro on for testing. */
    val debugForcePro: Flow<Boolean> = context.proDataStore.data.map { isDebugBuild && it[debugForceKey] == true }

    suspend fun unlockedOnce(): Boolean = unlocked.first()

    suspend fun setDebugForcePro(enabled: Boolean) {
        if (!isDebugBuild) return
        withContext(dispatchers.io) { context.proDataStore.edit { it[debugForceKey] = enabled } }
    }

    /** Configures the SDK once, anonymously. False without an API key. */
    private suspend fun ensureConfigured(): Boolean = configureMutex.withLock {
        if (apiKey.isBlank()) return@withLock false
        if (Purchases.isConfigured) return@withLock true
        withContext(dispatchers.main) {
            if (isDebugBuild) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
            Purchases.sharedInstance.updatedCustomerInfoListener =
                UpdatedCustomerInfoListener { info -> scope.launch { applyCustomerInfo(info) } }
        }
        true
    }

    /** The paywall asks for this before showing: configures now if startup has not yet. False without a key. */
    suspend fun ensureReady(): Boolean = ensureConfigured()

    /** After the first frame: configure and re-read the entitlement (picks up a refund or another device's purchase). */
    suspend fun start() {
        if (ensureConfigured()) refreshPurchases()
    }

    /**
     * Re-reads the entitlement. App start ([userInitiated] false) reads CustomerInfo; Settings →
     * Restore purchase (true) asks RevenueCat to restore this Play account's purchases.
     * Only a successful answer changes the cache, so offline Pro keeps working.
     */
    suspend fun refreshPurchases(userInitiated: Boolean = false) {
        if (!Purchases.isConfigured) {
            if (userInitiated) _status.value = ProStatus.UNAVAILABLE
            return
        }
        if (userInitiated) _status.value = ProStatus.BUSY
        runCatching {
            if (userInitiated) Purchases.sharedInstance.awaitRestore() else Purchases.sharedInstance.awaitCustomerInfo()
        }.onSuccess { info ->
            val hasPro = applyCustomerInfo(info)
            if (userInitiated) {
                _status.value = if (hasPro) ProStatus.RESTORED else ProStatus.NOTHING_TO_RESTORE
                analytics.log(AnalyticsEvent.RESTORE_COMPLETED, mapOf(AnalyticsParam.FOUND to hasPro))
            }
        }.onFailure {
            if (userInitiated) _status.value = ProStatus.UNAVAILABLE
        }
    }

    /** The paywall reported a completed purchase. */
    suspend fun onPurchaseCompleted(info: CustomerInfo) {
        applyCustomerInfo(info)
        _status.value = ProStatus.PURCHASED
        analytics.log(AnalyticsEvent.PURCHASE_COMPLETED)
    }

    /** The paywall's own Restore button finished. */
    suspend fun onRestoreCompleted(info: CustomerInfo) {
        val hasPro = applyCustomerInfo(info)
        _status.value = if (hasPro) ProStatus.RESTORED else ProStatus.NOTHING_TO_RESTORE
        analytics.log(AnalyticsEvent.RESTORE_COMPLETED, mapOf(AnalyticsParam.FOUND to hasPro))
    }

    fun onPurchaseStarted() = analytics.log(AnalyticsEvent.PURCHASE_STARTED)

    private suspend fun applyCustomerInfo(info: CustomerInfo): Boolean {
        val hasPro = info.entitlements[ENTITLEMENT_ID]?.isActive == true
        setPro(hasPro)
        return hasPro
    }

    private suspend fun setPro(value: Boolean) = withContext(dispatchers.io) {
        context.proDataStore.edit { it[isProKey] = value }
        Unit
    }

    /** The status line was shown; back to idle. */
    fun clearStatus() {
        _status.value = ProStatus.IDLE
    }

    /**
     * The one soft prompt (§11.2): a free student with at least [MIN_ACTIVE_DAYS]
     * study days, at least [MIN_DAYS_INSTALLED] days after install, shown once ever.
     */
    suspend fun shouldShowSoftPrompt(activeStudyDays: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!paywallEnabled || activeStudyDays < MIN_ACTIVE_DAYS) return false
        if (unlockedOnce()) return false
        val shown = withContext(dispatchers.io) { context.proDataStore.data.first()[softPromptShownKey] == true }
        if (shown) return false
        val installedAt = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrDefault(nowMs)
        return nowMs - installedAt >= MIN_DAYS_INSTALLED * DAY_MS
    }

    suspend fun markSoftPromptShown() = withContext(dispatchers.io) {
        context.proDataStore.edit { it[softPromptShownKey] = true }
        Unit
    }

    companion object {
        /** The RevenueCat entitlement that unlocks Pro (product `lifetime`, one-time). */
        const val ENTITLEMENT_ID = "neet_tracker_pro"
        const val MIN_ACTIVE_DAYS = 5
        const val MIN_DAYS_INSTALLED = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
