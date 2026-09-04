package com.steadyline.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingShell(
    state: OnboardingUiState,
    title: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Dimens.topBar).padding(horizontal = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Dimens.touch)) {
                Icon(if (state.index == 0) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, if (state.index == 0) "Exit setup" else "Back")
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                state.progressIndex?.let { progress -> repeat(7) { segment ->
                    Box(Modifier.weight(1f).height(Dimens.progress).background(if (segment <= progress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, Shapes.full))
                } } ?: Spacer(Modifier.weight(1f))
                }
            Spacer(Modifier.size(Dimens.touch))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Dimens.lg, end = Dimens.lg, bottom = Dimens.lg),
            verticalArrangement = Arrangement.spacedBy(Dimens.xl),
        ) {
            item { Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Normal) }
            item { Column(verticalArrangement = Arrangement.spacedBy(Dimens.md), content = content) }
        }
        Surface(border = BorderStroke(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant)) {
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Dimens.lg, vertical = Dimens.md).height(Dimens.cta),
                shape = Shapes.full,
            ) { Text(if (state.step == Step.PLAN) "Start day 1" else "Continue", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
fun ChoiceRow(
    choice: Choice,
    selected: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 56.dp,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(horizontal = Dimens.lg, vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.lg)) {
            leadingIcon?.let { Icon(it, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(choice.title, style = MaterialTheme.typography.titleMedium)
                Text(choice.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun SectionLabel(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
