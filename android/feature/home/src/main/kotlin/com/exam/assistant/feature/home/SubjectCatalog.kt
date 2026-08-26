package com.exam.assistant.feature.home

import androidx.annotation.StringRes
import com.exam.assistant.domain.BlockTag

internal data class SubjectOption(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val shortRes: Int,
)

internal object SubjectCatalog {
    val options = listOf(
        SubjectOption("quant", R.string.home_subject_quant, R.string.home_subject_quant_short),
        SubjectOption("reasoning", R.string.home_subject_reasoning, R.string.home_subject_reasoning_short),
        SubjectOption("ga", R.string.home_subject_ga, R.string.home_subject_ga_short),
        SubjectOption("english", R.string.home_subject_english, R.string.home_subject_english_short),
    )

    fun allIds(): Set<String> = options.map { it.id }.toSet()

    fun find(id: String): SubjectOption? = options.firstOrNull { it.id == id }
}

@StringRes
internal fun BlockTag.labelRes(): Int = when (this) {
    BlockTag.READ -> R.string.home_tag_read
    BlockTag.PRACTICE -> R.string.home_tag_practice
    BlockTag.REVISE -> R.string.home_tag_revise
}
