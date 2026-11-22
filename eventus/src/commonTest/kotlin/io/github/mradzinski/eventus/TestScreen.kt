package io.github.mradzinski.eventus

import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel

/** The state of the screen the tests route events for: a log of what its handlers did, in order. */
data class TestState(val log: List<String> = emptyList()) : UiState

sealed interface TestEvent : UiEvent

data class Run(val id: String) : TestEvent

data class Observe(val id: String) : TestEvent

data object Boom : TestEvent

data class VisibilityChanged(val visible: Boolean) : TestEvent

/** Two events that share a type and carry the item they are about, for strategies that span events. */
sealed interface Step : TestEvent {

    val item: String
}

data class Plus(override val item: String) : Step

data class Minus(override val item: String) : Step

data object Tick : TestEvent

sealed interface TestEffect : UiEffect

data class Failed(val message: String?) : TestEffect

data class Shown(val text: String) : TestEffect

typealias TestStore = ScreenStore<TestState, TestEffect>

typealias TestHandler = EventHandler<*, TestState, TestEffect>

fun TestStore.log(entry: String) = update { it.copy(log = it.log + entry) }

/** The four ready-made strategies, each for the `Run` event alone. */
val readyMadeStrategies: List<DispatchStrategy<Run>> = listOf(
    DispatchStrategy.Concurrent,
    DispatchStrategy.Latest<Run>(),
    DispatchStrategy.Sequential<Run>(),
    DispatchStrategy.DropIfAlreadyRunning<Run>(),
)

/** Lets a test end the runs of a [StepHandler] one at a time, also when their events are equal. */
class Gates {

    private val permits = mutableMapOf<TestEvent, Channel<Unit>>()

    fun of(event: TestEvent): Channel<Unit> = permits.getOrPut(event) { Channel(Channel.UNLIMITED) }

    /** Lets one run of [event] end. */
    fun open(event: TestEvent) {
        of(event).trySend(Unit)
    }
}

/** Logs how each run of its event starts and ends, and waits in between until [gates] lets it go on. */
class StepHandler<E : TestEvent>(
    override val eventType: KClass<E>,
    override val strategy: DispatchStrategy<E>,
    private val gates: Gates,
) : EventHandler<E, TestState, TestEffect> {

    override suspend fun handle(event: E, store: TestStore) {
        store.log("start $event")

        try {
            gates.of(event).receive()
            store.log("end $event")
        } catch (cancellation: CancellationException) {
            store.log("cancelled $event")

            throw cancellation
        }
    }
}

/** Logs the start and the end of each run and waits for [gate] in between, so a test decides when runs overlap. */
class RunHandler(override val strategy: DispatchStrategy<Run>) : EventHandler<Run, TestState, TestEffect> {

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
    override val strategy = DispatchStrategy.Latest<Observe>()

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
    override val strategy = DispatchStrategy.Latest<VisibilityChanged>()

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

class BoomHandler(override val strategy: DispatchStrategy<Boom> = DispatchStrategy.Concurrent) :
    EventHandler<Boom, TestState, TestEffect> {

    override val eventType = Boom::class

    override suspend fun handle(event: Boom, store: TestStore): Unit = throw IllegalStateException("boom")
}
