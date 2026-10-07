package com.exam.assistant.feature.syllabus

import com.exam.assistant.domain.ChapterPriority
import com.exam.assistant.domain.ChapterStage
import com.exam.assistant.domain.StageLabels
import com.exam.assistant.domain.SyllabusTickState

data class SyllabusTreeRow(
    val key: String,
    val name: String,
    val estimatedMinutes: Int,
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
    val classNumber: Int? = null,
    val isClassGroup: Boolean = false,
    val excluded: Boolean = false,
    /** Set on chapter rows once the PYQ table has loaded. Never zero for "no data": see [ChapterPriority]. */
    val priority: ChapterPriority? = null,
    /** A top-level chapter row: carries the chapter menu (rename, park, coaching). */
    val isChapter: Boolean = false,
    /** The student renamed this chapter; [originalName] is the bundled title. */
    val renamed: Boolean = false,
    val originalName: String = "",
    /** Chapter rows only: the student's visible stages, in order. */
    val stageChips: List<StageChipUi> = emptyList(),
)

/** One stage chip on a chapter row. [count] is 0/1 for ticks, N for Revised. Null label = default name. */
data class StageChipUi(
    val stage: ChapterStage,
    val customLabel: String?,
    val count: Int,
)

data class SyllabusChapterStat(
    val classNumber: Int?,
    val complete: Boolean,
)

data class SyllabusSubjectCard(
    val key: String,
    val name: String,
    val subjectId: String,
    val percent: Int,
    val expanded: Boolean,
    val rows: List<SyllabusTreeRow>,
    /** The subject's first topic — what "play" on the card itself jumps into. */
    val firstTopicKey: String?,
    val firstTopicTitle: String,
    val doneChapterCount: Int,
    val totalChapterCount: Int,
    val totalEstimatedMinutes: Int,
    val questions: Int,
    val chapterStats: List<SyllabusChapterStat>,
    val excludedCount: Int = 0,
)

data class SyllabusUiState(
    val loading: Boolean = true,
    val subjects: List<SyllabusSubjectCard> = emptyList(),
    val allCount: Int = 0,
    val dueCount: Int = 0,
    val completedPercent: Int = 0,
    val isEmpty: Boolean = false,
    /** Years the PYQ counts cover, for the "(2021–25)" label. Zero until loaded. */
    val pyqFirstYear: Int = 0,
    val pyqLastYear: Int = 0,
    val stageLabels: StageLabels = StageLabels(),
)
