package com.exam.assistant

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
        windowInsets = WindowInsets.navigationBars,
    ) {
        Tab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
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
                    indicatorColor = colors.selectionContainer,
                ),
            )
        }
    }
}

/** The bottom bar. Order here is order on screen. */
enum class Tab(
    val route: Route,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(Route.Home, R.string.tab_today, Icons.Filled.Home, Icons.Outlined.Home),
    Syllabus(
        Route.Syllabus,
        R.string.tab_syllabus,
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    Focus(Route.Focus, R.string.tab_focus, Icons.Filled.Timer, Icons.Outlined.Timer),
    Progress(Route.Progress, R.string.tab_progress, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    Settings(Route.Settings, R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
