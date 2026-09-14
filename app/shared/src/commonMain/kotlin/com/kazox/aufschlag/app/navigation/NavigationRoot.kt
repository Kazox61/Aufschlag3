package com.kazox.aufschlag.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.kazox.aufschlag.feature.home.presentation.HomeRoot
import com.kazox.aufschlag.feature.settings.presentation.SettingsRoot
import com.kazox.ui.foundation.KazTheme

/**
 * Tab switches swap on the spot: the tapped tab is simply there, which is what makes the bottom
 * bar feel responsive. Attached as metadata on the tab-root entries so it outranks NavDisplay's
 * specs (nav3 priority: transitioning entry metadata > scene metadata > NavDisplay defaults).
 * Screens pushed inside a tab carry no metadata, so they keep the animated transitions,
 * including when popping back to the tab root: a pop reads the *leaving* screen's metadata, and
 * that screen is the pushed one, not the tab root.
 */
private val instantSwitch: Map<String, Any> =
    NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None } +
        NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }

@Composable
fun NavigationRoot() {
    // Only the bottom bar's tabs are top-level routes — each gets its own back stack, and
    // Navigator switches tabs for these keys. Anything else navigated to is pushed onto the
    // current tab's stack instead.
    val navigationState = rememberNavigationState(
        startRoute = AufschlagGraph.Home,
        topLevelRoutes = setOf(
            AufschlagGraph.Home,
            AufschlagGraph.Settings,
        ),
    )
    val navigator = rememberNavigator(navigationState)

    val bottomBar: @Composable () -> Unit = {
        BottomNavigationBar(
            onNavigate = { route -> navigator.navigate(route) },
            current = navigationState.topLevelRoute,
        )
    }

    val entryProvider = entryProvider<NavKey> {
        entry<AufschlagGraph.Home>(metadata = instantSwitch) {
            HomeRoot(bottomBar = bottomBar)
        }
        entry<AufschlagGraph.Settings>(metadata = instantSwitch) {
            SettingsRoot(bottomBar = bottomBar)
        }
    }

    // KazTheme.motion is a composable read; the spec lambdas below are not composable, so the
    // duration is captured here.
    val fadeMs = KazTheme.motion.durationDefault

    NavDisplay(
        entries = navigationState.toEntries(entryProvider),
        onBack = navigator::goBack,
        transitionSpec = {
            fadeIn(tween(fadeMs)) togetherWith
                ExitTransition.KeepUntilTransitionsFinished
        },
        // Returning to the start route shrinks the entries list to just Home, which NavDisplay
        // classifies as a pop — without this it falls back to the platform pop transition, and
        // the iOS default is a full-screen slide.
        //
        // On pops NavDisplay puts the entering screen *underneath* the leaving one, so
        // KeepUntilTransitionsFinished would hold the old screen opaque on top until everything
        // settles — a hold-then-cut that makes the Home tab feel unresponsive. On a pop the old
        // screen is the one on top, so fade *it* out; both screens share the background color,
        // which makes this read as a clean crossfade.
        popTransitionSpec = {
            fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))
        },
    )
}
