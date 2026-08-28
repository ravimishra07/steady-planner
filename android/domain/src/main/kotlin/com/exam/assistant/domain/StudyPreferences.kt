package com.exam.assistant.domain

import java.time.DayOfWeek

/**
 * What the student SAYS about how they want to be scheduled — soft
 * preferences the scheduler should prefer but must not treat as a hard
 * constraint (that's [WeeklyAvailability]). Never populate these fields
 * from observed behavior; that belongs in a derived [StudyBehaviorProfile].
 */
data class StudyPreferences(
    val attemptId: String,

    val preferredSessionMinutes: Int? = null,
    val shortBreakMinutes: Int? = null,
    val longBreakMinutes: Int? = null,

    val autoScheduleEnabled: Boolean = false,
    val autoRescheduleMissed: Boolean = false,
    val autoScheduleRevision: Boolean = true,

    val defaultStudyPlace: String? = null,
)

data class SubjectStudyPreference(
    val attemptId: String,
    val subjectId: String,
    val priority: SubjectPriority,
    val targetMinutesPerWeek: Int? = null,
)

enum class SubjectPriority {
    LOW,
    NORMAL,
    HIGH,
}

data class SubjectPreferredWindow(
    val id: String,
    val attemptId: String,
    val subjectId: String,

    val dayOfWeek: DayOfWeek?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,

    val strength: PreferenceStrength,
)

enum class PreferenceStrength {
    PREFERRED,
    AVOID,
}
