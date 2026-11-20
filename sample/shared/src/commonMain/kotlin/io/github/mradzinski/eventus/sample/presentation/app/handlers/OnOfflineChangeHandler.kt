package io.github.mradzinski.eventus.sample.presentation.app.handlers

import io.github.mradzinski.eventus.sample.data.Connectivity
import io.github.mradzinski.eventus.sample.presentation.app.AppEvent.OnOfflineChange
import io.github.mradzinski.eventus.sample.presentation.app.AppHandler
import io.github.mradzinski.eventus.sample.presentation.app.AppStore

/** The switch does not touch the state: `OnAppStartedHandler` observes the setting and shows it. */
internal class OnOfflineChangeHandler(private val connectivity: Connectivity) : AppHandler<OnOfflineChange> {

    override val eventType = OnOfflineChange::class

    override suspend fun handle(event: OnOfflineChange, store: AppStore) {
        connectivity.setOnline(!event.offline)
    }
}
