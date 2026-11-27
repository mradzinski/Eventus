package io.github.mradzinski.eventus.sample.app.handlers

import io.github.mradzinski.eventus.sample.app.AppEvent.OnOfflineChange
import io.github.mradzinski.eventus.sample.app.AppHandler
import io.github.mradzinski.eventus.sample.app.AppStore
import io.github.mradzinski.eventus.sample.domain.Connectivity

/** The switch does not touch the state: `OnAppStartedHandler` observes the setting and shows it. */
internal class OnOfflineChangeHandler(private val connectivity: Connectivity) : AppHandler<OnOfflineChange> {

    override val eventType = OnOfflineChange::class

    override suspend fun handle(event: OnOfflineChange, store: AppStore) {
        connectivity.setOnline(!event.offline)
    }
}
