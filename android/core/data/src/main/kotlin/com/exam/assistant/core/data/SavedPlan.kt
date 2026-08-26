package com.exam.assistant.core.data

/**
 * What onboarding writes and the rest of the app reads later.
 * Kept flat so DataStore does not need a serializer on the critical path.
 */
data class SavedPlan(
    val examId: String,
    val daysUntilExam: Int,
    val workId: String,
    val weekdayHours: Float,
    val weekendHours: Float,
    val studyPlace: String,
)
