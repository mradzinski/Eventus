package io.github.mradzinski.eventus.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.mradzinski.eventus.sample.app.AppViewModel
import io.github.mradzinski.eventus.sample.app.OfflineSwitch
import io.github.mradzinski.eventus.sample.navigation.AppNavigation

/**
 * The whole sample app: a task list and a task editor.
 *
 * @param viewModelFactory What creates the view models: the one of the platform's graph.
 */
@Composable
fun App(viewModelFactory: MetroViewModelFactory) {
    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModelFactory) {
        AppContent()
    }
}

@Composable
private fun AppContent() {
    val viewModel = metroViewModel<AppViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // The switch belongs to the app, not to a screen. Each screen receives it ready-made and only
            // decides where it goes in its bar, so no screen knows anything about being offline.
            AppNavigation(
                offlineSwitch = {
                    OfflineSwitch(
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                },
            )
        }
    }
}
