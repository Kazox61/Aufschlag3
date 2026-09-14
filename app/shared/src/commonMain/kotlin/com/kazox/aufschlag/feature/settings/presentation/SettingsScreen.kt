package com.kazox.aufschlag.feature.settings.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazox.aufschlag.resources.Res
import com.kazox.aufschlag.resources.nav_settings
import com.kazox.ui.components.scaffold.Scaffold
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoot(
    bottomBar: @Composable () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        bottomBar = bottomBar,
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun SettingsScreen(
    bottomBar: @Composable () -> Unit,
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    Scaffold(
        bottomBar = bottomBar,
        // Content passes behind the floating bar rather than stopping above it.
        overlayBottomBar = true,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.nav_settings),
                variant = TextVariant.H2,
            )
        }
    }
}
