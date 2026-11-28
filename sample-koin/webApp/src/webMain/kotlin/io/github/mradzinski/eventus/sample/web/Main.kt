package io.github.mradzinski.eventus.sample.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.mradzinski.eventus.sample.WebApp
import io.github.mradzinski.eventus.sample.startSample

/** Starts the web sample (JS and Wasm): the app drawn into the page body. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startSample()

    ComposeViewport { WebApp() }
}
