package io.github.mradzinski.eventus.sample.presentation.app

import androidx.lifecycle.ViewModel
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import kotlinx.coroutines.CoroutineScope

/**
 * No error handler: none of the bar's handlers can fail, and if one ever does it should not be hidden.
 *
 * It sends [AppEvent.OnAppStarted] as soon as it is created. The work that event starts lasts as long as
 * the view model does.
 */
internal class AppViewModel(handlers: Collection<AppHandler<*>>, scope: CoroutineScope = routerScope()) :
    ViewModel(scope),
    EventRouter<AppEvent, AppUiState, AppEffect> by EventRouter(
        scope = scope,
        initialState = AppUiState(),
        handlers = handlers,
    ) {

    init {
        onEvent(AppEvent.OnAppStarted)
    }
}
