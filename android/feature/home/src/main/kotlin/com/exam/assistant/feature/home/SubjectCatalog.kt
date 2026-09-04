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
        SubjectOption("physics", R.string.home_subject_physics, R.string.home_subject_physics_short),
        SubjectOption("chemistry", R.string.home_subject_chemistry, R.string.home_subject_chemistry_short),
        SubjectOption("botany", R.string.home_subject_botany, R.string.home_subject_botany_short),
        SubjectOption("zoology", R.string.home_subject_zoology, R.string.home_subject_zoology_short),
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
