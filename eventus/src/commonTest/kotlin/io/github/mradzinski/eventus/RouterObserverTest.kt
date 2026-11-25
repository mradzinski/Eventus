package io.github.mradzinski.eventus

import app.cash.turbine.test
import io.github.mradzinski.eventus.RouterTrace.Cancelled
import io.github.mradzinski.eventus.RouterTrace.Cancelled.Reason
import io.github.mradzinski.eventus.RouterTrace.Completed
import io.github.mradzinski.eventus.RouterTrace.Dispatched
import io.github.mradzinski.eventus.RouterTrace.EffectEmitted
import io.github.mradzinski.eventus.RouterTrace.Failed
import io.github.mradzinski.eventus.RouterTrace.Started
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class RouterObserverTest {

    private val gates = Gates()
    private val reports = mutableListOf<RouterTrace<TestEvent>>()

    @Test
    fun `an event that runs is reported as dispatched then started then completed`() = runTest {
        val router = router(StepHandler(Tick::class, DispatchStrategy.Concurrent, gates))

        send(router, Tick)
        open(Tick)

        reports shouldContainExactly listOf(
            Dispatched(Tick, number = 1, dispatch = Dispatch.Run, replacedOrAwaited = emptyList()),
            Started(Tick, number = 1),
            Completed(Tick, number = 1),
        )
    }

    @Test
    fun `an event that is ignored is reported once`() = runTest {
        val router = stepRouter(DispatchStrategy.DropIfAlreadyRunning<Step>())

        send(router, Plus("save"), Minus("delete"))

        reports.drop(2) shouldContainExactly listOf(
            Dispatched(Minus("delete"), number = 2, dispatch = Dispatch.Ignore, replacedOrAwaited = emptyList()),
        )
    }

    @Test
    fun `a replaced run is reported after the event that replaced it and says which one`() = runTest {
        val router = stepRouter(DispatchStrategy.Latest<Step>())

        send(router, Plus("query"), Minus("filter"))

        sentences().drop(2) shouldContainExactly listOf(
            "#2 Minus(item=filter) replaces #1",
            "#1 Plus(item=query) was replaced by #2",
            "#2 Minus(item=filter) started",
        )
        reports[3] shouldBe Cancelled(Plus("query"), number = 1, reason = Reason.ReplacedBy(number = 2))
    }

    @Test
    fun `an event that waits is reported with the events it waits for and starts when they have ended`() = runTest {
        val router = router(StepHandler(Tick::class, DispatchStrategy.Sequential<Tick>(), gates))

        send(router, Tick, Tick)
        open(Tick)

        sentences() shouldContainExactly listOf(
            "#1 Tick runs",
            "#1 Tick started",
            "#2 Tick waits for #1",
            "#1 Tick completed",
            "#2 Tick started",
        )
    }

    @Test
    fun `a run that fails is reported with its error and the error handler still gets it`() = runTest {
        val handled = mutableListOf<Throwable>()
        val router = router(BoomHandler(), errorHandler = { _, error, _ -> handled += error })

        send(router, Boom)

        val failed = reports.last().shouldBeInstanceOf<Failed<TestEvent>>()

        failed.error.message shouldBe "boom"
        failed.toString() shouldBe "#1 Boom failed: ${failed.error}"
        handled shouldContainExactly listOf(failed.error)
    }

    @Test
    fun `runs that end with the scope are reported as cancelled with the scope`() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job())

        val router = router(StepHandler(Tick::class, DispatchStrategy.Sequential<Tick>(), gates), scope = scope)

        send(router, Tick, Tick)
        scope.cancel()
        testScheduler.runCurrent()

        sentences().takeLast(2) shouldContainExactly listOf(
            "#1 Tick was cancelled with the scope",
            "#2 Tick was cancelled with the scope",
        )
    }

    @Test
    fun `a handler that ends by its own cancellation is reported as cancelled by its own handler`() = runTest {
        val router = router(GivesUpHandler())

        send(router, Tick)

        reports.last() shouldBe Cancelled(Tick, number = 1, reason = Reason.ByHandler)
        sentences().last() shouldBe "#1 Tick was cancelled by its own handler"
    }

    @Test
    fun `a run named by two replacements is reported as replaced by the first`() = runTest {
        val router = stepRouter(DispatchStrategy.Latest<Step>())

        send(router, Plus("a"))
        router.onEvent(Minus("b"))
        router.onEvent(Minus("c"))
        testScheduler.runCurrent()

        sentences() shouldContainExactly listOf(
            "#1 Plus(item=a) runs",
            "#1 Plus(item=a) started",
            "#2 Minus(item=b) replaces #1",
            "#3 Minus(item=c) replaces #1, #2",
            "#1 Plus(item=a) was replaced by #2",
            "#2 Minus(item=b) was replaced by #3",
            "#3 Minus(item=c) started",
        )
    }

    @Test
    fun `an answer that names no active event is reported as an event that runs`() = runTest {
        val gone = listOf(Minus("gone"))

        val router = router(
            StepHandler(Plus::class, { _, _ -> Dispatch.WaitFor(gone) }, gates),
            StepHandler(Tick::class, { _, _ -> Dispatch.Replace(gone) }, gates),
        )

        send(router, Plus("a"), Tick)

        sentences() shouldContainExactly listOf(
            "#1 Plus(item=a) runs",
            "#1 Plus(item=a) started",
            "#2 Tick runs",
            "#2 Tick started",
        )
    }

    @Test
    fun `an observer that sends an event gets the reports of that event in order`() = runTest {
        lateinit var router: EventRouter<TestEvent, TestState, TestEffect>

        router = router(
            StepHandler(Plus::class, DispatchStrategy.Concurrent, gates),
            StepHandler(Minus::class, DispatchStrategy.Concurrent, gates),
            observer = { trace ->
                reports += trace

                if (trace == Started(Plus("a"), number = 1)) router.onEvent(Minus("b"))
            },
        )

        send(router, Plus("a"))

        sentences() shouldContainExactly listOf(
            "#1 Plus(item=a) runs",
            "#1 Plus(item=a) started",
            "#2 Minus(item=b) runs",
            "#2 Minus(item=b) started",
        )
    }

    @Test
    fun `an observer that throws fails in the scope and not in the error handler and the event still runs`() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val handled = mutableListOf<Throwable>()

        val router = router(
            StepHandler(Tick::class, DispatchStrategy.Concurrent, gates),
            context = CoroutineExceptionHandler { _, error -> uncaught += error },
            errorHandler = { _, error, _ -> handled += error },
            observer = { trace ->
                reports += trace

                if (trace is Started) error("bad observer")
            },
        )

        send(router, Tick)
        open(Tick)

        uncaught.map { it.message } shouldContainExactly listOf("The observer failed for: #1 Tick started")
        uncaught.map { it.cause?.message } shouldContainExactly listOf("bad observer")
        handled.shouldBeEmpty()
        router.state.value.log shouldContainExactly listOf("start Tick", "end Tick")
        sentences().last() shouldBe "#1 Tick completed"
    }

    @Test
    fun `an effect is reported when it is emitted with the event it came from`() = runTest {
        val router = router(ShowHandler())

        send(router, Run("a"), Run("b"))

        reports[2] shouldBe EffectEmitted(Run("a"), number = 1, effect = Shown("a"))
        sentences() shouldContainExactly listOf(
            "#1 Run(id=a) runs",
            "#1 Run(id=a) started",
            "#1 Run(id=a) emitted Shown(text=a)",
            "#1 Run(id=a) completed",
            "#2 Run(id=b) runs",
            "#2 Run(id=b) started",
            "#2 Run(id=b) emitted Shown(text=b)",
            "#2 Run(id=b) completed",
        )
    }

    @Test
    fun `an effect of the error handler is reported with the event that failed and before the failure`() = runTest {
        val router = router(
            BoomHandler(),
            errorHandler = { _, error, store -> store.emit(Failed(error.message)) },
        )

        send(router, Boom)

        sentences().drop(2).dropLast(1) shouldContainExactly listOf("#1 Boom emitted Failed(message=boom)")
        reports.last().shouldBeInstanceOf<RouterTrace.Failed<TestEvent>>()
    }

    @Test
    fun `an effect emitted as a run is replaced is reported before the end of that run`() = runTest {
        val router = router(ObserveThenShowHandler())

        send(router, Observe("a"), Observe("b"))

        sentences().drop(2) shouldContainExactly listOf(
            "#2 Observe(id=b) replaces #1",
            "#1 Observe(id=a) emitted Shown(text=stopped a)",
            "#1 Observe(id=a) was replaced by #2",
            "#2 Observe(id=b) started",
        )
    }

    @Test
    fun `the ui gets every effect that is reported`() = runTest {
        val router = router(
            ShowHandler(),
            BoomHandler(),
            errorHandler = { _, _, store -> store.emit(Shown("failed")) },
        )

        send(router, Run("a"), Boom, Run("b"))

        router.effects.test {
            awaitItem() shouldBe Shown("a")
            awaitItem() shouldBe Shown("failed")
            awaitItem() shouldBe Shown("b")
            expectNoEvents()
        }
        reports.filterIsInstance<EffectEmitted<TestEvent>>().map { it.effect } shouldContainExactly listOf(
            Shown("a"),
            Shown("failed"),
            Shown("b"),
        )
    }

    @Test
    fun `handlers of an observed router read and write one state`() = runTest {
        val router = router(ShowHandler(), StepHandler(Tick::class, DispatchStrategy.Concurrent, gates))

        send(router, Tick, Run("a"))
        open(Tick)

        router.state.value.log shouldContainExactly listOf("start Tick", "shown a", "end Tick")
    }

    private fun sentences(): List<String> = reports.map { it.toString() }

    private fun TestScope.router(
        vararg handlers: TestHandler,
        context: CoroutineContext = EmptyCoroutineContext,
        scope: CoroutineScope = CoroutineScope(backgroundScope.coroutineContext + context),
        errorHandler: ErrorHandler<TestEvent, TestState, TestEffect>? = null,
        observer: RouterObserver<TestEvent> = RouterObserver { reports += it },
    ): EventRouter<TestEvent, TestState, TestEffect> = EventRouter(
        scope = scope,
        initialState = TestState(),
        handlers = handlers.toList(),
        errorHandler = errorHandler,
        observer = observer,
    )

    /** A router whose plus and minus events share [strategy]. */
    private fun TestScope.stepRouter(strategy: DispatchStrategy<Step>) = router(
        StepHandler(Plus::class, strategy, gates),
        StepHandler(Minus::class, strategy, gates),
    )

    /** Sends [events] one at a time and lets each get as far as it can. */
    private fun TestScope.send(router: EventRouter<TestEvent, TestState, TestEffect>, vararg events: TestEvent) {
        events.forEach { event ->
            router.onEvent(event)
            testScheduler.runCurrent()
        }
    }

    /** Lets one run of [event] end. */
    private fun TestScope.open(event: TestEvent) {
        gates.open(event)
        testScheduler.runCurrent()
    }

    /** Ends the way a handler does when a timeout of its own is not caught. */
    private class GivesUpHandler : EventHandler<Tick, TestState, TestEffect> {

        override val eventType = Tick::class

        override suspend fun handle(event: Tick, store: TestStore): Unit = throw CancellationException("gave up")
    }

    /** Logs and shows the id of its event. */
    private class ShowHandler : EventHandler<Run, TestState, TestEffect> {

        override val eventType = Run::class

        override suspend fun handle(event: Run, store: TestStore) {
            store.log("shown ${event.id}")
            store.emit(Shown(event.id))
        }
    }

    /** Runs until it is replaced and emits an effect on its way out. */
    private class ObserveThenShowHandler : EventHandler<Observe, TestState, TestEffect> {

        override val eventType = Observe::class
        override val strategy = DispatchStrategy.Latest<Observe>()

        override suspend fun handle(event: Observe, store: TestStore) {
            try {
                awaitCancellation()
            } finally {
                store.emit(Shown("stopped ${event.id}"))
            }
        }
    }
}
