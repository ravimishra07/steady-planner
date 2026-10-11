package com.exam.assistant.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import com.exam.assistant.core.design.Size
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
                onClick = { onSelectExam(exam.id) },
                modifier = Modifier.defaultMinSize(minHeight = Size.ctaHeight),
                titleStyle = MaterialTheme.typography.titleMedium,
                trailing = {
                    androidx.compose.material3.Text(
                        text = stringResource(
                            if (exam.bundledSyllabus) R.string.onboarding_exam_full_syllabus
                            else R.string.onboarding_exam_add_chapters,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            com.exam.assistant.core.design.AppTheme.colors.onSelectionContainer
                        } else {
                            com.exam.assistant.core.design.AppTheme.colors.textSecondary
                        },
                    )
                },
            )
        }
    }
}
