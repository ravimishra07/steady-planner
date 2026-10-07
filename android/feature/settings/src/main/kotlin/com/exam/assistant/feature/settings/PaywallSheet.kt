package com.exam.assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.exam.assistant.core.data.ProStatus
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing

/** Everything the paywall sheet shows. Built by `:app`'s paywall host. */
data class PaywallUiState(
    /** Play's localized price; null falls back to the "₹59 one-time" text. */
    val price: String? = null,
    val isPro: Boolean = false,
    val status: ProStatus = ProStatus.IDLE,
)

/** The Pro paywall (PRODUCTION_V1_PLAN §11.2): what Pro adds, the one-time price, Buy and Restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    state: PaywallUiState,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(if (state.isPro) R.string.paywall_title_owned else R.string.paywall_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.paywall_subtitle), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            PAYWALL_FEATURES.forEach { feature -> FeatureLine(stringResource(feature)) }
            Text(
                stringResource(R.string.paywall_free_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            statusMessage(state.status)?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(top = Spacing.sm))
            }
            if (!state.isPro) {
                val busy = state.status == ProStatus.BUSY
                Button(
                    onClick = onBuy,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(Size.ctaHeight).padding(top = Spacing.sm),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
                ) {
                    Text(
                        if (busy) stringResource(R.string.paywall_working)
                        else stringResource(R.string.paywall_buy, state.price ?: stringResource(R.string.paywall_price_fallback)),
                    )
                }
                TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.paywall_restore))
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.paywall_close)) }
            }
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = AppTheme.colors.success, modifier = Modifier.size(Size.smallIcon))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text)
    }
}

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

private val PAYWALL_FEATURES = listOf(
    R.string.paywall_feature_tests,
    R.string.paywall_feature_mistakes,
    R.string.paywall_feature_insights,
    R.string.paywall_feature_widget,
    R.string.paywall_feature_stage,
)
