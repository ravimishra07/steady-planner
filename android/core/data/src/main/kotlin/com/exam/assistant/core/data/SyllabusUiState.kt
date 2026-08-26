package com.exam.assistant.core.data

data class SyllabusUiState(
    val doneLeaves: Set<String> = emptySet(),
    val openNodes: Set<String> = emptySet(),
    val sectionIndex: Int = 0,
)
