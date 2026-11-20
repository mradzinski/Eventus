package io.github.mradzinski.eventus.internal

import app.cash.turbine.test
import io.github.mradzinski.eventus.Boom
import io.github.mradzinski.eventus.BoomHandler
import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.Failed
import io.github.mradzinski.eventus.Observe
import io.github.mradzinski.eventus.ObserveHandler
import io.github.mradzinski.eventus.Run
import io.github.mradzinski.eventus.RunHandler
import io.github.mradzinski.eventus.Stopped
import io.github.mradzinski.eventus.StoppedHandler
import io.github.mradzinski.eventus.TestEffect
import io.github.mradzinski.eventus.TestEvent
import io.github.mradzinski.eventus.TestHandler
import io.github.mradzinski.eventus.TestState
import io.github.mradzinski.eventus.TestStore
import io.github.mradzinski.eventus.log
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private typealias TestRouter = DefaultEventRouter<TestEvent, TestState, TestEffect>

private typealias TestErrorHandler = ErrorHandler<TestEvent, TestState, TestEffect>

class DefaultEventRouterTest {

    @Test
    fun `it starts with the initial state`() = runTest {
        val router = router()

        router.state.value shouldBe TestState()
    }

    @Test
    fun `concurrent runs every event at once`() = runTest {
        logAfter(DispatchStrategy.Concurrent, "a", "b") shouldContainExactly
            listOf("start a", "start b", "end a", "end b")
    }

    @Test
    fun `latest cancels the run that is still active`() = runTest {
        logAfter(DispatchStrategy.Latest, "a", "b") shouldContainExactly listOf("start a", "start b", "end b")
    }

    @Test
    fun `sequential runs events one after the other`() = runTest {
        logAfter(DispatchStrategy.Sequential, "a", "b") shouldContainExactly
            listOf("start a", "end a", "start b", "end b")
    }

    @Test
    fun `drop if busy ignores an event while a run is active`() = runTest {
        logAfter(DispatchStrategy.DropIfBusy, "a", "b") shouldContainExactly listOf("start a", "end a")
    }

    @Test
    fun `drop if busy accepts an event again once the run has ended`() = runTest {
        val handler = RunHandler(DispatchStrategy.DropIfBusy)
        val router = router(handler)

        handler.gate.complete(Unit)
        router.onEvent(Run("a"))
        testScheduler.runCurrent()
        router.onEvent(Run("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "end a", "start b", "end b")
    }

    @Test
    fun `sequential keeps the order of events sent before any of them ran`() = runTest {
        val handler = RunHandler(DispatchStrategy.Sequential)
        val router = router(handler)

        handler.gate.complete(Unit)
        listOf("a", "b", "c").forEach { router.onEvent(Run(it)) }
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "end a", "start b", "end b", "start c", "end c")
    }

    @Test
    fun `a sequential run that fails does not stop the ones after it`() = runTest {
        val errors = mutableListOf<Throwable>()
        val router = router(FlakyHandler(), errorHandler = { _, error, _ -> errors += error })

        router.onEvent(Run("bad"))
        router.onEvent(Run("good"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("good")
        errors.map { it.message } shouldContainExactly listOf("bad")
    }

    @Test
    fun `a while started run stops on a screen stopped event that has no handler`() = runTest {
        val router = router(ObserveHandler())

        router.onEvent(Observe("a"))
        testScheduler.runCurrent()
        router.onEvent(Stopped)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "stop a")

        router.onEvent(Observe("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "stop a", "start b")
    }

    @Test
    fun `a screen stopped event with a handler stops the started runs and runs its handler`() = runTest {
        val router = router(ObserveHandler(), StoppedHandler())

        router.onEvent(Observe("a"))
        testScheduler.runCurrent()
        router.onEvent(Stopped)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "stop a", "stopped")
    }

    @Test
    fun `a screen stopped event with no handler and nothing started does nothing`() = runTest {
        val router = router()

        router.onEvent(Stopped)
        testScheduler.runCurrent()

        router.state.value.log.shouldBeEmpty()
    }

    @Test
    fun `a screen stopped event leaves the runs of other strategies alone`() = runTest {
        val handler = RunHandler(DispatchStrategy.Latest)
        val router = router(handler)

        router.onEvent(Run("a"))
        testScheduler.runCurrent()
        router.onEvent(Stopped)
        handler.gate.complete(Unit)
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "end a")
    }

    @Test
    fun `sending a while started event again replaces the run that is active`() = runTest {
        val router = router(ObserveHandler())

        router.onEvent(Observe("a"))
        testScheduler.runCurrent()
        router.onEvent(Observe("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "stop a", "start b")
    }

    @Test
    fun `every handler of a router receives the store the router shows`() = runTest {
        val first = CaptureHandler(Run::class)
        val second = CaptureHandler(Observe::class)
        val router = router(first, second)

        router.onEvent(Run("a"))
        router.onEvent(Observe("b"))
        testScheduler.runCurrent()

        first.store shouldBeSameInstanceAs second.store
        first.store?.state shouldBeSameInstanceAs router.state
    }

    @Test
    fun `a handler's error reaches the error handler with the event and the store and the screen keeps working`() =
        runTest {
            val reported = mutableListOf<TestEvent>()
            val runHandler = RunHandler(DispatchStrategy.Concurrent)
            val router = router(
                BoomHandler(),
                runHandler,
                errorHandler = { event, error, store ->
                    reported += event
                    store.emit(Failed(error.message))
                },
            )

            router.onEvent(Boom)
            testScheduler.runCurrent()

            router.effects.test {
                awaitItem() shouldBe Failed("boom")
            }
            reported shouldContainExactly listOf(Boom)

            runHandler.gate.complete(Unit)
            router.onEvent(Run("a"))
            testScheduler.runCurrent()

            router.state.value.log shouldContainExactly listOf("start a", "end a")
        }

    @Test
    fun `without an error handler a handler's error goes to the exception handler of the scope`() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val runHandler = RunHandler(DispatchStrategy.Concurrent)
        val router = router(
            BoomHandler(),
            runHandler,
            context = CoroutineExceptionHandler { _, error -> uncaught += error },
        )

        router.onEvent(Boom)
        testScheduler.runCurrent()

        uncaught.map { it.message } shouldContainExactly listOf("boom")

        runHandler.gate.complete(Unit)
        router.onEvent(Run("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "end a")
    }

    @Test
    fun `a cancelled run is not reported as an error`() = runTest {
        val errors = mutableListOf<Throwable>()
        val handler = RunHandler(DispatchStrategy.Latest)
        val router = router(handler, ObserveHandler(), errorHandler = { _, error, _ -> errors += error })

        router.onEvent(Run("a"))
        router.onEvent(Observe("a"))
        testScheduler.runCurrent()
        router.onEvent(Run("b"))
        router.onEvent(Stopped)
        handler.gate.complete(Unit)
        testScheduler.runCurrent()

        errors.shouldBeEmpty()
    }

    @Test
    fun `two handlers for one event fail when the router is created`() = runTest {
        val failure = shouldThrow<IllegalStateException> {
            router(RunHandler(DispatchStrategy.Concurrent), RunHandler(DispatchStrategy.Latest))
        }

        failure.message shouldContain "Multiple handlers registered for Run"
    }

    @Test
    fun `an event without a handler fails when it is sent`() = runTest {
        val router = router(RunHandler(DispatchStrategy.Concurrent))
        val failure = shouldThrow<IllegalStateException> { router.onEvent(Boom) }

        failure.message shouldBe "No handler registered for Boom"
    }

    @Test
    fun `a handler registered for a type is not given events of another type`() = runTest {
        val handler = CaptureHandler(Run::class)
        val router = router(handler, ObserveHandler())

        router.onEvent(Observe("a"))
        testScheduler.runCurrent()

        handler.store shouldBe null
    }

    @Test
    fun `nothing runs once the scope is cancelled`() = runTest {
        DispatchStrategy.entries.forEach { strategy ->
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val handler = RunHandler(strategy)
            val router =
                TestRouter(scope = scope, initialState = TestState(), handlers = listOf(handler), errorHandler = null)

            handler.gate.complete(Unit)
            scope.cancel()
            router.onEvent(Run("a"))
            router.onEvent(Run("b"))
            testScheduler.runCurrent()

            router.state.value.log.shouldBeEmpty()
        }
    }

    // The one test that looks inside the router: finished runs must not be kept, or a long-lived screen
    // would hold on to every run it ever started.
    @Test
    fun `a run that has ended is no longer tracked`() = runTest {
        listOf(EmptyCoroutineContext, Dispatchers.Unconfined).forEach { dispatcher ->
            val busy = RunHandler(DispatchStrategy.DropIfBusy)
            val router = router(busy, ObserveHandler(), context = dispatcher)

            router.onEvent(Run("a"))
            router.onEvent(Observe("a"))
            router.onEvent(Observe("b"))
            testScheduler.runCurrent()

            router.trackedRunCount shouldBe 2

            busy.gate.complete(Unit)
            router.onEvent(Stopped)
            testScheduler.runCurrent()

            router.trackedRunCount shouldBe 0
        }
    }

    @Test
    fun `a replaced latest run is forgotten when the newest one ends`() = runTest {
        val handler = RunHandler(DispatchStrategy.Latest)
        val router = router(handler)

        router.onEvent(Run("a"))
        testScheduler.runCurrent()
        router.onEvent(Run("b"))
        testScheduler.runCurrent()

        router.trackedRunCount shouldBe 1

        handler.gate.complete(Unit)
        testScheduler.runCurrent()

        router.trackedRunCount shouldBe 0
    }

    private fun TestScope.router(
        vararg handlers: TestHandler,
        context: CoroutineContext = EmptyCoroutineContext,
        errorHandler: TestErrorHandler? = null,
    ): TestRouter = TestRouter(
        scope = CoroutineScope(backgroundScope.coroutineContext + context),
        initialState = TestState(),
        handlers = handlers.toList(),
        errorHandler = errorHandler,
    )

    /** Sends one `Run` per id to a handler with [strategy], lets them finish and returns what was logged. */
    private fun TestScope.logAfter(strategy: DispatchStrategy, vararg ids: String): List<String> {
        val handler = RunHandler(strategy)
        val router = router(handler)

        ids.forEach { id ->
            router.onEvent(Run(id))
            testScheduler.runCurrent()
        }
        handler.gate.complete(Unit)
        testScheduler.runCurrent()

        return router.state.value.log
    }

    /** Remembers the store it was last called with. */
    private class CaptureHandler<E : TestEvent>(override val eventType: KClass<E>) :
        EventHandler<E, TestState, TestEffect> {

        var store: TestStore? = null

        override suspend fun handle(event: E, store: TestStore) {
            this.store = store
        }
    }

    /** Handles `Run` events in order and fails for the one whose id is "bad". */
    private class FlakyHandler : EventHandler<Run, TestState, TestEffect> {

        override val eventType = Run::class
        override val strategy = DispatchStrategy.Sequential

        override suspend fun handle(event: Run, store: TestStore) {
            check(event.id != "bad") { "bad" }

            store.log(event.id)
        }
    }
}
