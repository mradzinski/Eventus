package io.github.mradzinski.eventus.internal

import app.cash.turbine.test
import io.github.mradzinski.eventus.Boom
import io.github.mradzinski.eventus.BoomHandler
import io.github.mradzinski.eventus.Dispatch
import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.Failed
import io.github.mradzinski.eventus.Gates
import io.github.mradzinski.eventus.Minus
import io.github.mradzinski.eventus.Observe
import io.github.mradzinski.eventus.ObserveHandler
import io.github.mradzinski.eventus.Plus
import io.github.mradzinski.eventus.Run
import io.github.mradzinski.eventus.RunHandler
import io.github.mradzinski.eventus.Step
import io.github.mradzinski.eventus.StepHandler
import io.github.mradzinski.eventus.TestEffect
import io.github.mradzinski.eventus.TestEvent
import io.github.mradzinski.eventus.TestHandler
import io.github.mradzinski.eventus.TestState
import io.github.mradzinski.eventus.TestStore
import io.github.mradzinski.eventus.Tick
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.VisibilityChanged
import io.github.mradzinski.eventus.VisibilityHandler
import io.github.mradzinski.eventus.log
import io.github.mradzinski.eventus.readyMadeStrategies
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
        logAfter(DispatchStrategy.Latest<Run>(), "a", "b") shouldContainExactly listOf("start a", "start b", "end b")
    }

    @Test
    fun `sequential runs events one after the other`() = runTest {
        logAfter(DispatchStrategy.Sequential<Run>(), "a", "b") shouldContainExactly
            listOf("start a", "end a", "start b", "end b")
    }

    @Test
    fun `drop if already running ignores an event while a run is active`() = runTest {
        logAfter(DispatchStrategy.DropIfAlreadyRunning<Run>(), "a", "b") shouldContainExactly listOf("start a", "end a")
    }

    @Test
    fun `drop if already running accepts an event again once the run has ended`() = runTest {
        val handler = RunHandler(DispatchStrategy.DropIfAlreadyRunning<Run>())
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
        val handler = RunHandler(DispatchStrategy.Sequential<Run>())
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
    fun `a run that never returns lasts until the scope is cancelled`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())

        val router =
            TestRouter(
                scope = scope,
                initialState = TestState(),
                handlers = listOf(ObserveHandler()),
                errorHandler = null,
            )

        router.onEvent(Observe("a"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a")

        scope.cancel()
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("start a", "stop a")
    }

    @Test
    fun `a latest event that returns at once stops the run before it and a later one starts again`() = runTest {
        val router = router(VisibilityHandler())

        router.onEvent(VisibilityChanged(visible = true))
        testScheduler.runCurrent()
        router.onEvent(VisibilityChanged(visible = false))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("following", "stopped following")
        router.trackedRunCount shouldBe 0

        router.onEvent(VisibilityChanged(visible = true))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf("following", "stopped following", "following")
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
        val handler = RunHandler(DispatchStrategy.Latest<Run>())
        val router = router(handler, errorHandler = { _, error, _ -> errors += error })

        router.onEvent(Run("a"))
        testScheduler.runCurrent()
        router.onEvent(Run("b"))
        handler.gate.complete(Unit)
        testScheduler.runCurrent()

        errors.shouldBeEmpty()
    }

    @Test
    fun `two handlers for one event fail when the router is created`() = runTest {
        val failure = shouldThrow<IllegalStateException> {
            router(RunHandler(DispatchStrategy.Concurrent), RunHandler(DispatchStrategy.Latest<Run>()))
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
        readyMadeStrategies.forEach { strategy ->
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

    @Test
    fun `a strategy that already ran an event runs nothing once the scope is cancelled`() = runTest {
        readyMadeStrategies.forEach { strategy ->
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val handler = RunHandler(strategy)

            val router =
                TestRouter(scope = scope, initialState = TestState(), handlers = listOf(handler), errorHandler = null)

            handler.gate.complete(Unit)
            router.onEvent(Run("a"))
            testScheduler.runCurrent()
            scope.cancel()
            router.onEvent(Run("b"))
            testScheduler.runCurrent()

            router.state.value.log shouldContainExactly listOf("start a", "end a")
        }
    }

    @Test
    fun `a router created with a cancelled scope runs nothing`() = runTest {
        readyMadeStrategies.forEach { strategy ->
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val handler = RunHandler(strategy)

            handler.gate.complete(Unit)
            scope.cancel()

            val router =
                TestRouter(scope = scope, initialState = TestState(), handlers = listOf(handler), errorHandler = null)

            router.onEvent(Run("a"))
            testScheduler.runCurrent()

            router.state.value shouldBe TestState()
        }
    }

    @Test
    fun `latest of a type cancels the active run of another event of that type`() = runTest {
        val gates = Gates()
        val router = stepRouter(gates, DispatchStrategy.Latest<Step>())

        send(router, Plus("a"), Minus("b"))
        gates.open(Plus("a"))
        gates.open(Minus("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf(
            "start Plus(item=a)",
            "cancelled Plus(item=a)",
            "start Minus(item=b)",
            "end Minus(item=b)",
        )
    }

    @Test
    fun `sequential of a type runs the events of that type in the order they were sent`() = runTest {
        val gates = Gates()
        val router = stepRouter(gates, DispatchStrategy.Sequential<Step>())

        send(router, Plus("a"), Minus("b"), Plus("c"))
        listOf(Plus("c"), Minus("b"), Plus("a")).forEach { event ->
            gates.open(event)
            testScheduler.runCurrent()
        }

        router.state.value.log shouldContainExactly listOf(
            "start Plus(item=a)",
            "end Plus(item=a)",
            "start Minus(item=b)",
            "end Minus(item=b)",
            "start Plus(item=c)",
            "end Plus(item=c)",
        )
    }

    @Test
    fun `drop if already running of a type ignores an event while another of that type is active`() = runTest {
        val gates = Gates()
        val router = stepRouter(gates, DispatchStrategy.DropIfAlreadyRunning<Step>())

        send(router, Plus("a"), Minus("b"))
        gates.open(Plus("a"))
        testScheduler.runCurrent()
        send(router, Minus("b"))
        gates.open(Minus("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf(
            "start Plus(item=a)",
            "end Plus(item=a)",
            "start Minus(item=b)",
            "end Minus(item=b)",
        )
    }

    @Test
    fun `a strategy with a key keeps the events of one key in order and lets other keys run`() = runTest {
        val gates = Gates()
        val router = stepRouter(gates, DispatchStrategy.Sequential<Step> { it.item })

        send(router, Plus("x"), Minus("x"), Plus("y"))
        listOf(Plus("y"), Minus("x"), Plus("x")).forEach { event ->
            gates.open(event)
            testScheduler.runCurrent()
        }

        router.state.value.log shouldContainExactly listOf(
            "start Plus(item=x)",
            "start Plus(item=y)",
            "end Plus(item=y)",
            "end Plus(item=x)",
            "start Minus(item=x)",
            "end Minus(item=x)",
        )
    }

    @Test
    fun `equal events run one after the other and none is tracked once they have ended`() = runTest {
        val gates = Gates()
        val router = router(StepHandler(Tick::class, DispatchStrategy.Sequential<Tick>(), gates))

        send(router, Tick, Tick, Tick)

        router.state.value.log shouldContainExactly listOf("start Tick")
        router.trackedRunCount shouldBe 3

        repeat(3) {
            gates.open(Tick)
            testScheduler.runCurrent()
        }

        router.state.value.log shouldContainExactly List(3) { listOf("start Tick", "end Tick") }.flatten()
        router.trackedRunCount shouldBe 0
    }

    @Test
    fun `a strategy written by hand can ignore one event and replace another`() = runTest {
        val gates = Gates()

        // A second plus is ignored, and a plus cancels a minus.
        val plusStrategy = DispatchStrategy<Plus> { _, active ->
            if (active.any { it is Plus }) {
                Dispatch.Ignore
            } else {
                Dispatch.Replace(active.filterIsInstance<Minus>())
            }
        }

        val router = router(
            StepHandler(Plus::class, plusStrategy, gates),
            StepHandler(Minus::class, DispatchStrategy.Concurrent, gates),
        )

        send(router, Minus("a"), Plus("b"), Plus("c"))
        gates.open(Plus("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf(
            "start Minus(item=a)",
            "cancelled Minus(item=a)",
            "start Plus(item=b)",
            "end Plus(item=b)",
        )
    }

    @Test
    fun `a waiting run that is replaced never starts`() = runTest {
        val gates = Gates()

        val router = router(
            StepHandler(Plus::class, DispatchStrategy.Sequential<Step>(), gates),
            StepHandler(Minus::class, DispatchStrategy.Latest<Step>(), gates),
        )

        send(router, Plus("a"), Plus("b"), Minus("c"))
        gates.open(Plus("b"))
        testScheduler.runCurrent()

        router.state.value.log shouldContainExactly listOf(
            "start Plus(item=a)",
            "cancelled Plus(item=a)",
            "start Minus(item=c)",
        )
        router.trackedRunCount shouldBe 1
    }

    @Test
    fun `an answer that names an event that is not active runs the event`() = runTest {
        val gates = Gates()

        listOf<Dispatch>(
            Dispatch.Replace(listOf(Minus("gone"))),
            Dispatch.WaitFor(listOf(Minus("gone"))),
        ).forEach { answer ->
            val router = router(StepHandler(Plus::class, { _, _ -> answer }, gates))

            send(router, Plus("a"))

            router.state.value.log shouldContainExactly listOf("start Plus(item=a)")
        }
    }

    @Test
    fun `a strategy that throws fails in the scope and not in the error handler and the event does not run`() =
        runTest {
            val gates = Gates()
            val uncaught = mutableListOf<Throwable>()
            val reported = mutableListOf<Throwable>()

            val router = router(
                StepHandler(Plus::class, { _, _ -> error("bad strategy") }, gates),
                StepHandler(Minus::class, DispatchStrategy.Concurrent, gates),
                context = CoroutineExceptionHandler { _, error -> uncaught += error },
                errorHandler = { _, error, _ -> reported += error },
            )

            send(router, Plus("a"), Minus("b"))

            uncaught.map { it.message } shouldContainExactly listOf("The strategy of StepHandler failed for Plus")
            uncaught.map { it.cause?.message } shouldContainExactly listOf("bad strategy")
            reported.shouldBeEmpty()
            router.state.value.log shouldContainExactly listOf("start Minus(item=b)")
        }

    @Test
    fun `a strategy is asked again when the active events changed while it was deciding`() = runTest {
        val gates = Gates()
        val seen = mutableListOf<List<UiEvent>>()
        lateinit var router: TestRouter

        // The first time it is asked, another event gets in before it answers.
        val strategy = DispatchStrategy<Plus> { _, active ->
            seen += active

            if (seen.size == 1) router.onEvent(Minus("b"))

            Dispatch.WaitFor(active)
        }

        router = router(
            StepHandler(Plus::class, strategy, gates),
            StepHandler(Minus::class, DispatchStrategy.Concurrent, gates),
        )

        send(router, Plus("a"))
        gates.open(Minus("b"))
        testScheduler.runCurrent()

        seen shouldContainExactly listOf(emptyList(), listOf(Minus("b")))
        router.state.value.log shouldContainExactly listOf(
            "start Minus(item=b)",
            "end Minus(item=b)",
            "start Plus(item=a)",
        )
    }

    // The one test that looks inside the router: finished runs must not be kept, or a long-lived screen
    // would hold on to every run it ever started.
    @Test
    fun `a run that has ended is no longer tracked`() = runTest {
        listOf(EmptyCoroutineContext, Dispatchers.Unconfined).forEach { dispatcher ->
            val busy = RunHandler(DispatchStrategy.DropIfAlreadyRunning<Run>())
            val router = router(busy, ObserveHandler(), context = dispatcher)

            router.onEvent(Run("a"))
            router.onEvent(Observe("a"))
            router.onEvent(Observe("b"))
            testScheduler.runCurrent()

            router.trackedRunCount shouldBe 2

            busy.gate.complete(Unit)
            testScheduler.runCurrent()

            // The observation never returns, so it is still tracked; the run that ended is not.
            router.trackedRunCount shouldBe 1
        }
    }

    @Test
    fun `a replaced latest run is forgotten when the newest one ends`() = runTest {
        val handler = RunHandler(DispatchStrategy.Latest<Run>())
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

    /** A router whose plus and minus events share [strategy]. */
    private fun TestScope.stepRouter(gates: Gates, strategy: DispatchStrategy<Step>): TestRouter = router(
        StepHandler(Plus::class, strategy, gates),
        StepHandler(Minus::class, strategy, gates),
    )

    /** Sends [events] one at a time and lets each get as far as it can. */
    private fun TestScope.send(router: TestRouter, vararg events: TestEvent) {
        events.forEach { event ->
            router.onEvent(event)
            testScheduler.runCurrent()
        }
    }

    /** Sends one `Run` per id to a handler with [strategy], lets them finish and returns what was logged. */
    private fun TestScope.logAfter(strategy: DispatchStrategy<Run>, vararg ids: String): List<String> {
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
        override val strategy = DispatchStrategy.Sequential<Run>()

        override suspend fun handle(event: Run, store: TestStore) {
            check(event.id != "bad") { "bad" }

            store.log(event.id)
        }
    }
}
