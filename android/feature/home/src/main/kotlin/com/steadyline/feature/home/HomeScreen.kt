package com.steadyline.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.steadyline.core.design.AppTheme
import com.steadyline.core.design.AppType
import com.steadyline.core.design.Spacing

/**
 * Placeholder. Phase 0 only proves the shell, navigation and theme; the real
 * screen lands in its own phase.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Home", style = AppType.title, color = AppTheme.colors.text)
        Text(
            "Placeholder — this screen is built in its own phase.",
            style = AppType.md,
            color = AppTheme.colors.textMuted,
        )
    }
}
