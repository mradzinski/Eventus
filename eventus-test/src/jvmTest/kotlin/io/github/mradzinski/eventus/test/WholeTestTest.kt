package io.github.mradzinski.eventus.test

import io.github.mradzinski.eventus.DispatchStrategy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * What a whole test does, seen from outside it. On the JVM a test function returns when the test has
 * ended and throws what the test threw, so it can be called from another test.
 */
class WholeTestTest {

    @Test
    fun `a handler that never returns is stopped when the test ends`() {
        val handler = NeverReturnsHandler()

        runEventHandlerTest(
            handler = handler,
            initialState = TestState(),
        ) {
            dispatch(TestEvent.OnScreenOpened)

            handler.started shouldBe true
            handler.stopped shouldBe false
        }

        handler.stopped shouldBe true
    }

    @Test
    fun `a strategy that throws fails the whole test and names the handler and the event`() {
        val thrown = shouldThrow<IllegalStateException> {
            runEventHandlerTest(
                handler = BadStrategyHandler(),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnSaveClick)
            }
        }

        thrown.message shouldContain "BadStrategyHandler"
        thrown.message shouldContain "OnSaveClick"
        thrown.cause?.message shouldBe "bad strategy"
    }

    @Test
    fun `a test that runs out of time fails with the events still being handled`() {
        val thrown = shouldThrow<AssertionError> {
            runEventHandlerTest(
                handler = NeverReturnsHandler(),
                initialState = TestState(),
                timeout = SHORT_LIMIT_MILLIS.milliseconds,
            ) {
                dispatch(TestEvent.OnScreenOpened)
                awaitIdle()
            }
        }

        thrown.message shouldContain "Still being handled: #1 OnScreenOpened"
    }

    @Test
    fun `without the wait a read right after dispatch does not see work on another thread`() {
        repeat(TRIES) {
            shouldThrow<AssertionError> {
                runEventHandlerTest(
                    handler = LoadElsewhereHandler(FakeRepository()),
                    initialState = TestState(),
                ) {
                    dispatch(TestEvent.OnRefresh)

                    state.items shouldContainExactly listOf("item 0", "item 1")
                }
            }
        }
    }

    private class BadStrategyHandler : TestHandler<TestEvent.OnSaveClick> {

        override val eventType = TestEvent.OnSaveClick::class
        override val strategy = DispatchStrategy<TestEvent.OnSaveClick> { _, _ -> error("bad strategy") }

        override suspend fun handle(event: TestEvent.OnSaveClick, store: TestStore) = Unit
    }

    private companion object {

        const val SHORT_LIMIT_MILLIS = 300
        const val TRIES = 10
    }
}
