package io.github.mradzinski.eventus

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ScreenStoreTest {

    @Test
    fun `it starts with the initial state`() {
        val store = TestStore(TestState(log = listOf("initial")))

        store.current shouldBe TestState(log = listOf("initial"))
        store.state.value shouldBe store.current
    }

    @Test
    fun `an update replaces the state with what the transform returns`() {
        val store = TestStore(TestState())

        store.update { it.copy(log = it.log + "a") }
        store.update { it.copy(log = it.log + "b") }

        store.current shouldBe TestState(log = listOf("a", "b"))
    }

    @Test
    fun `a collector gets the latest state first and then each new one`() = runTest {
        val store = TestStore(TestState())

        store.log("a")

        store.state.test {
            awaitItem() shouldBe TestState(log = listOf("a"))

            store.log("b")

            awaitItem() shouldBe TestState(log = listOf("a", "b"))
        }
    }

    @Test
    fun `an update that leaves the state equal emits nothing`() = runTest {
        val store = TestStore(TestState())

        store.state.test {
            awaitItem()

            store.update { it.copy(log = emptyList()) }

            expectNoEvents()
        }
    }

    @Test
    fun `an optimistic update shows at once and stays when the work finishes`() = runTest {
        val store = TestStore(TestState())
        val gate = CompletableDeferred<Unit>()
        val work = async {
            store.updateOptimistically(
                stateUpdate = { it.copy(log = it.log + "shown") },
                stateIfFailed = { it.copy(log = it.log - "shown") },
            ) {
                gate.await()

                "result"
            }
        }

        testScheduler.runCurrent()

        store.current.log shouldContainExactly listOf("shown")

        gate.complete(Unit)

        work.await() shouldBe "result"
        store.current.log shouldContainExactly listOf("shown")
    }

    @Test
    fun `an optimistic update is put back when the work fails and the failure goes on`() = runTest {
        val store = TestStore(TestState())
        val failure = shouldThrow<IllegalStateException> {
            store.updateOptimistically(
                stateUpdate = { it.copy(log = it.log + "shown") },
                stateIfFailed = { it.copy(log = it.log - "shown") },
            ) {
                error("boom")
            }
        }

        failure.message shouldBe "boom"
        store.current.log.shouldBeEmpty()
    }

    @Test
    fun `an optimistic update is put back when the work is cancelled`() = runTest {
        val store = TestStore(TestState())
        val work = launch {
            store.updateOptimistically(
                stateUpdate = { it.copy(log = it.log + "shown") },
                stateIfFailed = { it.copy(log = it.log - "shown") },
            ) {
                awaitCancellation()
            }
        }

        testScheduler.runCurrent()

        store.current.log shouldContainExactly listOf("shown")

        work.cancel()
        testScheduler.runCurrent()

        store.current.log.shouldBeEmpty()
    }

    @Test
    fun `putting an optimistic update back keeps other changes made in the meantime`() = runTest {
        val store = TestStore(TestState())
        val gate = CompletableDeferred<Unit>()
        val work = launch {
            // The failure is expected here: left alone it would also fail the test's own scope.
            shouldThrow<IllegalStateException> {
                store.updateOptimistically(
                    stateUpdate = { it.copy(log = it.log + "shown") },
                    stateIfFailed = { it.copy(log = it.log - "shown") },
                ) {
                    gate.await()
                }
            }
        }

        testScheduler.runCurrent()
        store.log("other")
        gate.completeExceptionally(IllegalStateException("boom"))
        work.join()

        store.current.log shouldContainExactly listOf("other")
    }

    @Test
    fun `effects emitted before anything collects are all delivered in order`() = runTest {
        val store = TestStore(TestState())
        val effects = List(200) { Shown("effect $it") }

        effects.forEach(store::emit)

        store.effects.test {
            effects.forEach { awaitItem() shouldBe it }
        }
    }

    @Test
    fun `an effect handed to a collector that is cancelled before receiving it goes to the next one`() = runTest {
        val store = TestStore(TestState())
        val received = mutableListOf<TestEffect>()
        val collector = launch { store.effects.collect { received += it } }

        // The collector is now waiting. The effect resumes it, but it is cancelled before it runs again.
        testScheduler.runCurrent()
        store.emit(Shown("kept"))
        collector.cancel()
        testScheduler.runCurrent()

        received.shouldBeEmpty()
        store.effects.test {
            awaitItem() shouldBe Shown("kept")
        }
    }

    @Test
    fun `an effect is delivered once and never again to a later collector`() = runTest {
        val store = TestStore(TestState())

        store.emit(Shown("once"))

        store.effects.test {
            awaitItem() shouldBe Shown("once")
        }

        store.effects.test {
            expectNoEvents()
        }
    }
}
