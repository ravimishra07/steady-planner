package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.ChapterCoaching
import com.exam.assistant.domain.MAX_COACHING_COUNT
import kotlin.math.roundToInt

/**
 * A chapter's coaching lectures and DPPs (§5.4): two counts with +/−, and "done up to N" for
 * each as one slider, so a whole chapter is entered in a few taps.
 */
@Composable
internal fun CoachingDialog(
    chapterId: String,
    chapterName: String,
    initial: ChapterCoaching?,
    onSave: (ChapterCoaching) -> Unit,
    onDismiss: () -> Unit,
) {
    var lectures by remember { mutableIntStateOf(initial?.lectures ?: 0) }
    var dpps by remember { mutableIntStateOf(initial?.dpps ?: 0) }
    var lecturesDone by remember { mutableIntStateOf(initial?.lecturesDone ?: 0) }
    var dppsDone by remember { mutableIntStateOf(initial?.dppsDone ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.syllabus_chapter_coaching)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(chapterName, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textSecondary)
                AppValueStepper(
                    label = stringResource(R.string.coaching_lectures),
                    value = lectures.toString(),
                    decreaseContentDescription = stringResource(R.string.coaching_fewer_lectures),
                    increaseContentDescription = stringResource(R.string.coaching_more_lectures),
                    onDecrease = {
                        lectures = (lectures - 1).coerceAtLeast(0)
                        lecturesDone = lecturesDone.coerceAtMost(lectures)
                    },
                    onIncrease = { lectures = (lectures + 1).coerceAtMost(MAX_COACHING_COUNT) },
                    decreaseEnabled = lectures > 0,
                    increaseEnabled = lectures < MAX_COACHING_COUNT,
                )
                DoneUpTo(
                    label = stringResource(R.string.coaching_lectures_done_up_to, lecturesDone, lectures),
                    done = lecturesDone,
                    total = lectures,
                    onChange = { lecturesDone = it },
                )
                AppValueStepper(
                    label = stringResource(R.string.coaching_dpps),
                    value = dpps.toString(),
                    decreaseContentDescription = stringResource(R.string.coaching_fewer_dpps),
                    increaseContentDescription = stringResource(R.string.coaching_more_dpps),
                    onDecrease = {
                        dpps = (dpps - 1).coerceAtLeast(0)
                        dppsDone = dppsDone.coerceAtMost(dpps)
                    },
                    onIncrease = { dpps = (dpps + 1).coerceAtMost(MAX_COACHING_COUNT) },
                    decreaseEnabled = dpps > 0,
                    increaseEnabled = dpps < MAX_COACHING_COUNT,
                )
                DoneUpTo(
                    label = stringResource(R.string.coaching_dpps_done_up_to, dppsDone, dpps),
                    done = dppsDone,
                    total = dpps,
                    onChange = { dppsDone = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ChapterCoaching(chapterId, lectures, dpps, lecturesDone, dppsDone))
                    onDismiss()
                },
            ) { Text(stringResource(R.string.syllabus_stage_labels_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.syllabus_stage_labels_cancel)) }
        },
    )
}

@Composable
private fun DoneUpTo(label: String, done: Int, total: Int, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.text)
    if (total > 0) {
        Slider(
            value = done.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, total)) },
            valueRange = 0f..total.toFloat(),
            steps = (total - 1).coerceAtLeast(0),
        )
    }
}
