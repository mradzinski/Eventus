package io.github.mradzinski.eventus.sample.app

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import io.github.mradzinski.eventus.sample.ui.RouterLog
import kotlinx.coroutines.CoroutineScope

/**
 * No error handler: none of the bar's handlers can fail, and if one ever does it should not be hidden.
 *
 * It sends [AppEvent.OnAppStarted] as soon as it is created. The work that event starts lasts as long as
 * the view model does.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
internal class AppViewModel(
    handlers: Set<AppHandler<*>>,
    logger: Logger,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<AppEvent, AppUiState, AppEffect> by EventRouter(
        scope = scope,
        initialState = AppUiState(),
        handlers = handlers,
        observer = RouterLog(logger, tag = "App"),
    ) {

    init {
        onEvent(AppEvent.OnAppStarted)
    }
}
