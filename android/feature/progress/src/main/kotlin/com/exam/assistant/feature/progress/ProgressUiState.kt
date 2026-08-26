package com.exam.assistant.feature.progress

import com.exam.assistant.domain.SyllabusSectionProgress

data class ProgressUiState(
    val loading: Boolean = true,
    val hasPlan: Boolean = false,
    val daysUntilExam: Int = 0,
    val gapHours: Int = 0,
    val isShort: Boolean = false,
    val needHours: Int = 0,
    val haveHours: Int = 0,
    val coveragePercent: Int = 0,
    val todayDoneHours: String = "0.0",
    val todayBudgetHours: Int = 0,
    val todayPercent: Int = 0,
    val focusSessionsToday: Int = 0,
    val syllabusHoursDone: Int = 0,
    val syllabusHoursTotal: Int = 0,
    val sections: List<SyllabusSectionProgress> = emptyList(),
)
