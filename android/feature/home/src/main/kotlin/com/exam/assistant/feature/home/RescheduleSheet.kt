package com.exam.assistant.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.DayBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RescheduleSheet(
    block: DayBlock,
    onMoveToNextSlot: () -> Unit,
    onMoveToTomorrow: () -> Unit,
    onChooseTime: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surfaceCard,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.home_reschedule),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                Text(
                    text = "${block.subjectLabel} · ${block.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                Button(
                    onClick = onMoveToNextSlot,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brandDeep,
                        contentColor = colors.onBrand,
                    ),
                ) {
                    Text(stringResource(R.string.home_reschedule_next_slot))
                }
                OutlinedButton(
                    onClick = onMoveToTomorrow,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                ) {
                    Text(stringResource(R.string.home_reschedule_tomorrow))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                ) {
                    Text(stringResource(R.string.home_reschedule_choose_time))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = block.startMinuteOfDay / 60,
            initialMinute = block.startMinuteOfDay % 60,
            is24Hour = false,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(Radius.lg), color = colors.surfaceCard) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.home_cancel))
                        }
                        TextButton(onClick = {
                            showTimePicker = false
                            onChooseTime(timePickerState.hour * 60 + timePickerState.minute)
                        }) {
                            Text(stringResource(R.string.home_ok))
                        }
                    }
                }
            }
        }
    }
}
