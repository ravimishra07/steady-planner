package com.exam.assistant.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing

@Composable
fun MoreScreen(
    planExamLabel: String?,
    daysLeft: Int?,
    background: BackgroundAppearance,
    onBackground: (BackgroundAppearance) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onOpenSettings: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var showThemeSheet by remember { mutableStateOf(false) }
    if (showThemeSheet) {
        ThemeBottomSheet(
            background = background,
            onBackground = onBackground,
            accentPalette = accentPalette,
            onAccentPalette = onAccentPalette,
            onDismiss = { showThemeSheet = false },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        AccountCard(
            title = planExamLabel ?: stringResource(R.string.more_your_plan),
            subtitle = if (planExamLabel != null && daysLeft != null) {
                stringResource(R.string.more_plan_subtitle, planExamLabel, daysLeft)
            } else {
                stringResource(R.string.more_no_plan)
            },
            onClick = onOpenSettings,
        )
        SectionLabel(stringResource(R.string.more_section_plan))
        ProfileGroup {
            ProfileRow(Icons.Outlined.Refresh, R.string.more_redo_onboarding, R.string.more_redo_onboarding_sub, onRedoOnboarding)
        }
        SectionLabel(stringResource(R.string.more_section_app))
        ProfileGroup {
            ProfileRow(Icons.Outlined.Palette, R.string.more_theme, R.string.more_theme_sub, onClick = { showThemeSheet = true })
            HorizontalDivider(color = colors.borderSubtle)
            ProfileRow(Icons.Outlined.Settings, R.string.more_settings, R.string.more_settings_sub, onOpenSettings)
        }
        SectionLabel(stringResource(R.string.more_section_about))
        ProfileGroup {
            ProfileRow(Icons.Outlined.Lock, R.string.more_privacy, R.string.more_privacy_sub) { onOpenPolicy("privacy") }
            HorizontalDivider(color = colors.borderSubtle)
            ProfileRow(Icons.Outlined.Info, R.string.more_terms, R.string.more_terms_sub) { onOpenPolicy("terms") }
            HorizontalDivider(color = colors.borderSubtle)
            ProfileRow(Icons.Outlined.Info, R.string.more_about, R.string.more_about_sub) { onOpenPolicy("about") }
        }
        Text(
            text = stringResource(R.string.more_footer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = Spacing.xl),
        )
    }
}

@Composable
fun PolicyScreen(
    policyId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val (titleRes, bodyRes) = when (policyId) {
        "privacy" -> R.string.policy_privacy_title to R.string.policy_privacy_body
        "terms" -> R.string.policy_terms_title to R.string.policy_terms_body
        else -> R.string.policy_about_title to R.string.policy_about_body
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xxl),
        )
    }
}

@Composable
private fun AccountCard(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.padding(end = Spacing.md),
                shape = CircleShape,
                color = colors.brandContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = colors.onBrandContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ProfileGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = AppTheme.colors.surface,
        shape = RoundedCornerShape(Radius.lg),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
    ) {
        Column(content = content)
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    titleRes: Int,
    subtitleRes: Int,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Surface(
        onClick = onClick,
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(Radius.sm), color = colors.surfaceControl) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.brandDeep,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.md),
            ) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(stringResource(subtitleRes), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(bottom = Spacing.sm, top = Spacing.sm),
    )
}
