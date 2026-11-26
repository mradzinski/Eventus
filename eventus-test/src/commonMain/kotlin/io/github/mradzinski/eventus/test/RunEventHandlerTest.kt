package io.github.mradzinski.eventus.test

import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.test.internal.Recorder
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tests one event handler the way the app runs it: through a router, with its dispatch strategy.
 *
 * The function creates the router and everything a test needs to follow it, and runs [test] inside a
 * coroutine test. In the block, dispatch events and read what happened; see [EventHandlerTest].
 *
 * ```
 * @Test
 * fun `a refresh shows and hides the spinner`() = runEventHandlerTest(
 *     handler = OnRefreshHandler(tasks),
 *     initialState = TaskListUiState(),
 * ) {
 *     dispatch(OnRefresh)
 *
 *     assertEquals(listOf(false, true, false), states.map { it.refreshing })
 * }
 * ```
 *
 * Return the result from the test function, as with `runTest`.
 *
 * @param handler the event handler to test.
 * @param initialState the state the screen starts with.
 * @param errorHandler the screen's error handler, to test what it does with a failure. Without one, a
 * failure is kept in [EventHandlerTest.failures], and one the test never reads fails the test.
 * @param timeout how long the test may take in real time, not on the test's clock. A test that runs
 * out fails with a message that names the events still being handled.
 * @param test the test itself.
 */
public fun <E : UiEvent, S : UiState, F : UiEffect> runEventHandlerTest(
    handler: EventHandler<E, S, F>,
    initialState: S,
    errorHandler: ErrorHandler<in E, S, F>? = null,
    timeout: Duration = DEFAULT_TIMEOUT,
    test: suspend EventHandlerTest<E, S, F>.() -> Unit,
): TestResult = runEventHandlerTest(
    handlers = listOf(handler),
    initialState = initialState,
    errorHandler = errorHandler,
    timeout = timeout,
    test = test,
)

/**
 * Tests several event handlers in one router, such as those that share a dispatch strategy, or every
 * event handler of a screen.
 *
 * It is [runEventHandlerTest] for more than one event handler: [EventHandlerTest.dispatch] accepts the
 * events of all of them.
 *
 * ```
 * @Test
 * fun `delete is ignored while a save runs`() = runEventHandlerTest(
 *     handlers = listOf(OnSaveClickHandler(tasks), OnDeleteClickHandler(tasks)),
 *     initialState = EditorUiState(),
 * ) {
 *     dispatch(OnSaveClick, OnDeleteClick)
 *
 *     assertEquals("#2 OnDeleteClick is ignored", reports.last().toString())
 * }
 * ```
 *
 * @param handlers the event handlers to test, at most one for each event.
 * @param initialState the state the screen starts with.
 * @param errorHandler the screen's error handler, to test what it does with a failure. Without one, a
 * failure is kept in [EventHandlerTest.failures], and one the test never reads fails the test.
 * @param timeout how long the test may take in real time, not on the test's clock. A test that runs
 * out fails with a message that names the events still being handled.
 * @param test the test itself.
 */
public fun <E : UiEvent, S : UiState, F : UiEffect> runEventHandlerTest(
    handlers: Collection<EventHandler<out E, S, F>>,
    initialState: S,
    errorHandler: ErrorHandler<in E, S, F>? = null,
    timeout: Duration = DEFAULT_TIMEOUT,
    test: suspend EventHandlerTest<E, S, F>.() -> Unit,
): TestResult = runTest(timeout = timeout + BACKSTOP_MARGIN) {
    eventHandlerTest(
        handlers = handlers,
        initialState = initialState,
        errorHandler = errorHandler,
        timeout = timeout,
        test = test,
    )
}

/**
 * The whole test, inside a coroutine test that is already running. It is apart from the public
 * functions so that this module's own tests can call it and catch what it throws.
 */
internal suspend fun <E : UiEvent, S : UiState, F : UiEffect> TestScope.eventHandlerTest(
    handlers: Collection<EventHandler<out E, S, F>>,
    initialState: S,
    errorHandler: ErrorHandler<in E, S, F>? = null,
    timeout: Duration = DEFAULT_TIMEOUT,
    test: suspend EventHandlerTest<E, S, F>.() -> Unit,
) {
    val recorder = Recorder<E>()

    val router = EventRouter<E, S, F>(
        scope = backgroundScope,
        initialState = initialState,
        handlers = handlers,
        errorHandler = { event, error, store ->
            recorder.failed(error)
            errorHandler?.handle(event, error, store)
        },
        observer = recorder,
    )

    val scope = EventHandlerTest(
        router = router,
        testScope = this,
        recorder = recorder,
        failuresAreHandled = errorHandler != null,
    )

    val finished = try {
        runWithin(timeout) { scope.test() }
    } catch (failure: Throwable) {
        // The test failed by itself. A failure of an event handler it had not read yet may be the reason.
        throw failure.carrying(scope.unreadFailures())
    }

    if (finished) testScheduler.runCurrent()

    val problem = if (finished) {
        firstCarryingTheRest(scope.unreadFailures())
    } else {
        AssertionError(timeoutMessage(timeout, recorder.stillActive)).carrying(scope.unreadFailures())
    }

    if (problem != null) throw problem
}

/** The first of [failures] with the others added to it, or null when there is none. */
private fun firstCarryingTheRest(failures: List<Throwable>): Throwable? = failures
    .firstOrNull()
    ?.carrying(failures.drop(1))

/** Adds [others] to this failure, so that whoever reads it sees them too. */
private fun Throwable.carrying(others: List<Throwable>): Throwable {
    others.forEach { addSuppressed(it) }

    return this
}

/**
 * Runs [block] on the test's own dispatcher and stops it after [timeout] of real time.
 *
 * The limit has to be counted on another dispatcher, because on the test's dispatcher time is the test's
 * clock, which jumps ahead whenever the test waits.
 *
 * @return false when the time ran out.
 */
private suspend fun TestScope.runWithin(timeout: Duration, block: suspend () -> Unit): Boolean {
    val testDispatcher =
        checkNotNull(coroutineContext[ContinuationInterceptor]) { "A coroutine test has a dispatcher." }

    val result = withContext(Dispatchers.Default) {
        withTimeoutOrNull(timeout) {
            withContext(testDispatcher) { block() }
        }
    }

    return result != null
}

private fun timeoutMessage(timeout: Duration, stillActive: List<String>): String {
    if (stillActive.isEmpty()) return "The test did not finish within $timeout. No event was still being handled."

    return "The test did not finish within $timeout. Still being handled: ${stillActive.joinToString()}. " +
        "An event handler that never returns has no end to wait for: wait for the state you expect with " +
        "router.state.first { … }, not with awaitIdle()."
}

/** Long enough for work on another thread on a slow machine, short enough to notice a test that hangs. */
private val DEFAULT_TIMEOUT = 10.seconds

/**
 * Added to the limit given to `runTest`, whose own message names no event. The limit above runs out
 * first; this one is only reached by a test that blocks its thread.
 */
private val BACKSTOP_MARGIN = 5.seconds
