package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.MAX_CHAPTER_TITLE_LENGTH
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

/** What the chapter menu on a Syllabus row can do. */
enum class ChapterAction { RENAME, RESET_NAME, PARK, COACHING }

/** Overflow menu on a chapter row: rename, reset name, park/unpark, coaching counts. */
@Composable
internal fun ChapterMenuButton(
    renamed: Boolean,
    parked: Boolean,
    onAction: (ChapterAction) -> Unit,
    showCoaching: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Size.touchTarget)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.syllabus_chapter_menu),
                tint = AppTheme.colors.textSecondary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.syllabus_chapter_rename)) },
                onClick = { open = false; onAction(ChapterAction.RENAME) },
            )
            if (renamed) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.syllabus_chapter_reset_name)) },
                    onClick = { open = false; onAction(ChapterAction.RESET_NAME) },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (parked) R.string.syllabus_chapter_unpark else R.string.syllabus_chapter_park))
                },
                onClick = { open = false; onAction(ChapterAction.PARK) },
            )
            if (showCoaching) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.syllabus_chapter_coaching)) },
                    onClick = { open = false; onAction(ChapterAction.COACHING) },
                )
            }
        }
    }
}

/** Rename a chapter. Saving an empty name, or the original one, restores the original. */
@Composable
internal fun RenameChapterDialog(
    currentName: String,
    originalName: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.syllabus_chapter_rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_CHAPTER_TITLE_LENGTH) },
                    singleLine = true,
                    placeholder = { Text(originalName) },
                )
                Text(
                    text = stringResource(R.string.syllabus_chapter_original_name, originalName),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                if (currentName != originalName) {
                    TextButton(onClick = { onSave(null); onDismiss() }) {
                        Text(stringResource(R.string.syllabus_chapter_reset_name))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name); onDismiss() }) {
                Text(stringResource(R.string.syllabus_stage_labels_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.syllabus_stage_labels_cancel)) }
        },
    )
}
