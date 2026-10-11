package com.exam.assistant.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.data.ProStatus

// The paywall itself is RevenueCat's (:app PaywallHost). Settings → Pro still reports restore results.

@Composable
internal fun statusMessage(status: ProStatus): String? = when (status) {
    ProStatus.PENDING -> stringResource(R.string.pro_status_pending)
    ProStatus.PURCHASED -> stringResource(R.string.pro_status_purchased)
    ProStatus.RESTORED -> stringResource(R.string.pro_status_restored)
    ProStatus.NOTHING_TO_RESTORE -> stringResource(R.string.pro_status_nothing)
    ProStatus.UNAVAILABLE -> stringResource(R.string.pro_status_unavailable)
    ProStatus.ERROR -> stringResource(R.string.pro_status_error)
    ProStatus.IDLE, ProStatus.BUSY -> null
}
