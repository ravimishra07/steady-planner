package com.exam.assistant.feature.syllabus

import java.time.LocalDate

data class OrganiseChapterRow(
    val id: String,
    val subjectId: String,
    val subjectName: String,
    val title: String,
    val estimatedMinutes: Int,
    val excluded: Boolean,
    val custom: Boolean,
)

data class OrganiseDaySummary(
    val date: LocalDate,
    val plannedMinutes: Int,
    val blockCount: Int,
)

data class OrganisePlanRow(
    val id: String,
    val title: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val isRevision: Boolean,
)

data class OrganiseUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val examId: String = "",
    val selectedSubjectId: String = "",
    val subjects: List<Pair<String, String>> = emptyList(),
    val chapters: List<OrganiseChapterRow> = emptyList(),
    val week: List<OrganiseDaySummary> = emptyList(),
    val todaySessions: List<OrganisePlanRow> = emptyList(),
    val beforeIncludedCount: Int = 0,
    val dirty: Boolean = false,
    val saved: Boolean = false,
) {
    val includedCount: Int get() = chapters.count { !it.excluded }
    val excludedCount: Int get() = chapters.count { it.excluded }
    val totalMinutes: Int get() = chapters.filterNot { it.excluded }.sumOf { it.estimatedMinutes }
}
