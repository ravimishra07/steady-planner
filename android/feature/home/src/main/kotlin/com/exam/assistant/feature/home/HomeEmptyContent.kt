package com.exam.assistant.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing

@Composable
internal fun HomeEmptyContent(
    onSetupPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxl),
        )
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.home_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Button(
                    onClick = onSetupPlan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.ctaHeight),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandDeep,
                        contentColor = colors.onBrand,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_setup_plan),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
