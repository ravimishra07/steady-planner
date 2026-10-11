package com.exam.assistant.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.design.AccentPalette
import com.exam.assistant.core.design.BackgroundAppearance
import kotlin.math.roundToInt

@Composable
fun OnboardingRoute(
    planStore: PlanStore,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    availabilityRepository: AvailabilityRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    topicProgressRepository: TopicProgressRepository,
    planRepository: PlanRepository,
    rollingPlanRepository: RollingPlanRepository,
    dispatchers: AppDispatchers,
    background: BackgroundAppearance,
    onBackground: (BackgroundAppearance) -> Unit,
    accentPalette: AccentPalette,
    onAccentPalette: (AccentPalette) -> Unit,
    onFinished: (openOrganise: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.Factory(
            planStore,
            examPackRepository,
            attemptRepository,
            availabilityRepository,
            studyPreferenceRepository,
            targetSyllabusRepository,
            topicProgressRepository,
            planRepository,
            rollingPlanRepository,
            dispatchers,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val openOrganiseAfterFinish by viewModel.openOrganiseAfterFinish.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onFinished(openOrganiseAfterFinish)
    }
    BackHandler(enabled = state.canGoBack, onBack = viewModel::back)

    OnboardingScreen(
        state = state,
        background = background,
        accentPalette = accentPalette,
        canContinue = viewModel.canContinue(state),
        commitmentIssues = viewModel.commitmentIssues(state),
        capacityIssues = viewModel.capacityIssues(state),
        onBack = viewModel::back,
        onContinue = viewModel::continueFromCurrent,
        onBackground = onBackground,
        onAccentPalette = onAccentPalette,
        onSelectCoaching = viewModel::selectCoaching,
        onAddCommitment = viewModel::addCommitment,
        onRemoveCommitment = viewModel::removeCommitment,
        onRenameCommitment = viewModel::renameCommitment,
        onToggleCommitmentDay = viewModel::toggleCommitmentDay,
        onCommitmentStart = viewModel::setCommitmentStart,
        onCommitmentEnd = viewModel::setCommitmentEnd,
        onWake = viewModel::setWakeMinute,
        onSleep = viewModel::setSleepMinute,
        onDate = viewModel::setTargetDate,
        onSelectWork = viewModel::selectWork,
        onWeekdayChange = viewModel::setWeekdayHours,
        onWeekendChange = viewModel::setWeekendHours,
        onToggleTargetSubject = viewModel::toggleTargetSubject,
        onToggleTargetChapter = viewModel::toggleTargetChapter,
        onDismissFinishError = viewModel::dismissFinishError,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    background: BackgroundAppearance,
    accentPalette: AccentPalette,
    canContinue: Boolean,
    commitmentIssues: List<CommitmentIssue>,
    capacityIssues: List<CapacityIssue>,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onBackground: (BackgroundAppearance) -> Unit,
    onAccentPalette: (AccentPalette) -> Unit,
    onSelectCoaching: (String) -> Unit,
    onAddCommitment: (String) -> Unit,
    onRemoveCommitment: (String) -> Unit,
    onRenameCommitment: (String, String) -> Unit,
    onToggleCommitmentDay: (String, Int) -> Unit,
    onCommitmentStart: (String, Int) -> Unit,
    onCommitmentEnd: (String, Int) -> Unit,
    onWake: (Int) -> Unit,
    onSleep: (Int) -> Unit,
    onDate: (java.time.LocalDate) -> Unit,
    onSelectWork: (String) -> Unit,
    onWeekdayChange: (Float) -> Unit,
    onWeekendChange: (Float) -> Unit,
    onToggleTargetSubject: (String) -> Unit,
    onToggleTargetChapter: (String) -> Unit,
    onDismissFinishError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val title = stringResource(
        when (state.step) {
            OnboardingStep.Appearance -> R.string.onboarding_appearance_title
            OnboardingStep.Coaching -> R.string.onboarding_coaching_title
            OnboardingStep.Commitments -> R.string.onboarding_commitments_title
            OnboardingStep.Date -> R.string.onboarding_date_title
            OnboardingStep.Hours -> R.string.onboarding_hours_title
            OnboardingStep.Syllabus -> if (state.useProvidedSyllabus) R.string.onboarding_syllabus_title else R.string.onboarding_syllabus_custom_title
            OnboardingStep.Plan -> R.string.onboarding_plan_title
        },
    )
    val cta = stringResource(
        when (state.step) {
            OnboardingStep.Syllabus -> R.string.onboarding_build_plan
            OnboardingStep.Plan -> if (state.useProvidedSyllabus) R.string.onboarding_start_day_one else R.string.onboarding_plan_add_chapters
            else -> R.string.onboarding_continue
        },
    )

    OnboardingShell(
        title = title,
        progressIndex = state.step.progressIndex,
        canGoBack = state.canGoBack,
        ctaLabel = cta,
        continueEnabled = canContinue,
        onBack = onBack,
        onContinue = onContinue,
        modifier = modifier,
    ) {
        when (state.step) {
            OnboardingStep.Appearance -> OnboardingAppearanceStep(background, accentPalette, onBackground, onAccentPalette)
            OnboardingStep.Coaching -> OnboardingCoachingStep(state.coachingId, onSelectCoaching)
            OnboardingStep.Commitments -> OnboardingCommitmentsStep(
                state,
                commitmentIssues,
                onAddCommitment,
                onRemoveCommitment,
                onRenameCommitment,
                onToggleCommitmentDay,
                onCommitmentStart,
                onCommitmentEnd,
                onWake,
                onSleep,
            )
            OnboardingStep.Date -> OnboardingTargetDateStep(state.targetDate, false, onDate)
            OnboardingStep.Hours -> {
                OnboardingHoursParityStep(
                    state = state,
                    capacityIssues = capacityIssues,
                    onSelectWork = onSelectWork,
                    onWeekdayChange = onWeekdayChange,
                    onWeekendChange = onWeekendChange,
                )
            }
            OnboardingStep.Syllabus -> OnboardingSyllabusStep(
                state = state,
                onToggleSubject = onToggleTargetSubject,
                onToggleChapter = onToggleTargetChapter,
            )
            OnboardingStep.Plan -> state.cushion?.let {
                OnboardingPlanStep(
                    state = state,
                    cushion = it,
                    onWeekdayChange = onWeekdayChange,
                    onWeekendChange = onWeekendChange,
                    onDate = onDate,
                )
            }
                ?: CircularProgressIndicator()
        }
    }

    if (state.finishFailed) {
        AlertDialog(
            onDismissRequest = onDismissFinishError,
            confirmButton = {
                TextButton(onClick = onDismissFinishError) { Text(stringResource(R.string.onboarding_dismiss)) }
            },
            text = { Text(stringResource(R.string.onboarding_finish_failed)) },
        )
    }
}

@Composable
internal fun CapacityIssuesCard(issues: List<CapacityIssue>) {
    androidx.compose.material3.Surface(
        modifier = Modifier.padding(top = com.exam.assistant.core.design.Spacing.md),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.exam.assistant.core.design.Radius.lg),
        color = com.exam.assistant.core.design.AppTheme.colors.dangerContainer,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(com.exam.assistant.core.design.Spacing.lg)) {
            Text(stringResource(R.string.onboarding_hours_do_not_fit), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            issues.forEach { issue ->
                val value = when (issue) {
                    is CapacityIssue.Weekday -> stringResource(R.string.onboarding_hours_weekday_free, formatHours(issue.freeHours))
                    is CapacityIssue.Weekend -> stringResource(R.string.onboarding_hours_weekend_free, formatHours(issue.freeHours))
                }
                Text(value)
            }
            Text(stringResource(R.string.onboarding_hours_fix_hint))
        }
    }
}

private fun formatHours(value: Float): String = if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
