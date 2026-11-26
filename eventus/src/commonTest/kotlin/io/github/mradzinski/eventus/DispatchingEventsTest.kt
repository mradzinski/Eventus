package io.github.mradzinski.eventus

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest

/** Events that an event handler or an error handler dispatches with `ScreenStore.dispatch`. */
class DispatchingEventsTest {

    @Test
    fun `an event dispatched by an event handler is handled as an event of its own`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(RunThen { dispatch(Tick) }, TickHandler()),
        )

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "end a", "tick")
    }

    @Test
    fun `joining the job waits until the dispatched event has been handled`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(RunThen { dispatch(Tick).join() }, TickHandler()),
        )

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "tick", "end a")
    }

    @Test
    fun `a dispatched event follows its own strategy and an ignored one returns a job that has ended`() = runTest {
        val tick = TickHandler(strategy = DispatchStrategy.DropIfAlreadyRunning<Tick>(), gate = CompletableDeferred())

        val run = RunThen {
            val first = dispatch(Tick)
            val second = dispatch(Tick)

            log("first ended ${first.isCompleted}, second ended ${second.isCompleted}")
        }

        val router = router(scope = backgroundScope, handlers = listOf(run, tick))

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "first ended false, second ended true", "end a")
    }

    @Test
    fun `an error handler dispatches an event`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(BoomHandler(), TickHandler()),
            errorHandler = { _, _, store -> store.dispatch(Tick) },
        )

        router.onEvent(Boom)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("tick")
    }

    @Test
    fun `an error handler waits for the event it dispatched`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(BoomHandler(), TickHandler()),
            errorHandler = { _, _, store ->
                store.dispatch(Tick).join()
                store.log("handled")
            },
        )

        router.onEvent(Boom)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("tick", "handled")
    }

    @Test
    fun `an error handler that is waiting is cancelled when its event is replaced`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(BoomHandler(strategy = DispatchStrategy.Latest<Boom>())),
            errorHandler = { _, _, store ->
                store.log("handling")

                try {
                    awaitCancellation()
                } finally {
                    store.log("stopped")
                }
            },
        )

        router.onEvent(Boom)
        testScheduler.runCurrent()
        router.onEvent(Boom)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("handling", "stopped", "handling")
    }

    @Test
    fun `an error handler that throws fails in the scope and the screen keeps working`() = runTest {
        val uncaught = mutableListOf<Throwable>()

        val router = router(
            scope = backgroundScope + CoroutineExceptionHandler { _, error -> uncaught += error },
            handlers = listOf(BoomHandler(), TickHandler()),
            errorHandler = { _, _, _ -> error("bad error handler") },
        )

        router.onEvent(Boom)
        testScheduler.runCurrent()
        router.onEvent(Tick)
        testScheduler.runCurrent()

        uncaught.map { it.message } shouldContainExactly listOf("The error handler failed for: Boom")
        uncaught.map { it.cause?.message } shouldContainExactly listOf("bad error handler")
        router.state.value.log shouldContainExactly listOf("tick")
    }

    @Test
    fun `dispatching an event that has no handler fails the event that dispatched it`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(RunThen { dispatch(Tick) }),
            errorHandler = { event, error, store -> store.log("$event failed: ${error.message}") },
        )

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf(
            "start a",
            "Run(id=a) failed: No handler registered for Tick",
        )
    }

    @Test
    fun `dispatching an event of another screen fails the event that dispatched it`() = runTest {
        val router = router(
            scope = backgroundScope,
            handlers = listOf(RunThen { dispatch(ElsewhereEvent) }),
            errorHandler = { _, error, store -> store.log("failed: ${error.message}") },
        )

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly
            listOf("start a", "failed: No handler registered for ElsewhereEvent")
    }

    @Test
    fun `a dispatched event has reports of its own`() = runTest {
        val reports = mutableListOf<RouterTrace<TestEvent>>()

        val router = router(
            scope = backgroundScope,
            handlers = listOf(RunThen { dispatch(Tick) }, TickHandler()),
            observer = { reports += it },
        )

        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        val dispatched = reports
            .filterIsInstance<RouterTrace.Dispatched<TestEvent>>()
            .map { it.number to it.event }

        dispatched shouldContainExactly listOf(1L to Run("a"), 2L to Tick)
    }

    @Test
    fun `a dispatched event still runs when the event that dispatched it is replaced`() = runTest {
        val run = RunThen(strategy = DispatchStrategy.Latest<Run>()) {
            dispatch(Tick)
            CompletableDeferred<Unit>().await()
        }

        val router = router(scope = backgroundScope, handlers = listOf(run, TickHandler()))

        router.onEvent(Run("a"))
        testScheduler.runCurrent()
        router.onEvent(Run("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "tick", "start b", "tick")
    }

    @Test
    fun `a store that was created by hand cannot dispatch`() {
        val store = TestStore(TestState())
        val error = shouldThrow<IllegalStateException> { store.dispatch(Tick) }

        error.message shouldContain "belongs to no router"
    }

    private fun router(
        scope: CoroutineScope,
        handlers: Collection<TestHandler>,
        errorHandler: ErrorHandler<TestEvent, TestState, TestEffect>? = null,
        observer: RouterObserver<TestEvent>? = null,
    ): EventRouter<TestEvent, TestState, TestEffect> = EventRouter(
        scope = scope,
        initialState = TestState(),
        handlers = handlers,
        errorHandler = errorHandler,
        observer = observer,
    )

    private data object ElsewhereEvent : UiEvent

    /** Logs both ends of its run and does [then] in between, with the store it was given. */
    private class RunThen(
        override val strategy: DispatchStrategy<Run> = DispatchStrategy.Concurrent,
        private val then: suspend TestStore.() -> Unit,
    ) : EventHandler<Run, TestState, TestEffect> {

        override val eventType = Run::class

        override suspend fun handle(event: Run, store: TestStore) {
            store.log("start ${event.id}")
            store.then()
            store.log("end ${event.id}")
        }
    }

    /** Logs a tick, after [gate] when one is given. */
    private class TickHandler(
        override val strategy: DispatchStrategy<Tick> = DispatchStrategy.Concurrent,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : EventHandler<Tick, TestState, TestEffect> {

        override val eventType = Tick::class

        override suspend fun handle(event: Tick, store: TestStore) {
            gate?.await()
            store.log("tick")
        }
    }
}
