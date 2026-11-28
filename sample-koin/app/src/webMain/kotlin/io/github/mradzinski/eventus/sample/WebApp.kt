package io.github.mradzinski.eventus.sample

import androidx.compose.runtime.Composable

/** The sample app as a browser shows it: [App], with its back stack tied to the browser's Back button. */
@Composable
fun WebApp() {
    App(platformEffects = { backStack -> BrowserHistory(backStack) })
}
