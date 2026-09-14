package com.kazox.aufschlag.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey

class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    /**
     * Puts [route] where the current destination is, so back skips the screen being left.
     *
     * For hand-offs between two steps of one flow, where returning to the first step is
     * meaningless once the second exists. Falls back to a plain push at the root of a tab,
     * which has nothing to replace.
     */
    fun replaceCurrent(route: NavKey) {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Back stack for ${state.topLevelRoute} doesn't exist")

        if (currentStack.lastOrNull() != state.topLevelRoute) currentStack.removeLastOrNull()
        currentStack.add(route)
    }

    /**
     * Lands on [route]'s tab with nothing stacked above it, anywhere.
     *
     * The tab being left is emptied too, not just the one arrived at: its stack survives the
     * switch, so leaving it alone would strand a finished flow's screen there, waiting to
     * reappear the next time that tab is opened.
     */
    fun resetTo(route: NavKey) {
        val leaving = state.topLevelRoute
        state.backStacks[leaving]?.let { stack ->
            stack.clear()
            stack.add(leaving)
        }

        val target = state.backStacks[route]
            ?: error("$route is not a top-level route")
        target.clear()
        target.add(route)
        state.topLevelRoute = route
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Back stack for ${state.topLevelRoute} doesn't exist")
        val currentRoute = currentStack.last()

        if(currentRoute == state.topLevelRoute) {
            state.topLevelRoute = state.startRoute
        } else {
            currentStack.removeLastOrNull()
        }
    }
}

@Composable
fun rememberNavigator(state: NavigationState): Navigator = remember(state) { Navigator(state) }
