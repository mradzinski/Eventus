package io.github.mradzinski.eventus

/**
 * Decides what a screen does when one of its event handlers fails.
 *
 * Event handlers do not catch their own failures: they throw, and the [EventRouter] passes each
 * failure here. A screen therefore has one place that turns an exception into something its UI can
 * show, and one place to log it.
 *
 * ```
 * class TaskListErrorHandler(
 *     private val logger: Logger,
 * ) : ErrorHandler<TaskListEvent, TaskListUiState, TaskListEffect> {
 *
 *     override suspend fun handle(
 *         event: TaskListEvent,
 *         error: Throwable,
 *         store: ScreenStore<TaskListUiState, TaskListEffect>,
 *     ) {
 *         logger.warn("$event failed", error)
 *
 *         store.emit(TaskListEffect.ShowFailure(error.toFailureReason()))
 *     }
 * }
 * ```
 *
 * Like an event handler, it holds nothing about the screen: the state and the effects come in with
 * each call. Give it to the screen's router through the `errorHandler` parameter of the [EventRouter]
 * function.
 */
public fun interface ErrorHandler<E : UiEvent, S : UiState, F : UiEffect> {

    /**
     * Called when the handler of [event] has thrown [error]. Cancellation is never passed here.
     *
     * Emit an effect to tell the user once, or update the state when the failure must stay on screen.
     * It can also start another event with [ScreenStore.dispatch], and wait for it by joining the job.
     *
     * It runs in the coroutine of the event that failed, on the thread its handler ran on. That event
     * stays active until this function returns, so its dispatch strategy still counts it, and it is
     * cancelled with that event: when another event replaces it, or when the scope of the router is
     * cancelled. It must not throw: a failure here is a mistake in the code and fails in the scope of
     * the router as an uncaught error.
     *
     * @param store The state and effects of the screen the event came from.
     */
    public suspend fun handle(
        event: E,
        error: Throwable,
        store: ScreenStore<S, F>,
    )
}
