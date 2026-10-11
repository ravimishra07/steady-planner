package com.exam.assistant.feature.home

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.exam.assistant.core.design.AppTheme
import com.exam.assistant.core.design.Radius
import com.exam.assistant.core.design.Size
import com.exam.assistant.core.design.Spacing
import com.exam.assistant.domain.DayBlock
import com.exam.assistant.domain.RevisionSuggestion
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTopicNode
import com.exam.assistant.domain.StudyPlacementIssue
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.REVISION_INTERVAL_DAYS
import com.exam.assistant.domain.currentMinuteOfDay
import com.exam.assistant.domain.formatMinuteOfDay
import com.exam.assistant.domain.topicHours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeStudyContent(
    state: HomeUiState,
    onEditPlan: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onToggleCalendarExpanded: () -> Unit,
    onOpenAdd: () -> Unit,
    onDismissSheet: () -> Unit,
    onBackInStudyPicker: () -> Unit,
    onSelectPickerSection: (Int) -> Unit,
    onOpenPickerSubtopics: (List<Int>) -> Unit,
    onSetPickerQuery: (String) -> Unit,
    onPickTopic: (nodeKey: String, title: String, sectionName: String, subjectId: String, topicPath: String) -> Unit,
    onPickRevision: (RevisionSuggestion) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetActivityType: (StudyActivityType) -> Unit,
    onSetScheduledMinute: (Int) -> Unit,
    onConfirmStart: () -> Unit,
    onConfirmAddToPlan: () -> Unit,
    onStartScheduledSession: (String) -> Unit,
    onStartMissedSession: (String) -> Unit,
    onReplanRestOfToday: () -> Unit,
    onOpenAddInGap: (Int, Int) -> Unit,
    onRescheduleToNextSlot: (String) -> Unit,
    onRescheduleToTomorrow: (String) -> Unit,
    onRescheduleToTime: (String, Int) -> Unit,
    onSkipSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var rescheduleTarget by remember { mutableStateOf<DayBlock?>(null) }
    Box(modifier = modifier.fillMaxSize()) {
        if (state.sheet == HomeSheet.None) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeCalendarChrome(
                    selectedDate = state.selectedDate,
                    weekDays = state.weekDays,
                    monthDays = state.monthDays,
                    expanded = state.calendarExpanded,
                    onToggleExpanded = onToggleCalendarExpanded,
                    onSelectDate = onSelectDate,
                )

                TodaySummary(
                    entries = state.dayTimeline,
                    plannedMinutes = state.plannedTodayMinutes,
                    completedMinutes = state.completedTodayMinutes,
                    selectedIsToday = state.selectedIsToday,
                    onEditPlan = onEditPlan,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.md, bottom = Spacing.xl),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen),
                ) {
                    state.placementIssue?.let { issue ->
                        PlacementIssueMessage(
                            issue = issue,
                            modifier = Modifier.padding(bottom = Spacing.md),
                        )
                    }
                    state.missedDayRecovery?.let { recovery ->
                        MissedDayRecoveryPanel(
                            recovery = recovery,
                            onStartNextNow = { onStartMissedSession(recovery.nextSessionId) },
                            onReplan = onReplanRestOfToday,
                            modifier = Modifier.padding(bottom = Spacing.lg),
                        )
                    }
                    DayTimelineSection(
                        entries = state.dayTimeline,
                        activeSprint = state.activeSprint,
                        revisionItems = state.revisionItems,
                        onStartScheduled = onStartScheduledSession,
                        onStartMissed = onStartMissedSession,
                        onStartAutoRevision = onPickRevision,
                        onRequestReschedule = { block -> rescheduleTarget = block },
                        onOpenAdd = onOpenAdd,
                        onOpenAddInGap = onOpenAddInGap,
                    )
                    Spacer(Modifier.height(Size.themePreview))
                }
            }
            rescheduleTarget?.let { block ->
                RescheduleSheet(
                    block = block,
                    onMoveToNextSlot = {
                        onRescheduleToNextSlot(block.id)
                        rescheduleTarget = null
                    },
                    onMoveToTomorrow = {
                        onRescheduleToTomorrow(block.id)
                        rescheduleTarget = null
                    },
                    onChooseTime = { minute ->
                        onRescheduleToTime(block.id, minute)
                        rescheduleTarget = null
                    },
                    onDismiss = { rescheduleTarget = null },
                    onSkip = {
                        onSkipSession(block.id)
                        rescheduleTarget = null
                    },
                )
            }
            if (state.selectedIsToday) {
                FloatingActionButton(
                    onClick = onOpenAdd,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Spacing.screen, bottom = Spacing.md),
                    containerColor = colors.brandDeep,
                    contentColor = colors.onBrand,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add_study))
                }
            }
        } else {
            StudyPickerFrame(
                state = state,
                onBack = onBackInStudyPicker,
                onClose = onDismissSheet,
                onSetQuery = onSetPickerQuery,
                onSelectSection = onSelectPickerSection,
                onOpenSubtopics = onOpenPickerSubtopics,
                onPickTopic = onPickTopic,
                onSetDuration = onSetDuration,
                onSetActivityType = onSetActivityType,
                onSetScheduledMinute = onSetScheduledMinute,
                onConfirmStart = onConfirmStart,
                onConfirmAddToPlan = onConfirmAddToPlan,
            )
        }
    }

    BackHandler(enabled = state.sheet != HomeSheet.None, onBack = onBackInStudyPicker)
}

private data class StudyPickerRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val sectionIndex: Int,
    val path: List<Int>,
    val topicPath: String,
    val opensSubtopics: Boolean,
    val nodeId: String,
    val subjectId: String,
)

@Composable
private fun StudyPickerFrame(
    state: HomeUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSetQuery: (String) -> Unit,
    onSelectSection: (Int) -> Unit,
    onOpenSubtopics: (List<Int>) -> Unit,
    onPickTopic: (String, String, String, String, String) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetActivityType: (StudyActivityType) -> Unit,
    onSetScheduledMinute: (Int) -> Unit,
    onConfirmStart: () -> Unit,
    onConfirmAddToPlan: () -> Unit,
) {
    val colors = AppTheme.colors
    val resources = LocalContext.current.resources
    val picking = state.sheet == HomeSheet.PickTopic
    val query = state.pickerQuery.trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        StudyPickerTopBar(
            title = stringResource(
                if (picking) R.string.home_start_study else R.string.home_how_long,
            ),
            showBack = picking && (query.isNotEmpty() || state.pickerLevel != StudyPickerLevel.Subjects) || !picking,
            onBack = onBack,
            onClose = onClose,
        )

        if (picking) {
            OutlinedTextField(
                value = state.pickerQuery,
                onValueChange = onSetQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                singleLine = true,
                shape = RoundedCornerShape(Radius.lg),
                placeholder = { Text(stringResource(R.string.home_picker_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textMuted,
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.brandDeep,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                    focusedPlaceholderColor = colors.textDisabled,
                    unfocusedPlaceholderColor = colors.textDisabled,
                ),
            )
        }

        if (picking) {
            val rows = when {
                query.isNotEmpty() -> searchRows(resources, state.sections, query)
                state.pickerLevel == StudyPickerLevel.Subjects -> subjectRows(resources, state.sections)
                state.pickerLevel == StudyPickerLevel.Topics -> {
                    val sectionIndex = state.pickerSectionIndex
                    if (sectionIndex == null) emptyList() else topicRows(resources, state.sections, sectionIndex)
                }
                else -> {
                    val sectionIndex = state.pickerSectionIndex
                    val topicIndex = state.pickerTopicPath.firstOrNull()
                    if (sectionIndex == null || topicIndex == null) {
                        emptyList()
                    } else {
                        val root = state.sections.getOrNull(sectionIndex)?.topics?.getOrNull(topicIndex)
                        if (root == null) emptyList() else leafRows(
                            resources = resources,
                            node = root,
                            sectionIndex = sectionIndex,
                            rootPath = state.pickerTopicPath,
                            ancestors = listOf(root.name),
                            sectionName = state.sections[sectionIndex].name,
                            subjectId = state.sections[sectionIndex].id.orEmpty(),
                        )
                    }
                }
            }

            val label = when {
                query.isNotEmpty() -> stringResource(R.string.home_picker_suggestions)
                state.pickerLevel == StudyPickerLevel.Subjects -> stringResource(R.string.home_picker_browse_subject)
                state.pickerLevel == StudyPickerLevel.Topics -> state.sections
                    .getOrNull(state.pickerSectionIndex ?: -1)?.name.orEmpty()
                else -> state.sections
                    .getOrNull(state.pickerSectionIndex ?: -1)
                    ?.topics
                    ?.getOrNull(state.pickerTopicPath.firstOrNull() ?: -1)
                    ?.name
                    .orEmpty()
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                if (rows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_picker_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = Spacing.xxl),
                        )
                    }
                } else {
                    items(rows, key = { it.id }) { row ->
                        val selectRow = {
                            onPickTopic(
                                row.nodeId,
                                row.title,
                                state.sections[row.sectionIndex].name,
                                row.subjectId,
                                row.topicPath,
                            )
                        }
                        StudyPickerRowView(
                            row = row,
                            onSelect = if (row.path.isEmpty()) null else selectRow,
                            onBrowse = if (row.opensSubtopics) {
                                {
                                    if (row.path.isEmpty()) onSelectSection(row.sectionIndex)
                                    else onOpenSubtopics(row.path)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.md,
                    bottom = Spacing.md,
                ),
            ) {
                item {
                    DurationPickerContent(
                        topicTitle = state.pendingTopic?.title.orEmpty(),
                        topicPath = state.pendingTopic?.topicPath
                            ?.takeIf { it.isNotBlank() }
                            ?: state.pendingTopic?.sectionName.orEmpty(),
                        selectedMinutes = state.selectedDurationMinutes,
                        onSelectMinutes = onSetDuration,
                        activityType = state.pendingTopic?.activityType ?: StudyActivityType.LEARN,
                        onSelectActivityType = onSetActivityType,
                        customEndMinuteOfDay = state.customEndMinuteOfDay,
                        onSelectEndMinute = onSetScheduledMinute,
                        placementIssue = state.placementIssue,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.selectedIsToday) {
                    Button(
                        onClick = onConfirmStart,
                        modifier = Modifier.fillMaxWidth().height(Size.ctaHeight),
                        shape = RoundedCornerShape(Radius.lg),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.brandDeep,
                            contentColor = colors.onBrand,
                        ),
                    ) {
                        Text(stringResource(R.string.home_start_now))
                    }
                }
                OutlinedButton(
                    onClick = onConfirmAddToPlan,
                    modifier = Modifier.fillMaxWidth().height(Size.ctaHeight),
                    shape = RoundedCornerShape(Radius.lg),
                ) {
                    Text(stringResource(R.string.home_add_to_plan))
                }
            }
        }
    }
}

@Composable
private fun StudyPickerTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(Size.touchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_picker_back), tint = colors.text)
                }
            } else {
                Spacer(modifier = Modifier.size(Size.touchTarget))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(Size.touchTarget)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_picker_close), tint = colors.text)
            }
        }
        HorizontalDivider(color = colors.hairlineSoft)
    }
}

@Composable
private fun StudyPickerRowView(
    row: StudyPickerRow,
    onSelect: (() -> Unit)?,
    onBrowse: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBrowse ?: onSelect ?: {})
                    .padding(vertical = Spacing.sm),
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text,
                )
                if (row.subtitle.isNotBlank()) {
                    Text(
                        text = row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onSelect != null && onBrowse != null) {
                TextButton(onClick = onSelect) {
                    Text(stringResource(R.string.home_picker_choose))
                }
            }
            if (onBrowse != null) {
                IconButton(onClick = onBrowse, modifier = Modifier.size(Size.touchTarget)) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.home_picker_browse_deeper, row.title),
                        tint = colors.textMuted,
                    )
                }
            }
        }
    }
}

private fun subjectRows(resources: Resources, sections: List<SyllabusSection>): List<StudyPickerRow> =
    sections.mapIndexed { index, section ->
        StudyPickerRow(
            id = "subject-$index",
            title = section.name,
            subtitle = resources.getQuantityString(
                R.plurals.home_picker_subject_summary,
                section.topics.size,
                section.topics.size,
                section.topics.sumOf { topicHours(it).toInt() },
            ),
            sectionIndex = index,
            path = emptyList(),
            topicPath = section.name,
            opensSubtopics = true,
            nodeId = section.topics.firstOrNull()?.id.orEmpty(),
            subjectId = section.id.orEmpty(),
        )
    }

private fun topicRows(resources: Resources, sections: List<SyllabusSection>, sectionIndex: Int): List<StudyPickerRow> {
    val section = sections.getOrNull(sectionIndex) ?: return emptyList()
    return section.topics.mapIndexed { index, topic ->
        StudyPickerRow(
            id = "topic-$sectionIndex-$index",
            title = topic.name,
            subtitle = if (topic.children.isEmpty()) {
                formatTopicHours(resources, topic)
            } else {
                resources.getQuantityString(
                    R.plurals.home_picker_subtopic_summary,
                    countLeaves(topic),
                    countLeaves(topic),
                    formatTopicHours(resources, topic),
                )
            },
            sectionIndex = sectionIndex,
            path = listOf(index),
            topicPath = topic.name,
            opensSubtopics = topic.children.isNotEmpty(),
            nodeId = topic.id.orEmpty(),
            subjectId = section.id.orEmpty(),
        )
    }
}

private fun searchRows(resources: Resources, sections: List<SyllabusSection>, query: String): List<StudyPickerRow> =
    sections.flatMapIndexed { sectionIndex, section ->
        section.topics.flatMapIndexed { topicIndex, topic ->
            leafRows(
                resources = resources,
                node = topic,
                sectionIndex = sectionIndex,
                rootPath = listOf(topicIndex),
                ancestors = listOf(topic.name),
                sectionName = section.name,
                subjectId = section.id.orEmpty(),
            )
        }
    }.filter { row ->
        listOf(row.title, row.topicPath, row.subtitle).any { it.contains(query, ignoreCase = true) }
    }.take(40)

private fun leafRows(
    resources: Resources,
    node: SyllabusTopicNode,
    sectionIndex: Int,
    rootPath: List<Int>,
    ancestors: List<String>,
    sectionName: String,
    subjectId: String,
): List<StudyPickerRow> {
    if (node.children.isEmpty()) {
        val topicPath = pathOf(resources, ancestors)
        return listOf(
            StudyPickerRow(
                id = "search-$sectionIndex-${rootPath.joinToString("-")}",
                title = node.name,
                subtitle = resources.getString(R.string.home_picker_path, topicPath, sectionName),
                sectionIndex = sectionIndex,
                path = rootPath,
                topicPath = resources.getString(R.string.home_picker_path, sectionName, topicPath),
                opensSubtopics = false,
                nodeId = node.id.orEmpty(),
                subjectId = subjectId,
            ),
        )
    }
    return node.children.flatMapIndexed { index, child ->
        leafRows(
            resources = resources,
            node = child,
            sectionIndex = sectionIndex,
            rootPath = rootPath + index,
            ancestors = ancestors + child.name,
            sectionName = sectionName,
            subjectId = subjectId,
        )
    }
}

private fun countLeaves(node: SyllabusTopicNode): Int =
    if (node.children.isEmpty()) 1 else node.children.sumOf(::countLeaves)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationPickerContent(
    topicTitle: String,
    topicPath: String,
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    activityType: StudyActivityType,
    onSelectActivityType: (StudyActivityType) -> Unit,
    customEndMinuteOfDay: Int?,
    onSelectEndMinute: (Int) -> Unit,
    placementIssue: StudyPlacementIssue?,
) {
    val colors = AppTheme.colors
    val options = listOf(15, 30, 45)
    var showTimePicker by remember { mutableStateOf(false) }

    val liveEndMinute = (customEndMinuteOfDay ?: (currentMinuteOfDay() + selectedMinutes))
        .coerceIn(0, 24 * 60 - 1)
    val revisionDue = remember {
        java.time.LocalDate.now().plusDays(REVISION_INTERVAL_DAYS.toLong())
    }
    val revisionPattern = stringResource(R.string.home_revision_date_pattern)
    val revisionFormatter = remember(revisionPattern) {
        java.time.format.DateTimeFormatter.ofPattern(revisionPattern, java.util.Locale.getDefault())
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text(
                    text = topicTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                )
                if (topicPath.isNotBlank()) {
                    Text(
                        text = topicPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }

        Text(stringResource(R.string.home_activity_type), style = MaterialTheme.typography.bodyLarge, color = colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(
                StudyActivityType.LEARN to R.string.home_activity_learn,
                StudyActivityType.PRACTICE to R.string.home_activity_practice,
                StudyActivityType.REVISION to R.string.home_activity_revision,
                StudyActivityType.MOCK_TEST to R.string.home_activity_mock,
            ).forEach { (type, label) ->
                FilterChip(
                    selected = activityType == type,
                    onClick = { onSelectActivityType(type) },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        Text(
            text = stringResource(R.string.home_sprint_length),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEach { minutes ->
                FilterChip(
                    selected = selectedMinutes == minutes,
                    onClick = { onSelectMinutes(minutes) },
                    label = { Text(stringResource(R.string.home_duration_min, minutes)) },
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(Radius.lg),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_ends_at),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMinuteOfDay(liveEndMinute),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                    )
                }
                HorizontalDivider(color = colors.textMuted.copy(alpha = 0.14f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_revision_due),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = revisionDue.format(revisionFormatter),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                    )
                }
            }
        }

        placementIssue?.let { issue -> PlacementIssueMessage(issue) }

    }

    if (showTimePicker) {
        val initialHour = liveEndMinute / 60
        val initialMinute = liveEndMinute % 60
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(Radius.lg),
                color = colors.surfaceCard,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.home_cancel))
                        }
                        TextButton(onClick = {
                            onSelectEndMinute(timePickerState.hour * 60 + timePickerState.minute)
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.home_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacementIssueMessage(
    issue: StudyPlacementIssue,
    modifier: Modifier = Modifier,
) {
    val message = when (issue) {
        StudyPlacementIssue.INVALID_DURATION -> R.string.home_error_duration
        StudyPlacementIssue.OUTSIDE_DAY -> R.string.home_error_outside_day
        StudyPlacementIssue.OVERLAPS_STUDY -> R.string.home_error_study_overlap
        StudyPlacementIssue.OVERLAPS_FIXED -> R.string.home_error_fixed_overlap
        StudyPlacementIssue.NO_AVAILABLE_TIME -> R.string.home_error_no_available_time
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.warning,
        modifier = modifier,
    )
}

@Composable
private fun MissedDayRecoveryPanel(
    recovery: MissedDayRecoveryUi,
    onStartNextNow: () -> Unit,
    onReplan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Surface(
        shape = RoundedCornerShape(Radius.lg),
        color = colors.elevated,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.home_recovery_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.home_recovery_body,
                    recovery.missedCount,
                    recovery.missedCount,
                    formatDuration(recovery.availableTodayMinutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.home_recovery_next, recovery.nextSessionTitle),
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onStartNextNow,
                enabled = recovery.canStartNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.brandDeep,
                    contentColor = colors.onBrand,
                ),
                shape = RoundedCornerShape(Radius.pill),
            ) {
                Text(stringResource(R.string.home_start_next_now))
            }
            TextButton(onClick = onReplan) {
                Text(stringResource(R.string.home_replan_rest_today))
            }
            if (!recovery.canStartNow) {
                Text(
                    text = stringResource(R.string.home_recovery_busy_now),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

private fun pathOf(resources: Resources, parts: List<String>): String =
    parts.reduceOrNull { path, part -> resources.getString(R.string.home_picker_path, path, part) }.orEmpty()

private fun formatTopicHours(resources: Resources, node: SyllabusTopicNode): String {
    val hours = topicHours(node)
    return if (hours > 0) resources.getString(R.string.home_estimated_hours, hours.toInt()) else ""
}
