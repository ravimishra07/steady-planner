package com.exam.assistant.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.exam.assistant.core.design.AppType
import com.exam.assistant.core.design.Spacing

@Composable
internal fun OnboardingExamStep(
    selectedExamId: String,
    onSelectExam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ExamCatalog.options.forEach { exam ->
            val selected = exam.id == selectedExamId
            OnboardingSelectableCard(
                title = stringResource(exam.labelRes),
                selected = selected,
                enabled = exam.available,
                onClick = { onSelectExam(exam.id) },
                modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                titleStyle = AppType.lg,
            )
        }
    }
}
