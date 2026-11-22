package io.github.mradzinski.eventus

import kotlin.reflect.KClass

/**
 * Handles one event type of one screen.
 *
 * A handler holds the logic for its event: it calls the app's repositories or use cases, updates the
 * state and emits effects. It keeps nothing about the screen itself. The state and the effects come in
 * with each call, as the [ScreenStore] of the screen, so a handler's constructor takes only what the
 * handler really depends on.
 *
 * ```
 * class OnRefreshHandler(
 *     private val tasks: TaskRepository,
 * ) : EventHandler<OnRefresh, TaskListUiState, TaskListEffect> {
 *
 *     override val eventType = OnRefresh::class
 *     override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnRefresh>()
 *
 *     override suspend fun handle(event: OnRefresh, store: ScreenStore<TaskListUiState, TaskListEffect>) {
 *         tasks.refresh()
 *         store.emit(TaskListEffect.Refreshed)
 *     }
 * }
 * ```
 *
 * To report a failure, let it throw: the [EventRouter] passes it to the screen's [ErrorHandler].
 */
public interface EventHandler<E : UiEvent, S : UiState, F : UiEffect> {

    /**
     * The event this handler handles. The router matches it against the exact class of each event, so
     * a handler for a parent type does not receive the events of its subtypes.
     */
    public val eventType: KClass<E>

    /**
     * What the router does with an event of this handler while other events are running or waiting.
     * The default runs every event at once.
     */
    public val strategy: DispatchStrategy<E>
        get() = DispatchStrategy.Concurrent

    /**
     * Handles [event]. It runs in the router's coroutine scope and is cancelled with it.
     *
     * @param store The state and effects of the screen the event came from.
     */
    public suspend fun handle(event: E, store: ScreenStore<S, F>)
}
