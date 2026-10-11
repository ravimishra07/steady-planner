package com.exam.assistant.feature.onboarding

import androidx.annotation.StringRes
import com.exam.assistant.domain.NEET_EXAM_ID

internal data class ExamOption(
    val id: String,
    @StringRes val labelRes: Int,
    val bundledSyllabus: Boolean,
)

/** Native exam packs available to onboarding. NEET is the product default. */
internal object ExamCatalog {
    val options = listOf(
        ExamOption(NEET_EXAM_ID, R.string.onboarding_exam_neet, bundledSyllabus = true),
    )

    fun contains(examId: String): Boolean = options.any { it.id == examId }
    fun hasBundledSyllabus(examId: String): Boolean =
        options.firstOrNull { it.id == examId }?.bundledSyllabus == true
}
