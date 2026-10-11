package com.exam.assistant.feature.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.design.ProLockCard
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.core.design.AppCard
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.MistakeType
import com.exam.assistant.domain.MockMistake
import com.exam.assistant.domain.MockSubject
import com.exam.assistant.domain.MockTestType
import com.exam.assistant.domain.formatInsightDate

@Composable
fun MockFormRoute(
    mockId: String?,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    mockTestRepository: MockTestRepository,
    proRepository: ProRepository,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    viewModel: MockFormViewModel = viewModel(
        key = "mock-form-${mockId ?: "new"}",
        factory = MockFormViewModel.Factory(mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    MockFormScreen(
        state = state,
        actions = MockFormActions(
            onBack = onDone,
            onName = viewModel::setName,
            onType = viewModel::setType,
            onDate = viewModel::setDate,
            onSubjectScore = viewModel::setSubjectScore,
            onTotal = viewModel::setTotal,
            onMaxTotal = viewModel::setMaxTotal,
            onAttempted = viewModel::setAttempted,
            onCorrect = viewModel::setCorrect,
            onWrong = viewModel::setWrong,
            onTagChapter = viewModel::tagChapter,
            onMistakeType = viewModel::setMistakeType,
            onRemoveMistake = viewModel::removeMistake,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onOpenPaywall = onOpenPaywall,
        ),
        modifier = modifier,
    )
}

/** Every callback the form needs, kept in one value so the screen signature stays readable. */
data class MockFormActions(
    val onBack: () -> Unit,
    val onName: (String) -> Unit,
    val onType: (MockTestType) -> Unit,
    val onDate: (java.time.LocalDate) -> Unit,
    val onSubjectScore: (MockSubject, String) -> Unit,
    val onTotal: (String) -> Unit,
    val onMaxTotal: (String) -> Unit,
    val onAttempted: (String) -> Unit,
    val onCorrect: (String) -> Unit,
    val onWrong: (String) -> Unit,
    val onTagChapter: (String) -> Unit,
    val onMistakeType: (String, MistakeType) -> Unit,
    val onRemoveMistake: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onOpenPaywall: () -> Unit = {},
)

@Composable
fun MockFormScreen(state: MockFormUiState, actions: MockFormActions, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.day_log_back), tint = colors.textSecondary)
            }
            Text(
                stringResource(if (state.isEdit) R.string.mock_form_edit_title else R.string.mock_form_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (state.isEdit && !state.unavailable) {
                IconButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.mock_form_delete), tint = colors.danger)
                }
            }
        }
        when {
            state.loading -> FormMessage(stringResource(R.string.insights_loading))
            state.unavailable -> FormMessage(stringResource(R.string.mock_form_unavailable))
            else -> MockFormFields(state, actions)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mock_form_delete_title)) },
            text = { Text(stringResource(R.string.mock_form_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.onDelete() }) {
                    Text(stringResource(R.string.mock_form_delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.log_session_cancel)) } },
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun MockFormFields(state: MockFormUiState, actions: MockFormActions) {
    val colors = AppTheme.colors
    var pickDate by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(state.attemptedText.isNotEmpty() || state.correctText.isNotEmpty() || state.wrongText.isNotEmpty()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            MockTestType.entries.forEach { type ->
                FilterChip(
                    selected = type == state.type,
                    onClick = { actions.onType(type) },
                    label = { Text(stringResource(mockTypeLabel(type))) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.name,
                onValueChange = actions.onName,
                singleLine = true,
                label = { Text(stringResource(R.string.mock_form_name)) },
                placeholder = { Text(stringResource(mockTypeLabel(state.type))) },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.heightIn(min = Size.touchTarget)) {
                Text(formatInsightDate(state.date))
            }
        }
        FormLabel(stringResource(R.string.mock_form_scores))
        Text(stringResource(R.string.mock_form_scores_hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        MockSubject.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { subject ->
                    NumberField(
                        value = state.subjectScores[subject].orEmpty(),
                        onValue = { actions.onSubjectScore(subject, it) },
                        label = stringResource(subjectLabel(subject)),
                        signed = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            NumberField(
                value = if (state.totalIsAuto) state.total?.toString().orEmpty() else state.totalText,
                onValue = actions.onTotal,
                label = stringResource(if (state.totalIsAuto) R.string.mock_form_total_auto else R.string.mock_form_total),
                signed = true,
                enabled = !state.totalIsAuto,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = state.maxTotalText,
                onValue = actions.onMaxTotal,
                label = stringResource(R.string.mock_form_out_of),
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(stringResource(if (showDetails) R.string.mock_form_hide_details else R.string.mock_form_show_details))
        }
        if (showDetails) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(state.attemptedText, actions.onAttempted, stringResource(R.string.mock_form_attempted), modifier = Modifier.weight(1f))
                NumberField(state.correctText, actions.onCorrect, stringResource(R.string.mock_form_correct), modifier = Modifier.weight(1f))
                NumberField(state.wrongText, actions.onWrong, stringResource(R.string.mock_form_wrong), modifier = Modifier.weight(1f))
            }
        }
        if (state.proUnlocked) {
            MistakeLog(state, actions)
        } else {
            // Mistakes tagged while Pro stay in state, so saving never drops them.
            ProLockCard(
                title = stringResource(R.string.pro_lock_mistakes_title),
                body = stringResource(R.string.pro_lock_mistakes_body),
                actionLabel = stringResource(R.string.pro_lock_action),
                onUnlock = actions.onOpenPaywall,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.log_session_save_failed), style = MaterialTheme.typography.bodySmall, color = colors.danger)
        }
        Button(
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().height(Size.ctaHeight).padding(top = Spacing.xs),
        ) {
            Text(stringResource(if (state.isEdit) R.string.log_session_save_changes else R.string.mock_form_save))
        }
        Spacer(Modifier.height(Spacing.xl))
    }
    if (pickDate) SessionDatePicker(state.date, onDate = { actions.onDate(it); pickDate = false }, onDismiss = { pickDate = false })
}

// Pro: shown only when unlocked (MockFormFields).
@Composable
internal fun MistakeLog(state: MockFormUiState, actions: MockFormActions) {
    val colors = AppTheme.colors
    var picking by remember { mutableStateOf(false) }
    FormLabel(stringResource(R.string.mock_form_mistakes))
    Text(stringResource(R.string.mock_form_mistakes_hint), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    if (state.mistakes.isNotEmpty()) {
        AppCard {
            state.mistakes.forEachIndexed { index, mistake ->
                MistakeRow(state.chapterTitle(mistake.chapterId), mistake, actions)
                if (index < state.mistakes.lastIndex) HorizontalDivider(color = colors.borderSubtle)
            }
        }
    }
    OutlinedButton(
        onClick = { picking = true },
        enabled = state.subjects.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = Size.touchTarget),
    ) { Text(stringResource(R.string.mock_form_tag_chapter)) }
    if (picking) {
        TagChapterDialog(
            subjects = state.subjects,
            tagged = state.mistakes.mapTo(mutableSetOf()) { it.chapterId },
            onPick = { actions.onTagChapter(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun MistakeRow(title: String, mistake: MockMistake, actions: MockFormActions) {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f), maxLines = 2)
            IconButton(onClick = { actions.onRemoveMistake(mistake.chapterId) }) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.mock_form_untag), tint = colors.textMuted)
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MistakeType.entries.forEach { type ->
                FilterChip(
                    selected = type == mistake.type,
                    onClick = { actions.onMistakeType(mistake.chapterId, type) },
                    label = { Text(stringResource(mistakeTypeLabel(type))) },
                )
            }
        }
    }
}

@Composable
private fun TagChapterDialog(
    subjects: List<SubjectChoice>,
    tagged: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    var subjectId by remember { mutableStateOf(subjects.firstOrNull()?.id) }
    val chapters = subjects.firstOrNull { it.id == subjectId }?.chapters.orEmpty().filterNot { it.id in tagged }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mock_form_tag_chapter)) },
        text = {
            Column {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    subjects.forEach { subject ->
                        FilterChip(selected = subject.id == subjectId, onClick = { subjectId = subject.id }, label = { Text(subject.name) })
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = Size.dialogListMaxHeight)) {
                    items(chapters, key = { it.id }) { chapter ->
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(chapter.id) }.padding(vertical = Spacing.md),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_session_cancel)) } },
        containerColor = colors.surface,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        keyboardOptions = KeyboardOptions(keyboardType = if (signed) KeyboardType.Text else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

@Composable
private fun FormMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textMuted)
    }
}

private fun mistakeTypeLabel(type: MistakeType): Int = when (type) {
    MistakeType.CONCEPT -> R.string.mistake_concept
    MistakeType.SILLY -> R.string.mistake_silly
    MistakeType.TIME -> R.string.mistake_time
    MistakeType.GUESS -> R.string.mistake_guess
}
