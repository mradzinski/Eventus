package io.github.mradzinski.eventus

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * The state and the effects of one screen.
 *
 * An [EventRouter] creates one store, keeps it for as long as the router lives and hands it to every
 * [EventHandler] it runs, so all the handlers of a screen read and write the same state. The UI never
 * receives the store: it reads [EventRouter.state] and [EventRouter.effects].
 *
 * Create one yourself only in a test, to call a handler directly:
 * ```
 * val store = ScreenStore<TaskListUiState, TaskListEffect>(TaskListUiState.Loading)
 *
 * OnRefreshHandler(FakeTaskRepository()).handle(OnRefresh, store)
 * ```
 *
 * Every member is safe to call from any thread.
 */
public class ScreenStore<S : UiState, F : UiEffect> private constructor(
    source: MutableStateFlow<S>,
    shared: Channel<F>?,
    private val onEffect: ((F) -> Unit)?,
    private val onDispatch: ((UiEvent) -> Job)?,
) {

    /**
     * Creates the store of one screen.
     *
     * @param initialState The state the screen starts with.
     */
    public constructor(initialState: S) : this(
        source = MutableStateFlow(initialState),
        shared = null,
        onEffect = null,
        onDispatch = null,
    )

    /** The state of the screen. It always has a value, and a new collector gets the latest one first. */
    public val state: StateFlow<S>
        field = source

    /** The state of the screen right now. */
    public val current: S
        get() = state.value

    /**
     * Holds the effects until a collector takes them. An effect that was handed to a collector which
     * was cancelled before it could receive it comes back through `onUndeliveredElement` and is put
     * in the channel again, so the next collector gets it.
     */
    private val effectChannel: Channel<F> = shared ?: Channel(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { effect -> putBack(effect) },
    )

    /**
     * The effects of the screen, in the order they were emitted.
     *
     * Each effect is delivered once, to one collector. Effects emitted while nothing collects are kept
     * and delivered to the next collector, however many there are. An effect that reaches a collector
     * at the moment that collector is cancelled is kept too, and goes to the next collector after the
     * effects that were already waiting. An effect is only lost when the collector that received it
     * is cancelled while handling it.
     */
    public val effects: Flow<F> = effectChannel.receiveAsFlow()

    /**
     * Replaces the state with the result of [transform], which receives the current state.
     *
     * The replacement is atomic: when two handlers update at once, neither update is lost. For that
     * reason [transform] may run more than once and must only compute the new value.
     *
     * The state must be immutable. A new state that is equal to the current one is not emitted, so a
     * state that was changed in place (an item added to a list it already holds, for example) never
     * reaches the UI. Build a new value instead, with `copy`.
     */
    public fun update(transform: (S) -> S) {
        state.update(transform)
    }

    /**
     * Changes the state at once, runs [work], and puts the state back if the work does not finish.
     *
     * Use it to show the result of an action before the action is done, so the user does not wait:
     * ```
     * store.updateOptimistically(
     *     stateUpdate = { it.copy(favorite = true) },
     *     stateIfFailed = { it.copy(favorite = false) },
     * ) {
     *     profiles.setFavorite(profileId, favorite = true)
     * }
     * ```
     *
     * [stateUpdate] and [stateIfFailed] only change what the screen shows; neither does any work.
     * The repository call, or whatever the action really is, goes in [work] and nowhere else.
     *
     * When [work] throws, the exception goes on to the caller after the state is put back, so a
     * handler that uses this still reports its failure to the screen's [ErrorHandler]. A [work] that is
     * cancelled counts as not finished: the state is put back as well.
     *
     * Use this for state that only this screen holds. If something else replaces the same part of the
     * state while [work] runs, such as a handler that observes a repository, it overwrites the change.
     * For data like that, let its owner (the repository) show the change early and take it back.
     *
     * @param stateUpdate Makes the state the screen shows at once. Runs right away, like [update].
     * @param stateIfFailed Makes the state the screen goes back to. Runs only if [work] throws or is
     * cancelled. It receives the state as it is at that moment, so undo only what [stateUpdate] did:
     * other changes made in the meantime are then kept.
     * @param work The action itself.
     * @return What [work] returns.
     */
    public suspend fun <T> updateOptimistically(
        stateUpdate: (S) -> S,
        stateIfFailed: (S) -> S,
        work: suspend () -> T,
    ): T {
        var finished = false

        update(stateUpdate)

        try {
            val result = work()

            finished = true

            return result
        } finally {
            // Also reached when the work is cancelled, which a `catch` for failures would have to spell out.
            if (!finished) update(stateIfFailed)
        }
    }

    /** Emits [effect] to the UI. It never suspends and never fails. */
    public fun emit(effect: F) {
        onEffect?.invoke(effect)
        effectChannel.trySend(effect)
    }

    /**
     * Dispatches [event] to the router this store belongs to, as the UI does with [EventRouter.onEvent].
     *
     * Use it when handling one event has to start another: an event handler that adds an item and then
     * refreshes the total, or an [ErrorHandler] that signs the user out.
     * ```
     * cart.add(event.item)
     *
     * store.emit(CartEffect.OpenCart)
     * store.dispatch(OnCartRefresh)
     * ```
     *
     * The event is handled as any other: by its own event handler, under its own [DispatchStrategy],
     * and with its own reports. This function does not wait for it. To wait until the event has been
     * handled to its end, join the job: `store.dispatch(OnCartRefresh).join()`.
     *
     * The event must be one of the screen this store belongs to. An event the router has no handler
     * for throws, here, as it does from [EventRouter.onEvent]. So does a store that was created with
     * the constructor, which belongs to no router.
     *
     * @return The job that handles [event]. It has already completed when the strategy of the event
     * ignored it. It completes when the event has been handled, has failed or was cancelled.
     */
    public fun dispatch(event: UiEvent): Job {
        val onDispatch = checkNotNull(onDispatch) { "This store belongs to no router, so it cannot dispatch $event." }

        return onDispatch(event)
    }

    /**
     * This store as the event handlers of a router see it: the same state and the same effects, with
     * [onDispatch] behind [dispatch]. When [onEffect] is given it is told about each effect that is
     * emitted through the result, before the effect is sent. A router uses it to report which event an
     * effect came from, which the store by itself cannot know.
     */
    internal fun routedBy(onDispatch: (UiEvent) -> Job, onEffect: ((F) -> Unit)? = null): ScreenStore<S, F> =
        ScreenStore(
            source = state,
            shared = effectChannel,
            onEffect = onEffect,
            onDispatch = onDispatch,
        )

    private fun putBack(effect: F) {
        effectChannel.trySend(effect)
    }
}
