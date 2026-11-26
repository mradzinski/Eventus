package io.github.mradzinski.eventus.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class WaitingTest {

    private val repository = FakeRepository()

    @Test
    fun `work on another thread is waited for and then read`() = runEventHandlerTest(
        handler = LoadElsewhereHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnRefresh)
        awaitIdle()

        state.items shouldContainExactly listOf("item 0", "item 1")
        effects shouldContainExactly listOf(TestEffect.Saved)
    }

    @Test
    fun `a failure on another thread is waited for and then read`() = runEventHandlerTest(
        handler = FailElsewhereHandler(),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnSaveClick)
        awaitIdle()

        failures.single().message shouldBe "the disk is full"
    }

    @Test
    fun `a failure on another thread that nobody read fails the test`() = runTest {
        val thrown = shouldThrow<IllegalStateException> {
            eventHandlerTest(
                handlers = listOf(FailElsewhereHandler()),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnSaveClick)
                awaitIdle()
            }
        }

        thrown.message shouldBe "the disk is full"
    }

    @Test
    fun `waiting for idle lets a handler that waits finish`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnQueryChange("c"))
        awaitIdle()

        repository.searched shouldContainExactly listOf("c")
    }

    @Test
    fun `waiting for idle with nothing dispatched returns at once`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(),
    ) {
        awaitIdle()

        effects.shouldBeEmpty()
    }

    @Test
    fun `a handler that never returns is waited for by the state it gives`() = runEventHandlerTest(
        handler = WatchElsewhereHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnScreenOpened)
        router.state.first { it.items.isNotEmpty() }

        state.items shouldContainExactly listOf("item 0", "item 1")
    }

    @Test
    fun `a wait in the test itself is on the test's clock and not on the time limit`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
        timeout = SHORT_LIMIT_MILLIS.milliseconds,
    ) {
        dispatch(TestEvent.OnQueryChange("c"))
        delay(10.minutes)
        advanceBy(10.minutes)

        repository.searched shouldContainExactly listOf("c")
    }

    @Test
    fun `waiting for idle on a handler that never returns fails and names the event`() = runTest {
        val thrown = shouldThrow<AssertionError> {
            eventHandlerTest(
                handlers = listOf(NeverReturnsHandler()),
                initialState = TestState(),
                timeout = SHORT_LIMIT_MILLIS.milliseconds,
            ) {
                dispatch(TestEvent.OnScreenOpened)
                awaitIdle()
            }
        }

        thrown.message shouldContain "The test did not finish within ${SHORT_LIMIT_MILLIS}ms"
        thrown.message shouldContain "Still being handled: #1 OnScreenOpened"
        thrown.message shouldContain "router.state.first"
    }

    @Test
    fun `a state that never comes fails at the time limit and says no event was running`() = runTest {
        val thrown = shouldThrow<AssertionError> {
            eventHandlerTest(
                handlers = listOf(OnItemClickHandler()),
                initialState = TestState(),
                timeout = SHORT_LIMIT_MILLIS.milliseconds,
            ) {
                router.state.first { it.loading }
            }
        }

        thrown.message shouldContain "No event was still being handled"
    }

    @Test
    fun `a failure nobody read travels with a test that runs out of time`() = runTest {
        val thrown = shouldThrow<AssertionError> {
            eventHandlerTest(
                handlers = listOf(FailingItemClickHandler()),
                initialState = TestState(),
                timeout = SHORT_LIMIT_MILLIS.milliseconds,
            ) {
                dispatch(TestEvent.OnItemClick("1"))
                router.state.first { it.loading }
            }
        }

        thrown.suppressedExceptions.map { it.message } shouldContainExactly listOf("no 1")
    }

    private companion object {

        /** Real time, so short: each test that runs out of time really waits this long. */
        const val SHORT_LIMIT_MILLIS = 300
    }
}
