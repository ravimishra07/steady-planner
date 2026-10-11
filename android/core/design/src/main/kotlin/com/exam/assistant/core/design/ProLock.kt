package com.exam.assistant.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

/**
 * What a free student sees in place of a Pro feature: what it is, and one
 * button that opens the paywall. Callers pass their own strings.
 */
@Composable
fun ProLockCard(
    title: String,
    body: String,
    actionLabel: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onUnlock) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProLockIcon()
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, fontWeight = FontWeight.SemiBold)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            OutlinedButton(onClick = onUnlock, modifier = Modifier.padding(top = Spacing.sm)) { Text(actionLabel) }
        }
    }
}

/** The small lock shown next to a Pro-only choice. Decorative: the control carries the label. */
@Composable
fun ProLockIcon(modifier: Modifier = Modifier) {
    Icon(
        Icons.Outlined.Lock,
        contentDescription = null,
        tint = AppTheme.colors.textMuted,
        modifier = modifier.size(Size.compactIcon),
    )
}
