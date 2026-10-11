package com.exam.assistant.feature.progress

import com.exam.assistant.domain.DayLog
import java.time.LocalDate

/** "What did I study" for one day (PRODUCTION_V1_PLAN §5.6). */
data class DayLogUiState(
    val date: LocalDate,
    val loading: Boolean = true,
    val log: DayLog? = null,
    val loadFailed: Boolean = false,
)
