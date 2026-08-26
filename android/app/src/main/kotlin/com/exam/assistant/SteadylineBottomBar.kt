package com.exam.assistant

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.exam.assistant.core.design.AppTheme

@Composable
fun SteadylineBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    NavigationBar(
        modifier = modifier,
        containerColor = colors.tabBg,
        contentColor = colors.text,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.tabSelected,
                    selectedTextColor = colors.tabSelected,
                    unselectedIconColor = colors.tabUnselected,
                    unselectedTextColor = colors.tabUnselected,
                    indicatorColor = colors.brandContainer,
                ),
            )
        }
    }
}

/** The bottom bar. Order here is order on screen. */
enum class Tab(
    val route: Route,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(Route.Home, R.string.tab_today, Icons.Outlined.Home),
    Syllabus(Route.Syllabus, R.string.tab_syllabus, Icons.AutoMirrored.Outlined.MenuBook),
    Focus(Route.Focus, R.string.tab_focus, Icons.Outlined.Timer),
    Progress(Route.Progress, R.string.tab_progress, Icons.Outlined.BarChart),
    Settings(Route.Settings, R.string.tab_more, Icons.Outlined.MoreHoriz),
}
