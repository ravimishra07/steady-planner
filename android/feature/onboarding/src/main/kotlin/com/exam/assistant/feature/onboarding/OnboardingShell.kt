package com.exam.assistant.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Size

@Composable
internal fun OnboardingShell(
    title: String,
    progressIndex: Int?,
    canGoBack: Boolean,
    ctaLabel: String,
    modifier: Modifier = Modifier,
    continueEnabled: Boolean = true,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        OnboardingTopBar(
            progressIndex = progressIndex,
            canGoBack = canGoBack,
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
            .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.text,
                modifier = Modifier.padding(bottom = Spacing.xxl),
            )
            content()
            Spacer(Modifier.height(Spacing.xxl))
        }
        HorizontalDivider(color = colors.border)
        Button(
            onClick = onContinue,
            enabled = continueEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.md, bottom = Spacing.xxl)
                .height(Size.ctaHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.brandDeep,
                contentColor = colors.onBrand,
                disabledContainerColor = colors.surfaceControl,
                disabledContentColor = colors.textDisabled,
            ),
        ) {
            Text(text = ctaLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OnboardingTopBar(
    progressIndex: Int?,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.topAppBarHeight)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(Size.touchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                    tint = colors.text,
                )
            }
        } else {
            Spacer(Modifier.width(Size.touchTarget))
        }
        if (progressIndex != null) {
            OnboardingProgressBar(
                progressIndex = progressIndex,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(Size.touchTarget))
    }
}

@Composable
private fun OnboardingProgressBar(
    progressIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier.height(Size.progressHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(PROGRESS_SEGMENTS) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Size.progressHeight)
                    .clip(CircleShape)
                    .background(if (index <= progressIndex) colors.brandDeep else colors.elevated),
            )
        }
    }
}
