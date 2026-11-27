package io.github.mradzinski.eventus.sample.app.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.app.AppEvent.OnAppStarted
import io.github.mradzinski.eventus.sample.app.AppHandler
import io.github.mradzinski.eventus.sample.app.AppStore
import io.github.mradzinski.eventus.sample.domain.Connectivity

/**
 * Keeps the switch showing whether the app is offline. It never returns by itself, so it runs until the
 * view model is cleared. `Latest` means that sending the event again would start over, not observe twice.
 */
internal class OnAppStartedHandler(private val connectivity: Connectivity) : AppHandler<OnAppStarted> {

    override val eventType = OnAppStarted::class
    override val strategy = DispatchStrategy.Latest<OnAppStarted>()

    override suspend fun handle(event: OnAppStarted, store: AppStore) {
        connectivity.online.collect { online ->
            store.update { it.copy(offline = !online) }
        }
    }
}
