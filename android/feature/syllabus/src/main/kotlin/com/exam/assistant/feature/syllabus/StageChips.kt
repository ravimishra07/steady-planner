package com.exam.assistant.feature.syllabus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.ProLockIcon
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.Stroke
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.StageLabels

@Composable
internal fun defaultStageLabel(stage: ChapterStage): String = stringResource(
    when (stage) {
        ChapterStage.LEARNED -> R.string.syllabus_stage_learned
        ChapterStage.NCERT -> R.string.syllabus_stage_ncert
        ChapterStage.PRACTICE -> R.string.syllabus_stage_practice
        ChapterStage.PYQ -> R.string.syllabus_stage_pyq
        ChapterStage.REVISED -> R.string.syllabus_stage_revised
        ChapterStage.CUSTOM -> R.string.syllabus_stage_custom
    },
)

/** One-tap stage chips on a chapter row. No dialog: a tap is the tick (§5.1 acceptance). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StageChipRow(
    chapterId: String,
    chips: List<StageChipUi>,
    onTapStage: (String, ChapterStage) -> Unit,
    onUndoRevision: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        chips.forEach { chip ->
            val name = chip.customLabel ?: defaultStageLabel(chip.stage)
            val label = if (chip.stage == ChapterStage.REVISED && chip.count > 0) {
                stringResource(R.string.syllabus_stage_revised_count, name, chip.count)
            } else {
                name
            }
            StageChip(
                label = label,
                selected = chip.count > 0,
                onClick = { onTapStage(chapterId, chip.stage) },
                onLongClick = if (chip.stage == ChapterStage.REVISED) {
                    { onUndoRevision(chapterId) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.small),
        color = if (selected) colors.selectionContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(Stroke.hairline, colors.outlineVariant),
        modifier = Modifier
            .height(Size.compactControl)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onSelectionContainer,
                    modifier = Modifier.size(Size.smallIcon),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) colors.onSelectionContainer else colors.textSecondary,
            )
        }
    }
}

/** Rename or hide each stage, and name the one custom stage. */
@Composable
internal fun StageLabelsDialog(
    labels: StageLabels,
    onSave: (StageLabels) -> Unit,
    onDismiss: () -> Unit,
    /** Free tier with no custom stage yet: the custom row becomes a Pro lock. */
    customLocked: Boolean = false,
    onUnlockCustom: () -> Unit = {},
) {
    val names = remember {
        mutableStateMapOf<ChapterStage, String>().apply {
            ChapterStage.entries.forEach { stage -> put(stage, labels.labelFor(stage).orEmpty()) }
        }
    }
    val shown = remember {
        mutableStateMapOf<ChapterStage, Boolean>().apply {
            ChapterStage.entries.forEach { stage -> put(stage, stage !in labels.hidden) }
        }
    }
    var dirty by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.syllabus_stage_labels_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.syllabus_stage_labels_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                ChapterStage.entries.forEach { stage ->
                    if (stage == ChapterStage.CUSTOM && customLocked) {
                        TextButton(onClick = onUnlockCustom, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                            ProLockIcon(Modifier.padding(end = Spacing.sm))
                            Text(stringResource(R.string.syllabus_stage_custom_pro), modifier = Modifier.weight(1f))
                        }
                        return@forEach
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = shown[stage] == true,
                            onCheckedChange = { shown[stage] = it; dirty = true },
                        )
                        OutlinedTextField(
                            value = names[stage].orEmpty(),
                            onValueChange = { names[stage] = it.take(MAX_LABEL_LENGTH); dirty = true },
                            singleLine = true,
                            placeholder = { Text(defaultStageLabel(stage)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dirty,
                onClick = {
                    onSave(
                        StageLabels(
                            renamed = names.filterKeys { it != ChapterStage.CUSTOM }
                                .filterValues { it.isNotBlank() },
                            hidden = shown.filterValues { !it }.keys,
                            customLabel = names[ChapterStage.CUSTOM]?.takeIf { it.isNotBlank() && !customLocked },
                        ),
                    )
                    onDismiss()
                },
            ) { Text(stringResource(R.string.syllabus_stage_labels_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.syllabus_stage_labels_cancel)) }
        },
    )
}

private const val MAX_LABEL_LENGTH = 24
