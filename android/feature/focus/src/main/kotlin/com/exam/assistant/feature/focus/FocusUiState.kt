package com.exam.assistant.feature.focus

import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusStatus
import com.exam.assistant.domain.StudyOutcome

data class FocusQueueItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val tag: BlockTag,
    val durationMinutes: Int,
    val completed: Boolean = false,
)

data class FocusUiState(
    val loading: Boolean = true,
    val status: FocusStatus = FocusStatus.IDLE,
    val durationMinutes: Int = 50,
    val blockTitle: String = "",
    val blockSubtitle: String = "",
    val blockTag: BlockTag? = null,
    val hasBlock: Boolean = false,
    val queue: List<FocusQueueItem> = emptyList(),
    val showStopDialog: Boolean = false,
    val outcome: StudyOutcome? = null,
    /** Set after a Learn session on a chapter not yet learned: "Mark chapter learned?" (§5.1). */
    val learnPrompt: LearnPrompt? = null,
)

data class LearnPrompt(
    val chapterId: String,
    val chapterTitle: String,
)
