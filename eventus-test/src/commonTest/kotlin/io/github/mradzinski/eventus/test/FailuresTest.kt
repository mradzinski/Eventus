package io.github.mradzinski.eventus.test

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class FailuresTest {

    private val repository = FakeRepository()

    @Test
    fun `a handler that throws is read from the failures and its states are kept`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
    ) {
        repository.failure = IllegalStateException("offline")

        dispatch(TestEvent.OnRefresh)

        failures.single().shouldBeInstanceOf<IllegalStateException>()
        failures.single().message shouldBe "offline"
        states.map { it.loading } shouldContainExactly listOf(false, true, false)
    }

    @Test
    fun `every event that fails adds a failure`() = runEventHandlerTest(
        handler = FailingItemClickHandler(),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnItemClick("1"), TestEvent.OnItemClick("2"))

        failures.map { it.message } shouldContainExactly listOf("no 1", "no 2")
    }

    @Test
    fun `a failure nobody read fails the test with that failure`() = runTest {
        repository.failure = IllegalStateException("offline")

        val thrown = shouldThrow<IllegalStateException> {
            eventHandlerTest(
                handlers = listOf(OnRefreshHandler(repository)),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnRefresh)
            }
        }

        thrown.message shouldBe "offline"
    }

    @Test
    fun `several failures nobody read fail the test with the first and carry the rest`() = runTest {
        val thrown = shouldThrow<IllegalStateException> {
            eventHandlerTest(
                handlers = listOf(FailingItemClickHandler()),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnItemClick("1"), TestEvent.OnItemClick("2"))
            }
        }

        thrown.message shouldBe "no 1"
        thrown.suppressedExceptions.map { it.message } shouldContainExactly listOf("no 2")
    }

    @Test
    fun `an assertion that fails carries the unread failure with it`() = runTest {
        repository.failure = IllegalStateException("offline")

        val thrown = shouldThrow<AssertionError> {
            eventHandlerTest(
                handlers = listOf(OnRefreshHandler(repository)),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnRefresh)

                repository.refreshes shouldBe 0
            }
        }

        thrown.suppressedExceptions.map { it.message } shouldContainExactly listOf("offline")
    }

    @Test
    fun `a failure that happens as the block ends still fails the test`() = runTest {
        val thrown = shouldThrow<IllegalStateException> {
            eventHandlerTest(
                handlers = listOf(FailingItemClickHandler()),
                initialState = TestState(),
            ) {
                router.onEvent(TestEvent.OnItemClick("late"))
            }
        }

        thrown.message shouldBe "no late"
    }

    @Test
    fun `an event without a handler fails the test where it is dispatched`() = runTest {
        val thrown = shouldThrow<IllegalStateException> {
            eventHandlerTest<TestEvent, TestState, TestEffect>(
                handlers = listOf(OnRefreshHandler(repository)),
                initialState = TestState(),
            ) {
                dispatch(TestEvent.OnSaveClick)
            }
        }

        thrown.message shouldContain "OnSaveClick"
    }

    @Test
    fun `with an error handler a failure goes to it and does not fail the test`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
        errorHandler = MessageErrorHandler(),
    ) {
        repository.failure = IllegalStateException("offline")

        dispatch(TestEvent.OnRefresh)

        state.message shouldBe "offline"
        state.loading shouldBe false
        effects shouldContainExactly listOf(TestEffect.Opened("error"))
    }

    @Test
    fun `with an error handler the failure is still listed`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
        errorHandler = MessageErrorHandler(),
    ) {
        repository.failure = IllegalStateException("offline")

        dispatch(TestEvent.OnRefresh)

        failures.single().message shouldBe "offline"
    }
}
