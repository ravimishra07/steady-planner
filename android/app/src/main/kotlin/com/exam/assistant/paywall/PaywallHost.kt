package com.exam.assistant.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.feature.settings.PaywallSheet
import com.exam.assistant.feature.settings.PaywallUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaywallViewModel(private val proRepository: ProRepository) : ViewModel() {

    val state: StateFlow<PaywallUiState> =
        combine(proRepository.isPro, proRepository.formattedPrice, proRepository.status) { isPro, price, status ->
            PaywallUiState(price = price, isPro = isPro, status = status)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PaywallUiState())

    init {
        viewModelScope.launch { proRepository.loadProduct() }
    }

    fun buy(activity: Activity) {
        viewModelScope.launch { proRepository.launchPurchase(activity) }
    }

    fun restore() {
        viewModelScope.launch { proRepository.refreshPurchases(userInitiated = true) }
    }

    fun onDismissed() = proRepository.clearStatus()

    class Factory(private val proRepository: ProRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PaywallViewModel(proRepository) as T
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The paywall sheet over whatever screen asked for it. Shown by the nav host, never in onboarding or Focus. */
@Composable
fun PaywallHost(
    proRepository: ProRepository,
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = viewModel(factory = PaywallViewModel.Factory(proRepository)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    PaywallSheet(
        state = state,
        onBuy = { activity?.let(viewModel::buy) },
        onRestore = viewModel::restore,
        onDismiss = {
            viewModel.onDismissed()
            onDismiss()
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
