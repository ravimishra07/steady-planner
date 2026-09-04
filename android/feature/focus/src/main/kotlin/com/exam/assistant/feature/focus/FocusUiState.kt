package com.exam.assistant.feature.focus

import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusStatus

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
)
