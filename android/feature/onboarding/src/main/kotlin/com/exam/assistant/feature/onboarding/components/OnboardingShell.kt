package com.exam.assistant.feature.onboarding.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Motion
import com.exam.assistant.core.design.rememberHaptics
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.feature.onboarding.R
import com.exam.assistant.feature.onboarding.model.OnboardingStep
import com.exam.assistant.feature.onboarding.model.PROGRESS_SEGMENTS

@Composable
internal fun OnboardingShell(
    /** The current step: content slides forward or back when it changes. */
    step: OnboardingStep,
    title: @Composable (OnboardingStep) -> String,
    progressIndex: Int?,
    canGoBack: Boolean,
    ctaLabel: String,
    modifier: Modifier = Modifier,
    continueEnabled: Boolean = true,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)? = null,
    /** Optional controls docked between the content and the CTA (theme step accent picker). */
    bottomContent: (@Composable () -> Unit)? = null,
    content: @Composable (OnboardingStep) -> Unit,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
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
            onBack = {
                haptics.select()
                onBack()
            },
            onSkip = onSkip?.let { skip ->
                {
                    haptics.select()
                    skip()
                }
            },
        )
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f),
            transitionSpec = { stepTransition(forward = targetState.ordinal > initialState.ordinal) },
            label = "onboardingStep",
        ) { shown ->
            // Each step scrolls on its own, from the top.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
            ) {
                Text(
                    text = title(shown),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                    modifier = Modifier.padding(bottom = Spacing.xxl),
                )
                content(shown)
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
        HorizontalDivider(color = colors.border)
        if (bottomContent != null) {
            Box(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md)) {
                bottomContent()
            }
        }
        Button(
            onClick = {
                haptics.confirm()
                onContinue()
            },
            enabled = continueEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.md, bottom = Spacing.xxl)
                .height(Size.ctaHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
    onSkip: (() -> Unit)?,
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
        if (onSkip != null) {
            TextButton(onClick = onSkip, modifier = Modifier.height(Size.touchTarget)) {
                Text(stringResource(R.string.onboarding_skip), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        } else {
            Spacer(Modifier.width(Size.touchTarget))
        }
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
                    .background(
                        animateColorAsState(
                            targetValue = if (index <= progressIndex) colors.brandDeep else colors.elevated,
                            animationSpec = tween(Motion.medium, easing = Motion.emphasizedDecelerate),
                            label = "progressSegment",
                        ).value,
                    ),
            )
        }
    }
}

/** Forward: the next step comes in from the end; back: from the start. Short offset plus fade, per M3 shared axis. */
private fun stepTransition(forward: Boolean): ContentTransform {
    val sign = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(Motion.medium, easing = Motion.emphasizedDecelerate)) { width -> sign * width / 5 } +
        fadeIn(tween(Motion.medium, delayMillis = Motion.short / 2))
    val exit = slideOutHorizontally(tween(Motion.short, easing = Motion.emphasizedAccelerate)) { width -> -sign * width / 5 } +
        fadeOut(tween(Motion.short))
    return enter togetherWith exit
}
