package io.github.mradzinski.eventus.sample.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.mradzinski.eventus.sample.App
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.app_name
import io.github.mradzinski.eventus.sample.startSample
import org.jetbrains.compose.resources.stringResource

fun main() {
    startSample()

    application { SampleWindow() }
}

@Composable
private fun ApplicationScope.SampleWindow() {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 480.dp, height = 820.dp),
    ) {
        App()
    }
}
