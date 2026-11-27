package io.github.mradzinski.eventus.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.app_name
import org.jetbrains.compose.resources.stringResource

fun main() {
    val appGraph = createGraph<AppGraph>()

    application { SampleWindow(appGraph.metroViewModelFactory) }
}

@Composable
private fun ApplicationScope.SampleWindow(viewModelFactory: MetroViewModelFactory) {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 480.dp, height = 820.dp),
    ) {
        App(viewModelFactory)
    }
}
