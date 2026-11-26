package io.github.mradzinski.eventus.test

import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.test.internal.Recorder
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/**
 * What the block of [runEventHandlerTest] can do and read.
 *
 * Dispatch events with [dispatch], move the test's clock with [advanceBy], and wait for work that runs on a
 * thread of its own with [awaitIdle]. Then read what happened: [state], [states], [effects], [reports]
 * and [failures]. Every read first runs whatever can run without moving the clock, so there is nothing
 * to call between dispatching an event and reading its result.
 *
 * [router] and [testScope] are there for any other testing tool. A flow-testing library takes
 * `router.state` or `router.effects`, and whatever needs the test's clock or scopes takes `testScope`.
 */
public class EventHandlerTest<E : UiEvent, S : UiState, F : UiEffect> internal constructor(
    /** The router the event handlers run in. Its `state` and `effects` are the flows the UI would collect. */
    public val router: EventRouter<E, S, F>,
    /** The scope of the coroutine test this block runs in: its clock and its background scope. */
    public val testScope: TestScope,
    private val recorder: Recorder<E>,
    private val failuresAreHandled: Boolean,
) {

    private val recordedStates = MutableStateFlow(emptyList<S>())
    private var failuresWereRead = false

    init {
        // Unconfined, so each state is recorded where it is written and not when the test next lets go.
        testScope.backgroundScope.launch(Dispatchers.Unconfined) {
            router.state.collect { state -> recordedStates.update { it + state } }
        }
    }

    /** The state now. */
    public val state: S
        get() {
            settle()

            return router.state.value
        }

    /** Every state so far, the initial one first. */
    public val states: List<S>
        get() {
            settle()

            return recordedStates.value
        }

    /**
     * Every effect so far, in the order they were emitted.
     *
     * Reading them takes nothing from the router: whatever collects `router.effects` still gets each one.
     */
    public val effects: List<F>
        get() {
            // An event handler can only emit the effect type of its own store, which is F.
            @Suppress("UNCHECKED_CAST")
            return reports
                .filterIsInstance<RouterTrace.EffectEmitted<E>>()
                .map { it.effect as F }
        }

    /** What the router did with each event, in order. Each report prints as a sentence. */
    public val reports: List<RouterTrace<E>>
        get() {
            settle()

            return recorder.reports
        }

    /**
     * What the event handlers threw, in order.
     *
     * A failure the test never reads fails the test, so an event handler cannot fail unnoticed. That
     * does not apply when the test was given an error handler: the failure went to it, and the test
     * checks what it did.
     */
    public val failures: List<Throwable>
        get() {
            settle()
            failuresWereRead = true

            return recorder.failures
        }

    /**
     * Dispatches each event as the UI would. Everything that can run without moving the clock runs before the
     * next event is dispatched, as it would between two taps.
     */
    public fun dispatch(vararg events: E) {
        events.forEach { event ->
            router.onEvent(event)
            settle()
        }
    }

    /** Moves the test's clock forward by [duration] and runs what became due. */
    public fun advanceBy(duration: Duration) {
        testScope.testScheduler.advanceTimeBy(duration)
        settle()
    }

    /**
     * Waits until every event that was dispatched has been handled to its end.
     *
     * Use it when an event handler, or something it calls, does its work on a thread of its own, which
     * the test's clock does not control. An event handler that waits with `delay` is let through as well.
     *
     * An event handler that never returns has no end to wait for. Wait for the state you expect
     * instead, with `router.state.first { … }`.
     */
    public suspend fun awaitIdle() {
        recorder.awaitIdle()
        settle()
    }

    /** The failures the test still has to account for when it ends. */
    internal fun unreadFailures(): List<Throwable> =
        if (failuresWereRead || failuresAreHandled) emptyList() else recorder.failures

    /** Runs everything that can run without moving the test's clock. */
    private fun settle() = testScope.testScheduler.runCurrent()
}
