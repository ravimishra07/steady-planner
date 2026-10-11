package com.exam.assistant.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.AppValueStepper
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.MAX_COACHING_COUNT
import com.exam.assistant.domain.formatInsightDate

/**
 * "Taught in class today" (§4.5, §5.4): one tap moves the batch on a lecture in the same chapter;
 * "Different chapter" picks a chapter and lecture number.
 */
@Composable
internal fun TaughtTodayCard(
    coaching: CoachingTodayUi,
    onNextLecture: () -> Unit,
    onLogClass: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var pickerOpen by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(Radius.lg), color = colors.elevated, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.home_coaching_taught_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            val chapterTitle = coaching.batchChapterTitle
            Text(
                text = if (chapterTitle != null) {
                    stringResource(R.string.home_coaching_batch_at, chapterTitle, coaching.batchLecture)
                } else {
                    stringResource(R.string.home_coaching_batch_unset)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                if (chapterTitle != null && coaching.batchLecture < MAX_COACHING_COUNT) {
                    Button(
                        onClick = onNextLecture,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
                        shape = RoundedCornerShape(Radius.pill),
                    ) { Text(stringResource(R.string.home_coaching_next_lecture, coaching.batchLecture + 1)) }
                }
                TextButton(onClick = { pickerOpen = true }) {
                    Text(
                        stringResource(
                            if (chapterTitle != null) R.string.home_coaching_other_chapter else R.string.home_coaching_pick_chapter,
                        ),
                    )
                }
            }
        }
    }
    if (pickerOpen) {
        TaughtTodayDialog(
            coaching = coaching,
            onSave = onLogClass,
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun TaughtTodayDialog(
    coaching: CoachingTodayUi,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val subjects = remember(coaching.chapters) {
        coaching.chapters.map { it.subjectId to it.subjectName }.distinct()
    }
    val initialSubject = coaching.chapters.firstOrNull { it.chapterId == coaching.batchChapterId }?.subjectId
        ?: subjects.firstOrNull()?.first
    var subjectId by remember { mutableStateOf(initialSubject) }
    var chapterId by remember { mutableStateOf(coaching.batchChapterId) }
    var lecture by remember { mutableIntStateOf(coaching.batchLecture.coerceAtLeast(1)) }
    val shown = coaching.chapters.filter { it.subjectId == subjectId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_coaching_taught_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(subjects, key = { it.first }) { subject ->
                        FilterChip(
                            selected = subject.first == subjectId,
                            onClick = { subjectId = subject.first },
                            label = { Text(subject.second) },
                        )
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = Size.dialogListMaxHeight)) {
                    items(shown, key = { it.chapterId }) { chapter ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = chapter.chapterId == chapterId,
                                onClick = {
                                    if (chapter.chapterId != chapterId) lecture = 1
                                    chapterId = chapter.chapterId
                                },
                            )
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.text,
                            )
                        }
                    }
                }
                AppValueStepper(
                    label = stringResource(R.string.home_coaching_lecture_number),
                    value = lecture.toString(),
                    decreaseContentDescription = stringResource(R.string.home_coaching_lecture_earlier),
                    increaseContentDescription = stringResource(R.string.home_coaching_lecture_later),
                    onDecrease = { lecture = (lecture - 1).coerceAtLeast(1) },
                    onIncrease = { lecture = (lecture + 1).coerceAtMost(MAX_COACHING_COUNT) },
                    decreaseEnabled = lecture > 1,
                    increaseEnabled = lecture < MAX_COACHING_COUNT,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = chapterId != null,
                onClick = {
                    chapterId?.let { onSave(it, lecture) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.home_coaching_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_coaching_cancel)) } },
    )
}

/** Lectures and DPPs behind the batch, by subject, and the clear date at the current split. */
@Composable
internal fun CoachingBacklogCard(
    coaching: CoachingTodayUi,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Surface(shape = RoundedCornerShape(Radius.lg), color = colors.elevated, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.home_coaching_backlog_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            if (coaching.backlog.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_coaching_backlog_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            } else {
                coaching.backlog.forEach { row ->
                    Text(
                        text = stringResource(R.string.home_coaching_backlog_row, row.subjectName, row.lectures, row.dpps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                }
                val clear = coaching.clearDate
                Text(
                    text = if (clear != null) {
                        stringResource(
                            R.string.home_coaching_clear_by,
                            formatInsightDate(clear),
                            coaching.liveSplit,
                            100 - coaching.liveSplit,
                        )
                    } else {
                        stringResource(R.string.home_coaching_no_clear_date)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
