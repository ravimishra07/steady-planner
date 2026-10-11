package com.exam.assistant.feature.onboarding.model

enum class OnboardingStep {
    Appearance,
    Coaching,
    Commitments,
    Date,
    Hours,
    Start,
    Syllabus,
    Plan,
    ;

    val progressIndex: Int? get() = when (this) {
        Appearance -> 0
        Coaching -> 1
        Commitments -> 2
        Date -> 3
        Hours -> 4
        Start -> 5
        Syllabus, Plan -> PROGRESS_SEGMENTS - 1
    }

    fun next(): OnboardingStep? = when (this) {
        Appearance -> Coaching
        Coaching -> Commitments
        Commitments -> Date
        Date -> Hours
        Hours -> Start
        Start -> Syllabus
        Syllabus -> Plan
        Plan -> null
    }

    fun previous(): OnboardingStep? = when (this) {
        Appearance -> null
        Coaching -> Appearance
        Commitments -> Coaching
        Date -> Commitments
        Hours -> Date
        Start -> Hours
        Syllabus -> Start
        Plan -> Syllabus
    }
}
