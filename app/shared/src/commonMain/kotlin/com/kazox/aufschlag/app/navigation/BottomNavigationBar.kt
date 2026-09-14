package com.kazox.aufschlag.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.kazox.aufschlag.resources.Res
import com.kazox.aufschlag.resources.nav_home
import com.kazox.aufschlag.resources.nav_settings
import com.kazox.ui.components.icon.KazIcons
import com.kazox.ui.components.navigationbar.NavigationBar
import com.kazox.ui.components.navigationbar.NavigationBarAnimation
import com.kazox.ui.components.navigationbar.NavigationBarItem
import com.kazox.ui.components.navigationbar.NavigationBarVariant
import org.jetbrains.compose.resources.stringResource

@Composable
fun BottomNavigationBar(
    onNavigate: (AufschlagGraph) -> Unit,
    current: NavKey,
) {
    NavigationBar(variant = NavigationBarVariant.Floating) {
        NavigationBarItem(
            selected = current is AufschlagGraph.Home,
            onClick = { onNavigate(AufschlagGraph.Home) },
            icon = KazIcons.Home,
            label = stringResource(Res.string.nav_home),
            animation = NavigationBarAnimation.Tween,
        )
        NavigationBarItem(
            selected = current is AufschlagGraph.Settings,
            onClick = { onNavigate(AufschlagGraph.Settings) },
            icon = KazIcons.Settings,
            label = stringResource(Res.string.nav_settings),
            animation = NavigationBarAnimation.Tween,
        )
    }
}
