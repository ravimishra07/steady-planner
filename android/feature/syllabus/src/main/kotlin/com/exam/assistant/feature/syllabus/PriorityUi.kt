package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.ChapterPriority
import com.exam.assistant.domain.PriorityBand
import java.util.Locale

/** What the student taps to see where a chapter's yield comes from. */
internal data class PriorityDetail(val chapterTitle: String, val priority: ChapterPriority)

private fun bandLabel(band: PriorityBand): Int = when (band) {
    PriorityBand.HIGH -> R.string.syllabus_yield_high
    PriorityBand.MEDIUM -> R.string.syllabus_yield_medium
    PriorityBand.LOW -> R.string.syllabus_yield_low
    PriorityBand.NO_DATA -> R.string.syllabus_yield_no_data
}

/** "2021–25": the range of years the counts cover. */
internal fun pyqYearsLabel(firstYear: Int, lastYear: Int): String =
    if (firstYear <= 0 || lastYear <= 0) "" else "$firstYear–${(lastYear % 100).toString().padStart(2, '0')}"

private fun meanLabel(mean: Double): String = String.format(Locale.US, "%.1f", mean)

/**
 * "High yield · ~4.2 Qs/yr (2021–25)", or "No PYQ data" when there are fewer than two
 * years of counts. Never a zero. Tap to see the sources.
 */
@Composable
internal fun PriorityLabel(
    priority: ChapterPriority,
    firstYear: Int,
    lastYear: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val mean = priority.meanQuestionsPerYear
    val text = if (priority.band == PriorityBand.NO_DATA || mean == null) {
        stringResource(R.string.syllabus_yield_no_data)
    } else {
        stringResource(
            R.string.syllabus_yield_with_rate,
            stringResource(bandLabel(priority.band)),
            meanLabel(mean),
            pyqYearsLabel(firstYear, lastYear),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (priority.band == PriorityBand.HIGH) FontWeight.SemiBold else FontWeight.Normal,
        color = if (priority.band == PriorityBand.HIGH) colors.text else colors.textSecondary,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
    )
}

@Composable
internal fun PriorityDetailDialog(
    detail: PriorityDetail,
    firstYear: Int,
    lastYear: Int,
    onDismiss: () -> Unit,
) {
    val priority = detail.priority
    val mean = priority.meanQuestionsPerYear
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.syllabus_yield_close)) } },
        title = { Text(detail.chapterTitle) },
        text = {
            Column {
                if (mean == null || priority.band == PriorityBand.NO_DATA) {
                    Text(stringResource(R.string.syllabus_yield_no_data_body))
                } else {
                    Text(
                        stringResource(bandLabel(priority.band)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            R.string.syllabus_yield_rate_detail,
                            meanLabel(mean),
                            pyqYearsLabel(firstYear, lastYear),
                            priority.yearsUsed,
                        ),
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    if (priority.sourceNames.isNotEmpty()) {
                        Text(
                            stringResource(R.string.syllabus_yield_sources, priority.sourceNames.joinToString(", ")),
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
                Text(
                    stringResource(R.string.syllabus_yield_method),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textMuted,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        },
    )
}
