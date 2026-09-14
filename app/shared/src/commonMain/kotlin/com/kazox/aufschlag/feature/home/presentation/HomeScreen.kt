package com.kazox.aufschlag.feature.home.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazox.aufschlag.resources.Res
import com.kazox.aufschlag.resources.nav_home
import com.kazox.ui.components.scaffold.Scaffold
import com.kazox.ui.components.text.Text
import com.kazox.ui.components.text.TextVariant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoot(
    bottomBar: @Composable () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        bottomBar = bottomBar,
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun HomeScreen(
    bottomBar: @Composable () -> Unit,
    state: HomeState,
    onAction: (HomeAction) -> Unit,
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
                text = stringResource(Res.string.nav_home),
                variant = TextVariant.H2,
            )
        }
    }
}
