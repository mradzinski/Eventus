package io.github.mradzinski.eventus

import io.github.mradzinski.eventus.internal.DefaultEventRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a screen shows to its UI: the state to draw, the effects to perform and one way in for events.
 *
 * A view model implements it by delegating to the router that the [EventRouter] function creates, so
 * it extends no class of this library:
 * ```
 * class TaskListViewModel(
 *     scope: CoroutineScope = routerScope(),
 *     handlers: Collection<EventHandler<*, TaskListUiState, TaskListEffect>>,
 * ) : ViewModel(scope),
 *     EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
 *         scope = scope,
 *         initialState = TaskListUiState.Loading,
 *         handlers = handlers,
 *     )
 * ```
 */
public interface EventRouter<E : UiEvent, S : UiState, F : UiEffect> {

    /** The state of the screen. It always has a value, and a new collector gets the latest one first. */
    public val state: StateFlow<S>

    /**
     * The effects of the screen. Each one is delivered once, to one collector. Effects emitted while
     * nothing collects are kept for the next collector.
     */
    public val effects: Flow<F>

    /**
     * Sends [event] to the handler registered for its exact class, which runs it as its
     * [DispatchStrategy] says. It returns at once and is safe to call from any thread.
     *
     * @throws IllegalStateException when no handler is registered for the event.
     */
    public fun onEvent(event: E)
}

/**
 * Creates the router of one screen.
 *
 * The router creates the screen's [ScreenStore] from [initialState] and passes it to every handler it
 * runs. Handlers run in child coroutines of [scope] and are cancelled with it: the router lives exactly
 * as long as [scope] does and has nothing to close. One handler failing does not cancel the others.
 *
 * What the router remembers while it runs depends on the strategy of each handler and is stated on
 * each [DispatchStrategy] entry. In short: nothing for `Concurrent`; the active run, until it ends,
 * for `Latest` and `DropIfBusy`; one queue and one worker per event type, until [scope]
 * ends, for `Sequential`.
 *
 * @param scope Where the handlers run. A view model passes the scope it is closed with, for example
 * the one from [routerScope]. The router keeps a job in this scope until the scope is cancelled, so
 * pass a scope that gets cancelled, not one that something waits for: in a test, `backgroundScope`.
 * @param initialState The state the screen starts with.
 * @param handlers The handlers of the screen, one for each event type.
 * @param errorHandler Receives every failure of a handler, with the event it was handling and the
 * screen's store, so it can emit an effect or update the state. Cancellation is never reported. When
 * this is null, a handler's exception is not caught: it reaches the `CoroutineExceptionHandler` of
 * [scope] or, without one, the platform's handler for uncaught exceptions.
 * @throws IllegalStateException when two handlers are registered for the same event type.
 */
public fun <E : UiEvent, S : UiState, F : UiEffect> EventRouter(
    scope: CoroutineScope,
    initialState: S,
    handlers: Collection<EventHandler<*, S, F>>,
    errorHandler: ErrorHandler<E, S, F>? = null,
): EventRouter<E, S, F> = DefaultEventRouter(
    scope = scope,
    initialState = initialState,
    handlers = handlers,
    errorHandler = errorHandler,
)
