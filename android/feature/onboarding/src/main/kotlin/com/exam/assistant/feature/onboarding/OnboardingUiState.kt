package com.exam.assistant.feature.onboarding

import androidx.annotation.StringRes
import com.exam.assistant.domain.Cushion

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Exam,
    val examId: String = "",
    val daysUntilExam: Int = DEFAULT_DAYS,
    val workId: String = WORK_COLLEGE,
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val cushion: Cushion? = null,
    val finishing: Boolean = false,
) {
    val canGoBack: Boolean get() = step != OnboardingStep.Exam
}

internal data class DayShapeOption(
    val id: String,
    @StringRes val labelRes: Int,
    val weekdayHours: Float,
    val weekendHours: Float,
)

internal val DAY_SHAPES = listOf(
    DayShapeOption(WORK_FULLTIME, R.string.onboarding_shape_fulltime, 8f, 8f),
    DayShapeOption(WORK_WORKING, R.string.onboarding_shape_working, 3f, 8f),
    DayShapeOption(WORK_COLLEGE, R.string.onboarding_shape_college, 4f, 7f),
)

internal const val EXAM_CGL = "cgl"
internal const val WORK_FULLTIME = "ft"
internal const val WORK_WORKING = "job"
internal const val WORK_COLLEGE = "col"
internal const val DEFAULT_DAYS = 118
internal const val UNANNOUNCED_DAYS = 150
internal const val PROGRESS_SEGMENTS = 4
