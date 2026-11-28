package io.github.mradzinski.eventus.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.createGraph

/**
 * Starts the web app (JS and Wasm): creates the graph and draws [App] into the page body, with its back
 * stack tied to the browser's Back button.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun startWebApp() {
    val appGraph = createGraph<AppGraph>()

    ComposeViewport {
        App(
            viewModelFactory = appGraph.metroViewModelFactory,
            platformEffects = { backStack -> BrowserHistory(backStack) },
        )
    }
}
