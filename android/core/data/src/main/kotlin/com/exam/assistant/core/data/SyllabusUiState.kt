package com.exam.assistant.core.data

data class SyllabusUiState(
    val doneLeaves: Set<String> = emptySet(),
    val openNodes: Set<String> = emptySet(),
    val sectionIndex: Int = 0,
    /** Section keys excluded from the student's target syllabus, e.g. section_2. */
    val excludedSectionKeys: Set<String> = emptySet(),
)
