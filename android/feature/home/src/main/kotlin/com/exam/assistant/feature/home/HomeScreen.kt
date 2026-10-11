package com.exam.assistant.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.PendingSyllabusPick
import com.exam.assistant.domain.StudySessionRecord
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HomeRoute(
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    availabilityRepository: AvailabilityRepository,
    planRepository: PlanRepository,
    studySessionRepository: StudySessionRepository,
    topicProgressRepository: TopicProgressRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    revisionRepository: RevisionRepository,
    rollingPlanRepository: RollingPlanRepository,
    onSetupPlan: () -> Unit,
    onEditPlan: () -> Unit,
    onStartFocus: suspend (StudySessionRecord) -> Unit,
    pendingSyllabusPick: StateFlow<PendingSyllabusPick?>,
    onConsumedSyllabusPick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            examPackRepository,
            attemptRepository,
            availabilityRepository,
            planRepository,
            studySessionRepository,
            topicProgressRepository,
            studyPreferenceRepository,
            revisionRepository,
            rollingPlanRepository,
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

    val pendingPick by pendingSyllabusPick.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPick) {
        pendingPick?.let { pick ->
            viewModel.pickTopic(pick.nodeKey, pick.title, pick.sectionName, pick.subjectId, pick.topicPath)
            onConsumedSyllabusPick()
        }
    }

    HomeScreen(
        state = state,
        onSetupPlan = onSetupPlan,
        onEditPlan = onEditPlan,
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
        onSetActivityType = viewModel::setActivityType,
        onSetScheduledMinute = viewModel::setScheduledEndMinuteOfDay,
        onConfirmStart = viewModel::confirmStartSprint,
        onConfirmAddToPlan = viewModel::confirmAddToPlan,
        onStartScheduledSession = viewModel::startScheduledSession,
        onStartMissedSession = viewModel::startMissedSessionNow,
        onReplanRestOfToday = viewModel::replanRestOfToday,
        onOpenAddInGap = viewModel::openAddStudyInGap,
        onRescheduleToNextSlot = viewModel::rescheduleToNextSlot,
        onRescheduleToTomorrow = viewModel::rescheduleToTomorrowSameTime,
        onRescheduleToTime = viewModel::rescheduleToTime,
        onSkipSession = viewModel::skipSession,
        modifier = modifier,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSetupPlan: () -> Unit,
    onEditPlan: () -> Unit,
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
    onSetActivityType: (com.exam.assistant.domain.StudyActivityType) -> Unit,
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
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> Unit
            state.hasPlan -> HomeStudyContent(
                state = state,
                onEditPlan = onEditPlan,
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
                onSetActivityType = onSetActivityType,
                onSetScheduledMinute = onSetScheduledMinute,
                onConfirmStart = onConfirmStart,
                onConfirmAddToPlan = onConfirmAddToPlan,
                onStartScheduledSession = onStartScheduledSession,
                onStartMissedSession = onStartMissedSession,
                onReplanRestOfToday = onReplanRestOfToday,
                onOpenAddInGap = onOpenAddInGap,
                onRescheduleToNextSlot = onRescheduleToNextSlot,
                onRescheduleToTomorrow = onRescheduleToTomorrow,
                onRescheduleToTime = onRescheduleToTime,
                onSkipSession = onSkipSession,
            )
            else -> HomeEmptyContent(onSetupPlan = onSetupPlan)
        }
    }
}
