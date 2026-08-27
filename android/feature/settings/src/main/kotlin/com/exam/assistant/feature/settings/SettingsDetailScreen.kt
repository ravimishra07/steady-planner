package com.exam.assistant.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.core.design.ThemeChoice

@Composable
fun SettingsDetailRoute(
    planStore: PlanStore,
    settingsStore: SettingsStore,
    focusStore: FocusStore,
    syllabusStore: SyllabusStore,
    studySessionStore: StudySessionStore,
    syllabusRepository: SyllabusRepository,
    themeChoice: ThemeChoice,
    onThemeChoose: (ThemeChoice) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onBack: () -> Unit,
    onCleared: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsDetailViewModel = viewModel(
        factory = SettingsDetailViewModel.Factory(
            planStore,
            settingsStore,
            focusStore,
            syllabusStore,
            studySessionStore,
            syllabusRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsDetailScreen(
        state = state,
        themeChoice = themeChoice,
        onThemeChoose = onThemeChoose,
        accentPalette = accentPalette,
        onAccentPalette = onAccentPalette,
        onBack = onBack,
        onWeekdayChange = viewModel::setWeekdayHours,
        onWeekendChange = viewModel::setWeekendHours,
        onStudyPlaceChange = viewModel::setStudyPlace,
        onFocusDuration = viewModel::setFocusDurationMinutes,
        onRequestClear = viewModel::requestClear,
        onConfirmClear = {
            viewModel.confirmClear(onCleared)
        },
        onDismissClear = viewModel::dismissClear,
        onRequestSeed = viewModel::requestSeed,
        onConfirmSeed = viewModel::confirmSeed,
        onDismissSeed = viewModel::dismissSeed,
        onDismissSeedDone = viewModel::dismissSeedDone,
        onDismissSeedError = viewModel::dismissSeedError,
        modifier = modifier,
    )
}

@Composable
fun SettingsDetailScreen(
    state: SettingsDetailUiState,
    themeChoice: ThemeChoice,
    onThemeChoose: (ThemeChoice) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onBack: () -> Unit,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onStudyPlaceChange: (String) -> Unit,
    onFocusDuration: (Int) -> Unit,
    onRequestClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit,
    onRequestSeed: () -> Unit,
    onConfirmSeed: () -> Unit,
    onDismissSeed: () -> Unit,
    onDismissSeedDone: () -> Unit,
    onDismissSeedError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    if (state.showClearDialog) {
        AlertDialog(
            onDismissRequest = onDismissClear,
            title = { Text(stringResource(R.string.settings_clear_plan)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text(stringResource(R.string.settings_clear_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear) {
                    Text(stringResource(R.string.settings_clear_no))
                }
            },
        )
    }
    if (state.showSeedDialog) {
        AlertDialog(
            onDismissRequest = onDismissSeed,
            title = { Text(stringResource(R.string.settings_seed_title)) },
            text = { Text(stringResource(R.string.settings_seed_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirmSeed) {
                    Text(stringResource(R.string.settings_seed_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSeed) {
                    Text(stringResource(R.string.settings_seed_no))
                }
            },
        )
    }
    if (state.seedDone) {
        AlertDialog(
            onDismissRequest = onDismissSeedDone,
            title = { Text(stringResource(R.string.settings_seed_done_title)) },
            text = { Text(stringResource(R.string.settings_seed_done_body)) },
            confirmButton = {
                TextButton(onClick = onDismissSeedDone) {
                    Text(stringResource(R.string.settings_seed_done_ok))
                }
            },
        )
    }
    if (state.seedError != null) {
        AlertDialog(
            onDismissRequest = onDismissSeedError,
            title = { Text(stringResource(R.string.settings_seed_error_title)) },
            text = { Text(state.seedError) },
            confirmButton = {
                TextButton(onClick = onDismissSeedError) {
                    Text(stringResource(R.string.settings_seed_done_ok))
                }
            },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
            }
            Text(
                text = stringResource(R.string.settings_detail_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }
        SectionTitle(stringResource(R.string.settings_appearance))
        Text(
            text = stringResource(R.string.settings_theme_mode),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ThemeChoice.entries.forEach { option ->
                FilterChip(
                    selected = themeChoice == option,
                    onClick = { onThemeChoose(option) },
                    label = {
                        Text(
                            when (option) {
                                ThemeChoice.System -> stringResource(R.string.settings_theme_system)
                                ThemeChoice.Light -> stringResource(R.string.settings_theme_light)
                                ThemeChoice.Dark -> stringResource(R.string.settings_theme_dark)
                            },
                        )
                    },
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_theme_color),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
        )
        AccentPaletteSelector(
            selected = accentPalette,
            onSelect = onAccentPalette,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        SectionTitle(stringResource(R.string.settings_hours))
        Text(stringResource(R.string.settings_weekdays), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.weekdayHours,
            onValueChange = onWeekdayChange,
            valueRange = 1f..14f,
            steps = 25,
        )
        Text(stringResource(R.string.settings_weekends), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.weekendHours,
            onValueChange = onWeekendChange,
            valueRange = 1f..16f,
            steps = 29,
        )
        OutlinedTextField(
            value = state.studyPlace,
            onValueChange = onStudyPlaceChange,
            label = { Text(stringResource(R.string.settings_study_spot)) },
            placeholder = { Text(stringResource(R.string.settings_study_spot_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle(stringResource(R.string.settings_focus))
        Text(stringResource(R.string.settings_focus_length), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(25, 50, 90).forEach { minutes ->
                FilterChip(
                    selected = state.focusDurationMinutes == minutes,
                    onClick = { onFocusDuration(minutes) },
                    label = { Text(stringResource(R.string.settings_focus_minutes, minutes)) },
                )
            }
        }
        SectionTitle(stringResource(R.string.settings_data))
        Button(
            onClick = onRequestSeed,
            enabled = !state.seeding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.sm),
            shape = RoundedCornerShape(Radius.lg),
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandDeep, contentColor = colors.onBrand),
        ) {
            Text(
                if (state.seeding) {
                    stringResource(R.string.settings_seed_in_progress)
                } else {
                    stringResource(R.string.settings_seed_button)
                },
            )
        }
        Button(
            onClick = onRequestClear,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = colors.onBrand),
        ) {
            Text(stringResource(R.string.settings_clear_plan))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.text,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
    )
}
