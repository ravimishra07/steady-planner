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
    /** Pro, or the paywall switched off remotely. Gates the longer periods, forecast and most-lost chapters. */
    val proUnlocked: Boolean = false,
    /** One-shot: open the soft Pro prompt now. */
    val showSoftPrompt: Boolean = false,
)

/** Day and 7 days are free; longer Insights periods are Pro (§11.2). */
internal val InsightPeriod.isProPeriod: Boolean get() = (days ?: Long.MAX_VALUE) > FREE_PERIOD_DAYS

private const val FREE_PERIOD_DAYS = 7L

data class LostChapterUi(val title: String, val count: Int)

data class TargetSectionUi(
    val key: String,
    val name: String,
    val excluded: Boolean,
)

typealias ProgressUiState = InsightsUiState
