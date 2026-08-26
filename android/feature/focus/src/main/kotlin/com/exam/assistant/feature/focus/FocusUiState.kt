package com.exam.assistant.feature.focus

import com.exam.assistant.domain.BlockTag
import com.exam.assistant.domain.FocusStatus

data class FocusUiState(
    val loading: Boolean = true,
    val status: FocusStatus = FocusStatus.IDLE,
    val durationMinutes: Int = 50,
    val statusLabel: String = "",
    val blockTitle: String = "",
    val blockSubtitle: String = "",
    val blockTag: BlockTag? = null,
    val hasBlock: Boolean = false,
    val showStopDialog: Boolean = false,
)
