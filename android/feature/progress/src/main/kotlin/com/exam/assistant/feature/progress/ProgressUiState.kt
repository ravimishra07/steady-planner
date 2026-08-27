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
    val errorMessage: String? = null,
)

data class TargetSectionUi(
    val key: String,
    val name: String,
    val excluded: Boolean,
)

typealias ProgressUiState = InsightsUiState
