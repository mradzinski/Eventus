package io.github.mradzinski.eventus.sample.presentation.app.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.data.Connectivity
import io.github.mradzinski.eventus.sample.presentation.app.AppEvent.OnAppStarted
import io.github.mradzinski.eventus.sample.presentation.app.AppHandler
import io.github.mradzinski.eventus.sample.presentation.app.AppStore

/** Keeps the switch showing whether the app is offline, for as long as the app is visible. */
internal class OnAppStartedHandler(private val connectivity: Connectivity) : AppHandler<OnAppStarted> {

    override val eventType = OnAppStarted::class
    override val strategy = DispatchStrategy.WhileStarted

    override suspend fun handle(event: OnAppStarted, store: AppStore) {
        connectivity.online.collect { online ->
            store.update { it.copy(offline = !online) }
        }
    }
}
