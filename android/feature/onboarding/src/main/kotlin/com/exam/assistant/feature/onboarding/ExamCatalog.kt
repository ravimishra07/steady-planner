package com.exam.assistant.feature.onboarding

import androidx.annotation.StringRes

internal data class ExamOption(
    val id: String,
    @StringRes val labelRes: Int,
    val available: Boolean,
)

/** Mirrors prototype/data.js EXAMS. Only entries with a syllabus tree are selectable. */
internal object ExamCatalog {
    val options = listOf(
        ExamOption(EXAM_CGL, R.string.onboarding_exam_cgl, available = true),
        ExamOption("chsl", R.string.onboarding_exam_chsl, available = false),
        ExamOption("ntpc", R.string.onboarding_exam_ntpc, available = false),
        ExamOption("neet", R.string.onboarding_exam_neet, available = false),
        ExamOption("jee", R.string.onboarding_exam_jee, available = false),
        ExamOption("ibps", R.string.onboarding_exam_ibps, available = false),
    )

    fun isAvailable(examId: String): Boolean =
        options.any { it.id == examId && it.available }
}
