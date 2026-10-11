package com.exam.assistant.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.exam.assistant.core.common.Analytics
import com.exam.assistant.core.common.AnalyticsEvent
import com.exam.assistant.core.data.ProRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import kotlinx.coroutines.launch

/** Forwards RevenueCat paywall events to [ProRepository]; outlives one showing so a purchase is never dropped. */
class PaywallViewModel(
    private val proRepository: ProRepository,
    private val analytics: Analytics,
) : ViewModel(), PaywallListener {

    /** Null while RevenueCat is being configured; false when it cannot be (no API key). */
    private val _ready = MutableStateFlow<Boolean?>(if (proRepository.isConfigured) true else null)
    val ready: StateFlow<Boolean?> = _ready.asStateFlow()

    init {
        if (_ready.value == null) viewModelScope.launch { _ready.value = proRepository.ensureReady() }
    }

    override fun onPurchaseStarted(rcPackage: Package) = proRepository.onPurchaseStarted()

    override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
        viewModelScope.launch { proRepository.onPurchaseCompleted(customerInfo) }
    }

    override fun onRestoreCompleted(customerInfo: CustomerInfo) {
        viewModelScope.launch { proRepository.onRestoreCompleted(customerInfo) }
    }

    /** Each time the paywall opens. */
    fun onShown() = analytics.log(AnalyticsEvent.PAYWALL_VIEWED)

    class Factory(private val proRepository: ProRepository, private val analytics: Analytics) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PaywallViewModel(proRepository, analytics) as T
    }
}

/**
 * RevenueCat's paywall (designed in the RevenueCat dashboard, offering "default") over whatever screen
 * asked for it. Shown by the nav host, never in onboarding or Focus. If startup has not configured
 * RevenueCat yet, it does so first; with no API key at all it closes at once.
 */
@Composable
fun PaywallHost(
    proRepository: ProRepository,
    analytics: Analytics,
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = viewModel(factory = PaywallViewModel.Factory(proRepository, analytics)),
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    when (ready) {
        null -> return
        false -> {
            LaunchedEffect(Unit) { currentOnDismiss() }
            return
        }
        true -> Unit
    }
    LaunchedEffect(Unit) { viewModel.onShown() }
    val options = remember(viewModel) {
        PaywallDialogOptions.Builder()
            .setListener(viewModel)
            // Legacy templates draw an X from this; dashboard (V2) paywalls carry their own close button.
            .setShouldDisplayDismissButton(true)
            .setDismissRequest { currentOnDismiss() }
            .build()
    }
    PaywallDialog(options)
}
