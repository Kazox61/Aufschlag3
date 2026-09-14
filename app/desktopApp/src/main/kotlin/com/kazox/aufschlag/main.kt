package com.kazox.aufschlag

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kazox.aufschlag.app.di.initKoin

fun main() = application {
    initKoin()
    val state = rememberWindowState(
        width = 393.dp,
        height = 852.dp,
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Aufschlag",
        state = state
    ) {
        App()
    }
}
