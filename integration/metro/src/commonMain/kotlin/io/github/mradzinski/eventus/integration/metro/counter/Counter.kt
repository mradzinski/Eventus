package io.github.mradzinski.eventus.integration.metro.counter

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.metro.ContributesEventHandlers
import io.github.mradzinski.eventus.routerScope
import kotlinx.coroutines.CoroutineScope

internal data class CounterState(
    val title: String = "",
    val count: Int = 0,
    val failure: String? = null,
) : UiState

internal sealed interface CounterEffect : UiEffect

internal sealed interface CounterEvent : UiEvent {

    data object OnOpened : CounterEvent

    data object OnPlusClick : CounterEvent

    data object OnBreakClick : CounterEvent
}

internal typealias CounterStore = ScreenStore<CounterState, CounterEffect>

/** The one line a screen writes for Metro: the processor contributes every handler below to `AppScope`. */
@ContributesEventHandlers(AppScope::class)
internal interface CounterHandler<E : CounterEvent> : EventHandler<E, CounterState, CounterEffect>

/** Takes a dependency that needs its qualifier to be found. */
internal class OnOpenedHandler(@Named("greeting") private val greeting: String) :
    CounterHandler<CounterEvent.OnOpened> {

    override val eventType = CounterEvent.OnOpened::class

    override suspend fun handle(event: CounterEvent.OnOpened, store: CounterStore) {
        store.update { it.copy(title = greeting) }
    }
}

/** An object, which the generated provider hands over as it is. */
internal object OnPlusClickHandler : CounterHandler<CounterEvent.OnPlusClick> {

    override val eventType = CounterEvent.OnPlusClick::class

    override suspend fun handle(event: CounterEvent.OnPlusClick, store: CounterStore) {
        store.update { it.copy(count = it.count + 1) }
    }
}

internal class OnBreakClickHandler : CounterHandler<CounterEvent.OnBreakClick> {

    override val eventType = CounterEvent.OnBreakClick::class

    override suspend fun handle(event: CounterEvent.OnBreakClick, store: CounterStore): Unit = error("broken")
}

/** Not generated: an error handler is one class, and Metro's own `@Inject` is all it needs. */
@Inject
internal class CounterErrorHandler : ErrorHandler<CounterEvent, CounterState, CounterEffect> {

    override suspend fun handle(
        event: CounterEvent,
        error: Throwable,
        store: CounterStore,
    ) {
        store.update { it.copy(failure = error.message) }
    }
}

/** What a view model asks Metro for: the handlers of its own screen as a set, and its error handler. */
@Inject
internal class CounterScreen(val handlers: Set<CounterHandler<*>>, val errorHandler: CounterErrorHandler)

/**
 * A view model in the shape the documentation shows: Metro supplies the handlers and the error handler,
 * and the scope keeps its default value, which Metro uses because nothing in the graph provides a scope.
 */
@Inject
internal class CounterViewModel(
    handlers: Set<CounterHandler<*>>,
    errorHandler: CounterErrorHandler,
    scope: CoroutineScope = routerScope(),
) : EventRouter<CounterEvent, CounterState, CounterEffect> by EventRouter(
    scope = scope,
    initialState = CounterState(),
    handlers = handlers,
    errorHandler = errorHandler,
)
