package com.exam.assistant.feature.syllabus

import com.exam.assistant.domain.SyllabusTickState

data class SyllabusTreeRow(
    val key: String,
    val name: String,
    val hoursLabel: String,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val tickState: SyllabusTickState,
)

data class SyllabusUiState(
    val loading: Boolean = true,
    val sectionIndex: Int = 0,
    val sectionTabs: List<String> = emptyList(),
    val summary: String = "",
    val rows: List<SyllabusTreeRow> = emptyList(),
)
