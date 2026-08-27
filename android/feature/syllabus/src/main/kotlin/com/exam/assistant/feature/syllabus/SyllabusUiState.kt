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
    val percent: Int,
    val doneLeafCount: Int,
    val totalLeafCount: Int,
    /** For each ancestor column (0 until depth): does that ancestor have a later sibling, i.e. should its line continue past this row? */
    val ancestorContinues: List<Boolean>,
    /** Is this row the last child among its own siblings? Decides whether its own connector continues downward. */
    val isLastChild: Boolean,
    val subjectId: String,
    val sectionName: String,
    val topicPath: String,
)

data class SyllabusSubjectCard(
    val key: String,
    val name: String,
    val shortLabel: String,
    val subjectId: String,
    val percent: Int,
    val timeSpentLabel: String,
    val expanded: Boolean,
    val rows: List<SyllabusTreeRow>,
    /** The subject's first topic — what "play" on the card itself jumps into. */
    val firstTopicKey: String?,
    val firstTopicTitle: String,
)

data class SyllabusUiState(
    val loading: Boolean = true,
    val subjects: List<SyllabusSubjectCard> = emptyList(),
    val allCount: Int = 0,
    val dueCount: Int = 0,
    val completedPercentLabel: String = "0.00%",
    val timeSpentLabel: String = "0h 00m",
)
