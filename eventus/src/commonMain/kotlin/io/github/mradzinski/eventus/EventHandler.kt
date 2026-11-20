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
 *     override val strategy = DispatchStrategy.DropIfBusy
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

    /** How the router runs this handler when its event arrives again while a previous run is active. */
    public val strategy: DispatchStrategy
        get() = DispatchStrategy.Concurrent

    /**
     * Handles [event]. It runs in the router's coroutine scope and is cancelled with it.
     *
     * @param store The state and effects of the screen the event came from.
     */
    public suspend fun handle(event: E, store: ScreenStore<S, F>)
}

/**
 * What an [EventRouter] does when an event arrives while the handler of that event type is still
 * running a previous one.
 *
 * Each strategy applies per event type: two different events never wait for, cancel or drop each
 * other. What the router remembers for each strategy, and for how long, is stated on each entry.
 */
public enum class DispatchStrategy {

    /**
     * Every event runs at once, in a coroutine of its own. The default.
     *
     * The router keeps nothing: a run is forgotten as soon as it is started and ends by itself.
     */
    Concurrent,

    /**
     * A new event cancels the run that is still active and starts over. For input that makes the
     * previous work pointless, such as a search box or a slider.
     *
     * The router keeps the active run of the event type, to be able to cancel it, and forgets it the
     * moment that run ends.
     */
    Latest,

    /**
     * Events run one after the other, in the order they were sent. For changes that must be applied in
     * order, such as a toggle tapped several times.
     *
     * The first event of a type starts a queue and one worker coroutine for that type. Both then live
     * for as long as the router's scope does, also while the queue is empty, so the router holds one
     * idle coroutine for every event type that was sent at least once with this strategy. A run that
     * fails does not stop the queue.
     */
    Sequential,

    /**
     * An event that arrives while a run is active is ignored. For actions that must not run twice,
     * such as a submit button tapped again.
     *
     * The router keeps the active run of the event type, to know that it is busy, and forgets it the
     * moment that run ends.
     */
    DropIfBusy,

    /**
     * The run lasts for as long as the screen is visible: it is cancelled when an event that implements
     * [ScreenStoppedEvent] is sent. For observing data while the screen is on. Sending the event again
     * replaces the run that is active.
     *
     * The router keeps the active run of the event type until it ends, is replaced or is cancelled by
     * a [ScreenStoppedEvent].
     */
    WhileStarted,
}
