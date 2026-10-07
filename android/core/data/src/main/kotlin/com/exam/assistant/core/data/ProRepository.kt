package com.exam.assistant.core.data

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.exam.assistant.core.common.AppDispatchers
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Deliberately not in BackupRepository: an imported file must never grant Pro. Play is the source of truth.
private val Context.proDataStore by preferencesDataStore(name = "pro")

/** What the paywall and Settings → Pro show while billing works. */
enum class ProStatus {
    IDLE,

    /** Talking to Play: connecting, buying or restoring. */
    BUSY,

    /** Play reported the purchase as pending (e.g. cash at a shop). Pro unlocks once it completes. */
    PENDING,

    /** Purchase completed and acknowledged. */
    PURCHASED,

    /** Restore found the purchase. */
    RESTORED,

    /** Restore succeeded but this Google account has not bought Pro. */
    NOTHING_TO_RESTORE,

    /** Play Store missing, signed out, or offline. Cached Pro still works. */
    UNAVAILABLE,

    ERROR,
}

/**
 * Pro (PRODUCTION_V1_PLAN §11.2): one non-consumable, [PRODUCT_ID], ₹59 lifetime.
 *
 * Play Billing is the source of truth; the entitlement is cached in its own
 * DataStore so Pro works offline. A successful purchase query that no longer
 * contains the product (refund, revoke) turns Pro off. Verification is local
 * only: at this price a server is not worth it, and the risk (a patched APK
 * unlocking itself) is accepted and documented.
 *
 * Nothing here runs on the critical path: [refreshPurchases] is launched from
 * AppContainer after the first frame, and the client is built on first use.
 */
class ProRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val remoteConfig: RemoteConfig,
    private val scope: CoroutineScope,
    private val isDebugBuild: Boolean,
) {
    private val isProKey = booleanPreferencesKey("is_pro")
    private val debugForceKey = booleanPreferencesKey("debug_force_pro")
    private val softPromptShownKey = booleanPreferencesKey("soft_prompt_shown")

    private val connectMutex = Mutex()

    private val _status = MutableStateFlow(ProStatus.IDLE)
    val status: StateFlow<ProStatus> = _status.asStateFlow()

    private val _product = MutableStateFlow<ProductDetails?>(null)

    /** Play's localized price for Pro, or null until product details load (or when they can't). */
    val formattedPrice: Flow<String?> = _product.map { it?.oneTimePurchaseOfferDetails?.formattedPrice }

    /** False only when remote config turns the paywall off; then everything is unlocked. */
    val paywallEnabled: Boolean get() = remoteConfig.bool(ConfigKeys.PAYWALL_ENABLED, true)

    /** The cached Play entitlement (plus the debug-only override). */
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

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { onPurchasesUpdated(result, purchases.orEmpty()) }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    /** Connects, retrying with backoff. False when Play billing is not available on this device right now. */
    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        repeat(CONNECT_ATTEMPTS) { attempt ->
            val code = withContext(dispatchers.main) { startConnection() }
            if (code == BillingClient.BillingResponseCode.OK) return@withLock true
            if (code == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) return@withLock false
            delay(CONNECT_BACKOFF_MS * (1L shl attempt))
        }
        false
    }

    private suspend fun startConnection(): Int = suspendCancellableCoroutine { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode)
            }

            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
            }
        })
    }

    /** Loads Pro's product details for the localized price. Safe to call repeatedly. */
    suspend fun loadProduct() {
        if (_product.value != null) return
        runCatching {
            if (!ensureConnected()) return
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build()
            val result = withContext(dispatchers.io) { client.queryProductDetails(params) }
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _product.value = result.productDetailsList?.firstOrNull { it.productId == PRODUCT_ID }
            }
        }
    }

    /**
     * Re-reads what this Google account owns. App start ([userInitiated] false) and
     * Settings → Restore purchase (true). Only a successful query changes the cache,
     * so offline Pro keeps working and a refund or revoke takes Pro away.
     */
    suspend fun refreshPurchases(userInitiated: Boolean = false) {
        if (userInitiated) _status.value = ProStatus.BUSY
        val ok = runCatching {
            if (!ensureConnected()) return@runCatching false
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            val result = withContext(dispatchers.io) { client.queryPurchasesAsync(params) }
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@runCatching false
            val owned = result.purchasesList.filter { PRODUCT_ID in it.products }
            owned.forEach { acknowledgeIfNeeded(it) }
            val hasPro = owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            setPro(hasPro)
            if (userInitiated) {
                _status.value = when {
                    hasPro -> ProStatus.RESTORED
                    owned.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> ProStatus.PENDING
                    else -> ProStatus.NOTHING_TO_RESTORE
                }
            }
            true
        }.getOrDefault(false)
        if (!ok && userInitiated) _status.value = ProStatus.UNAVAILABLE
    }

    /** Opens Play's purchase sheet for Pro. The result arrives through the purchases listener. */
    suspend fun launchPurchase(activity: Activity) {
        _status.value = ProStatus.BUSY
        val launched = runCatching {
            if (!ensureConnected()) return@runCatching false
            loadProduct()
            val details = _product.value ?: return@runCatching false
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
                )
                .build()
            val result = withContext(dispatchers.main) { client.launchBillingFlow(activity, params) }
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> true
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    refreshPurchases(userInitiated = true)
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
        if (!launched) _status.value = ProStatus.UNAVAILABLE
    }

    private suspend fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val pro = purchases.filter { PRODUCT_ID in it.products }
                pro.forEach { acknowledgeIfNeeded(it) }
                when {
                    pro.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                        setPro(true)
                        _status.value = ProStatus.PURCHASED
                    }
                    pro.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> _status.value = ProStatus.PENDING
                    else -> _status.value = ProStatus.IDLE
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _status.value = ProStatus.IDLE
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases(userInitiated = true)
            else -> _status.value = ProStatus.ERROR
        }
    }

    /** Unacknowledged purchases are refunded by Play after three days. Pending ones cannot be acknowledged yet. */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        runCatching { withContext(dispatchers.io) { client.acknowledgePurchase(params) } }
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
        const val PRODUCT_ID = "pro_lifetime"
        const val MIN_ACTIVE_DAYS = 5
        const val MIN_DAYS_INSTALLED = 7L
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_BACKOFF_MS = 1_000L
    }
}
