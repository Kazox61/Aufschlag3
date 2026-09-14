package com.kazox.aufschlag.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AufschlagGraph : NavKey {

    // ─── Tabs (top-level routes, each with its own back stack) ───

    @Serializable
    data object Home : AufschlagGraph

    @Serializable
    data object Settings : AufschlagGraph

    // ─── Pushed destinations ───
    // Screens opened from within a tab go here; they are pushed onto the current tab's stack.
}
