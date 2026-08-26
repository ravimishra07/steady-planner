package com.exam.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.exam.assistant.core.design.Size

/**
 * A tap target with no ripple and a minimum height, so tab rows keep the 48dp
 * floor without every call site repeating it.
 */
fun Modifier.clickableTab(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .heightIn(min = Size.touchTarget)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
