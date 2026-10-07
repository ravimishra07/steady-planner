package com.exam.assistant.feature.progress

import com.exam.assistant.domain.InsightsData
import com.exam.assistant.domain.InsightPeriod
import com.exam.assistant.domain.SyllabusSection

data class InsightsUiState(
    val loading: Boolean = true,
    val hasPlan: Boolean = false,
    val period: InsightPeriod = InsightPeriod.WEEK,
    val data: InsightsData? = null,
    val targetSections: List<TargetSectionUi> = emptyList(),
    val showTargetManager: Boolean = false,
    val loadFailed: Boolean = false,
    /** "Most-lost chapters" from the mistake log (§8.2), most tagged first. */
    val mostLost: List<LostChapterUi> = emptyList(),
)

data class LostChapterUi(val title: String, val count: Int)

data class TargetSectionUi(
    val key: String,
    val name: String,
    val excluded: Boolean,
)

typealias ProgressUiState = InsightsUiState
