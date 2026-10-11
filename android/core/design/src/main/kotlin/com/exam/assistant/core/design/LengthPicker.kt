package com.exam.assistant.core.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Display strings for [LengthPicker]; the caller's module owns them. */
data class LengthPickerLabels(
    val minutes: (Int) -> String,
    val custom: String,
    val dialogTitle: String,
    val fieldLabel: String,
    val ok: String,
    val cancel: String,
)

/**
 * Preset session lengths plus a custom one (PRODUCTION_V1_PLAN §6.4). Focus and Settings both use
 * this so they offer the same choices. [parseCustom] turns typed text into a valid length or null.
 */
@Composable
fun LengthPicker(
    presets: List<Int>,
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    parseCustom: (String) -> Int?,
    labels: LengthPickerLabels,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    val isCustom = selectedMinutes !in presets
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        presets.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                enabled = enabled,
                label = { Text(labels.minutes(minutes)) },
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { editing = true },
            enabled = enabled,
            label = { Text(if (isCustom) labels.minutes(selectedMinutes) else labels.custom) },
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(selectedMinutes.toString()) }
        val parsed = parseCustom(text)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(labels.dialogTitle) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    label = { Text(labels.fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { parsed?.let(onSelect); editing = false },
                    enabled = parsed != null,
                ) { Text(labels.ok) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(labels.cancel) } },
            containerColor = AppTheme.colors.surface,
        )
    }
}
