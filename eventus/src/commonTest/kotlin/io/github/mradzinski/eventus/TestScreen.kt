package io.github.mradzinski.eventus

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** The state of the screen the tests route events for: a log of what its handlers did, in order. */
data class TestState(val log: List<String> = emptyList()) : UiState

sealed interface TestEvent : UiEvent

data class Run(val id: String) : TestEvent

data class Observe(val id: String) : TestEvent

data object Boom : TestEvent

data class VisibilityChanged(val visible: Boolean) : TestEvent

sealed interface TestEffect : UiEffect

data class Failed(val message: String?) : TestEffect

data class Shown(val text: String) : TestEffect

typealias TestStore = ScreenStore<TestState, TestEffect>

typealias TestHandler = EventHandler<*, TestState, TestEffect>

fun TestStore.log(entry: String) = update { it.copy(log = it.log + entry) }

/** Logs the start and the end of each run and waits for [gate] in between, so a test decides when runs overlap. */
class RunHandler(override val strategy: DispatchStrategy) : EventHandler<Run, TestState, TestEffect> {

    val gate = CompletableDeferred<Unit>()

    override val eventType = Run::class

    override suspend fun handle(event: Run, store: TestStore) {
        store.log("start ${event.id}")
        gate.await()
        store.log("end ${event.id}")
    }
}

/** Runs until it is cancelled, as a handler that observes data does, and logs both ends of the run. */
class ObserveHandler : EventHandler<Observe, TestState, TestEffect> {

    override val eventType = Observe::class
    override val strategy = DispatchStrategy.Latest

    override suspend fun handle(event: Observe, store: TestStore) {
        store.log("start ${event.id}")

        try {
            awaitCancellation()
        } finally {
            store.log("stop ${event.id}")
        }
    }
}

/**
 * Follows something for as long as the UI says it is visible: the recipe the README gives for work that
 * must stop while the UI is hidden. A new event cancels the run before it, and a hidden one returns at once.
 */
class VisibilityHandler : EventHandler<VisibilityChanged, TestState, TestEffect> {

    override val eventType = VisibilityChanged::class
    override val strategy = DispatchStrategy.Latest

    override suspend fun handle(event: VisibilityChanged, store: TestStore) {
        if (!event.visible) return

        store.log("following")

        try {
            awaitCancellation()
        } finally {
            store.log("stopped following")
        }
    }
}

class BoomHandler(override val strategy: DispatchStrategy = DispatchStrategy.Concurrent) :
    EventHandler<Boom, TestState, TestEffect> {

    override val eventType = Boom::class

    override suspend fun handle(event: Boom, store: TestStore): Unit = throw IllegalStateException("boom")
}
