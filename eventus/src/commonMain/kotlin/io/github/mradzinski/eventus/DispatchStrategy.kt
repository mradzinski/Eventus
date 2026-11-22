package io.github.mradzinski.eventus

/**
 * Decides what an [EventRouter] does with an event while other events are running or waiting.
 *
 * A ready-made strategy names, in its brackets, the events it looks at. An event handler usually names
 * its own event:
 *
 * ```
 * // A second tap on Save is ignored while a save is running.
 * override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnSaveClick>()
 * ```
 *
 * Naming a type that several events share makes the strategy look at all of them. Adding a key makes
 * it look only at the events with the same key:
 *
 * ```
 * // A change of the query or of the filter cancels the load of the other.
 * override val strategy = DispatchStrategy.Latest<OnCriteriaChange>()
 *
 * // The changes to one task run in order, and the tasks do not wait for each other.
 * override val strategy = DispatchStrategy.Sequential<OnTaskDoneChange> { it.taskId }
 * ```
 *
 * A strategy made for a type can only be used by the handler of an event of that type. Event handlers
 * that must agree share one value.
 *
 * For anything else, write the function:
 *
 * ```
 * // A refresh cancels "load more", and a second refresh is ignored.
 * override val strategy = DispatchStrategy<OnRefresh> { _, active ->
 *     if (active.any { it is OnRefresh }) {
 *         Dispatch.Ignore
 *     } else {
 *         Dispatch.Replace(active.filterIsInstance<OnLoadMore>())
 *     }
 * }
 * ```
 *
 * A strategy says nothing about how long a run lasts. A handler that does not return by itself, such as
 * one that collects a flow, is active until the router's scope is cancelled, unless a strategy replaces
 * it first. The router knows nothing about a user interface: nothing starts or stops a run because a
 * screen is shown or hidden.
 */
public fun interface DispatchStrategy<in E : UiEvent> {

    /**
     * Decides what to do with [event].
     *
     * The router calls this from `onEvent`, on the thread that sent the event, and calls it again if
     * the active events changed in the meantime. So it may run more than once and must only compute
     * its answer.
     *
     * If it throws, the event does not run and the failure is not passed to the screen's
     * [ErrorHandler]: it is a mistake in the code, so it fails in the router's scope as an uncaught
     * error.
     *
     * @param active The events of the screen that are running or waiting to run, oldest first, of
     * every event type.
     */
    public fun decide(event: E, active: List<UiEvent>): Dispatch

    /** The ready-made strategies. */
    public companion object {

        /** Every event runs at once, in a coroutine of its own, whatever else is active. The default. */
        public val Concurrent: DispatchStrategy<UiEvent> = DispatchStrategy { _, _ -> Dispatch.Run }

        /**
         * A new event cancels every active event that is a [T] and then runs. For input that makes the
         * previous work pointless, such as a search box or a slider.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> Latest(): DispatchStrategy<T> = latest(matcher<T>())

        /**
         * A new event cancels the active events that are a [T] and have the same [key], such as the id
         * of an item, and then runs.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> Latest(noinline key: (T) -> Any?): DispatchStrategy<T> =
            latest(matcher(key))

        /**
         * The events that are a [T] run one after the other, in the order they were sent. For changes
         * that must be applied in order, such as a counter tapped several times. A run that fails does
         * not stop the ones after it.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> Sequential(): DispatchStrategy<T> = sequential(matcher<T>())

        /**
         * The events that are a [T] and have the same [key], such as the id of an item, run one after
         * the other. Events with different keys do not wait for each other.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> Sequential(noinline key: (T) -> Any?): DispatchStrategy<T> =
            sequential(matcher(key))

        /**
         * An event is ignored while any event that is a [T] is running or waiting. For actions that
         * must not run twice, such as a submit button tapped again.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> DropIfAlreadyRunning(): DispatchStrategy<T> =
            dropIfAlreadyRunning(matcher<T>())

        /**
         * An event is ignored while an event that is a [T] and has the same [key], such as the id of an
         * item, is running or waiting.
         */
        @Suppress("FunctionName") // Reads as the name of the strategy.
        public inline fun <reified T : UiEvent> DropIfAlreadyRunning(
            noinline key: (T) -> Any?,
        ): DispatchStrategy<T> = dropIfAlreadyRunning(matcher(key))

        @PublishedApi
        internal fun <T : UiEvent> latest(matches: (event: T, other: UiEvent) -> Boolean): DispatchStrategy<T> =
            DispatchStrategy { event, active -> Dispatch.Replace(active.filter { matches(event, it) }) }

        @PublishedApi
        internal fun <T : UiEvent> sequential(matches: (event: T, other: UiEvent) -> Boolean): DispatchStrategy<T> =
            DispatchStrategy { event, active -> Dispatch.WaitFor(active.filter { matches(event, it) }) }

        @PublishedApi
        internal fun <T : UiEvent> dropIfAlreadyRunning(
            matches: (event: T, other: UiEvent) -> Boolean,
        ): DispatchStrategy<T> = DispatchStrategy { event, active ->
            if (active.any { matches(event, it) }) {
                Dispatch.Ignore
            } else {
                Dispatch.Run
            }
        }

        /** Matches every event that is a [T]. */
        @PublishedApi
        internal inline fun <reified T : UiEvent> matcher(): (event: T, other: UiEvent) -> Boolean =
            { _, other -> other is T }

        /** Matches the events that are a [T] and have the same [key] as the new one. */
        @PublishedApi
        internal inline fun <reified T : UiEvent> matcher(
            noinline key: (T) -> Any?,
        ): (event: T, other: UiEvent) -> Boolean = { event, other -> other is T && key(other) == key(event) }
    }
}
