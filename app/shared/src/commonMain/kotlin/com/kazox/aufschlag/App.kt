package com.kazox.aufschlag

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kazox.aufschlag.app.navigation.NavigationRoot
import com.kazox.ui.foundation.KazAccentPreset
import com.kazox.ui.foundation.KazPalette
import com.kazox.ui.foundation.KazStylePreset
import com.kazox.ui.foundation.KazTheme

@Composable
@Preview
fun App() {
    KazTheme(
        palette = KazPalette.Zinc,
        accent = KazAccentPreset.Default,
        isDark = isSystemInDarkTheme(),
        preset = KazStylePreset.Default,
    ) {
        // No safeDrawingPadding here: the Scaffold owns the system insets (its bars lift
        // clear of the status bar / home indicator while content flows edge to edge behind
        // them). Padding here would shrink the whole scaffold above the safe area instead.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KazTheme.colors.background),
        ) {
            NavigationRoot()
        }
    }
}
