package com.steadyline.onboarding

import androidx.compose.runtime.Composable

@Composable
fun OnboardingApp(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingShell(
        state = state,
        title = titleFor(state.step),
        onBack = actions::back,
        onContinue = actions::continueFlow,
    ) {
        when (state.step) {
            Step.APPEARANCE -> AppearanceScreen(state, actions)
            Step.EXAM -> ExamScreen(state, actions)
            Step.COACHING -> CoachingScreen(state, actions)
            Step.COMMITMENTS -> CommitmentsScreen(state, actions)
            Step.DATE -> DateScreen(state, actions)
            Step.HOURS -> HoursScreen(state, actions)
            Step.SYLLABUS -> SyllabusScreen(state, actions)
            Step.PLAN -> PlanScreen(state)
        }
    }
}

private fun titleFor(step: Step) = when (step) {
    Step.APPEARANCE -> "Make it yours"
    Step.EXAM -> "Which exam?"
    Step.COACHING -> "Which coaching?"
    Step.COMMITMENTS -> "What's already fixed?"
    Step.DATE -> "Confirm the exam date"
    Step.HOURS -> "Hours per day"
    Step.SYLLABUS -> "Where are you right now?"
    Step.PLAN -> "Your plan"
}
