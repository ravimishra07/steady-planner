package com.steadyline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.steadyline.core.design.AppTheme
import com.steadyline.core.design.AppType
import com.steadyline.core.design.Size

/**
 * Labels stay visible. An icon-only bar makes people guess, and both platforms
 * ship labelled tabs. Icons arrive with the real screens.
 */
@Composable
fun SteadylineBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tabBg)
            .navigationBarsPadding()
            .height(Size.tabBarHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            Text(
                text = tab.label,
                style = AppType.tabLabel,
                textAlign = TextAlign.Center,
                color = if (isSelected) colors.tabSelected else colors.tabUnselected,
                modifier = Modifier
                    .weight(1f)
                    .clickableTab { onSelect(tab) },
            )
        }
    }
}
