package com.exam.assistant.feature.onboarding

enum class OnboardingStep {
    Exam,
    Date,
    Shape,
    Hours,
    Cushion,
    ;

    val progressIndex: Int?
        get() = when (this) {
            Exam -> 0
            Date -> 1
            Shape -> 2
            Hours -> 3
            Cushion -> null
        }

    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)
}
