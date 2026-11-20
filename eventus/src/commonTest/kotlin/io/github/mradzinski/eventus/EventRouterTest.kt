package io.github.mradzinski.eventus

import app.cash.turbine.test
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

/** The public function, used the way a view model uses it: as the delegate of the screen's own class. */
class EventRouterTest {

    @Test
    fun `a class that delegates to the router shows its state and takes its events`() = runTest {
        val handler = RunHandler(DispatchStrategy.Concurrent)
        val screen = PlainScreen(backgroundScope, listOf(handler))

        screen.state.value shouldBe TestState(log = listOf("initial"))

        handler.gate.complete(Unit)
        screen.onEvent(Run("a"))
        testScheduler.runCurrent()

        screen.state.value.log shouldContainExactly listOf("initial", "start a", "end a")
    }

    @Test
    fun `a class that delegates with an error handler shows the effect it emits`() = runTest {
        val screen = ReportingScreen(backgroundScope, listOf(BoomHandler()))

        screen.onEvent(Boom)
        testScheduler.runCurrent()

        screen.effects.test {
            awaitItem() shouldBe Failed("boom")
        }
    }

    /** No error handler: the event type is inferred from the interface the class implements. */
    private class PlainScreen(scope: CoroutineScope, handlers: Collection<TestHandler>) :
        EventRouter<TestEvent, TestState, TestEffect> by EventRouter(
            scope = scope,
            initialState = TestState(log = listOf("initial")),
            handlers = handlers,
        )

    private class ReportingScreen(scope: CoroutineScope, handlers: Collection<TestHandler>) :
        EventRouter<TestEvent, TestState, TestEffect> by EventRouter(
            scope = scope,
            initialState = TestState(),
            handlers = handlers,
            errorHandler = { _, error, store -> store.emit(Failed(error.message)) },
        )
}
