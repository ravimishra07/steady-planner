package com.exam.assistant.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.domain.StudySessionRecord

@Composable
fun HomeRoute(
    planStore: PlanStore,
    syllabusRepository: SyllabusRepository,
    syllabusStore: SyllabusStore,
    studySessionStore: StudySessionStore,
    onSetupPlan: () -> Unit,
    onStartFocus: suspend (StudySessionRecord) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            planStore,
            syllabusRepository,
            syllabusStore,
            studySessionStore,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(viewModel) {
        viewModel.focusRequests.collect { session -> onStartFocus(session) }
    }

    HomeScreen(
        state = state,
        onSetupPlan = onSetupPlan,
        onSelectDate = viewModel::selectDate,
        onToggleCalendarExpanded = viewModel::toggleCalendarExpanded,
        onOpenAdd = viewModel::openAddStudy,
        onDismissSheet = viewModel::dismissSheet,
        onBackInStudyPicker = viewModel::backInStudyPicker,
        onSelectPickerSection = viewModel::selectPickerSection,
        onOpenPickerSubtopics = viewModel::openPickerSubtopics,
        onSetPickerQuery = viewModel::setPickerQuery,
        onPickTopic = viewModel::pickTopic,
        onPickRevision = viewModel::pickRevision,
        onSetDuration = viewModel::setDurationMinutes,
        onSetScheduledMinute = viewModel::setScheduledEndMinuteOfDay,
        onConfirmStart = viewModel::confirmStartSprint,
        onStartScheduledSession = viewModel::startScheduledSession,
        onOpenAddInGap = viewModel::openAddStudyInGap,
        onRescheduleToNextSlot = viewModel::rescheduleToNextSlot,
        onRescheduleToTomorrow = viewModel::rescheduleToTomorrowSameTime,
        onRescheduleToTime = viewModel::rescheduleToTime,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSetupPlan: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onToggleCalendarExpanded: () -> Unit,
    onOpenAdd: () -> Unit,
    onDismissSheet: () -> Unit,
    onBackInStudyPicker: () -> Unit,
    onSelectPickerSection: (Int) -> Unit,
    onOpenPickerSubtopics: (List<Int>) -> Unit,
    onSetPickerQuery: (String) -> Unit,
    onPickTopic: (String, String, String, String, String) -> Unit,
    onPickRevision: (com.exam.assistant.domain.RevisionSuggestion) -> Unit,
    onSetDuration: (Int) -> Unit,
    onSetScheduledMinute: (Int) -> Unit,
    onConfirmStart: () -> Unit,
    onStartScheduledSession: (String) -> Unit,
    onOpenAddInGap: (Int, Int) -> Unit,
    onRescheduleToNextSlot: (String) -> Unit,
    onRescheduleToTomorrow: (String) -> Unit,
    onRescheduleToTime: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.hasPlan -> HomeStudyContent(
                state = state,
                onSelectDate = onSelectDate,
                onToggleCalendarExpanded = onToggleCalendarExpanded,
                onOpenAdd = onOpenAdd,
                onDismissSheet = onDismissSheet,
                onBackInStudyPicker = onBackInStudyPicker,
                onSelectPickerSection = onSelectPickerSection,
                onOpenPickerSubtopics = onOpenPickerSubtopics,
                onSetPickerQuery = onSetPickerQuery,
                onPickTopic = onPickTopic,
                onPickRevision = onPickRevision,
                onSetDuration = onSetDuration,
                onSetScheduledMinute = onSetScheduledMinute,
                onConfirmStart = onConfirmStart,
                onStartScheduledSession = onStartScheduledSession,
                onOpenAddInGap = onOpenAddInGap,
                onRescheduleToNextSlot = onRescheduleToNextSlot,
                onRescheduleToTomorrow = onRescheduleToTomorrow,
                onRescheduleToTime = onRescheduleToTime,
            )
            else -> HomeEmptyContent(onSetupPlan = onSetupPlan)
        }
    }
}
