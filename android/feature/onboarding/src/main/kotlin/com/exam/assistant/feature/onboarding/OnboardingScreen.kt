package com.exam.assistant.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.PlanStore

@Composable
fun OnboardingRoute(
    planStore: PlanStore,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.Factory(planStore),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onFinished()
    }

    OnboardingScreen(
        state = state,
        totalHours = viewModel.totalHours(state),
        onBack = viewModel::back,
        onContinue = viewModel::continueFromCurrent,
        onUnannouncedDate = viewModel::useUnannouncedDate,
        onSelectWork = viewModel::selectWork,
        onWeekdayChange = viewModel::setWeekdayHours,
        onWeekendChange = viewModel::setWeekendHours,
        onStudyPlaceChange = viewModel::setStudyPlace,
        onSelectExam = viewModel::selectExam,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    totalHours: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onUnannouncedDate: () -> Unit,
    onSelectWork: (String) -> Unit,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onStudyPlaceChange: (String) -> Unit,
    onSelectExam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        when (state.step) {
            OnboardingStep.Exam -> R.string.onboarding_exam_title
            OnboardingStep.Date -> R.string.onboarding_date_title
            OnboardingStep.Shape -> R.string.onboarding_shape_title
            OnboardingStep.Hours -> R.string.onboarding_hours_title
            OnboardingStep.Cushion -> R.string.onboarding_plan_title
        },
    )
    val cta = stringResource(
        when (state.step) {
            OnboardingStep.Hours -> R.string.onboarding_build_plan
            OnboardingStep.Cushion -> R.string.onboarding_start_day_one
            else -> R.string.onboarding_continue
        },
    )

    OnboardingShell(
        title = title,
        progressIndex = state.step.progressIndex,
        canGoBack = state.canGoBack,
        ctaLabel = cta,
        continueEnabled = when (state.step) {
            OnboardingStep.Exam -> ExamCatalog.isAvailable(state.examId)
            else -> true
        },
        onBack = onBack,
        onContinue = onContinue,
        modifier = modifier,
    ) {
        when (state.step) {
            OnboardingStep.Exam -> OnboardingExamStep(
                selectedExamId = state.examId,
                onSelectExam = onSelectExam,
            )
            OnboardingStep.Date -> OnboardingDateStep(
                daysUntilExam = state.daysUntilExam,
                onUnannouncedDate = onUnannouncedDate,
            )
            OnboardingStep.Shape -> OnboardingShapeStep(
                selectedWorkId = state.workId,
                onSelect = onSelectWork,
            )
            OnboardingStep.Hours -> OnboardingHoursStep(
                weekdayHours = state.weekdayHours,
                weekendHours = state.weekendHours,
                studyPlace = state.studyPlace,
                totalHours = totalHours,
                onWeekdayChange = onWeekdayChange,
                onWeekendChange = onWeekendChange,
                onStudyPlaceChange = onStudyPlaceChange,
            )
            OnboardingStep.Cushion -> {
                val cushion = state.cushion
                if (cushion != null) {
                    OnboardingCushionStep(state = state, cushion = cushion)
                }
            }
        }
    }
}
