package io.github.mradzinski.eventus.test.internal

import io.github.mradzinski.eventus.Dispatch
import io.github.mradzinski.eventus.RouterObserver
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.UiEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Keeps what a router tells about itself during one test.
 *
 * An event handler may do part of its work on a thread of its own, so a report or a failure can arrive
 * while the test reads. Each list is therefore replaced as a whole and never changed in place.
 */
internal class Recorder<E : UiEvent> : RouterObserver<E> {

    private val recordedReports = MutableStateFlow(emptyList<RouterTrace<E>>())
    private val recordedFailures = MutableStateFlow(emptyList<Throwable>())

    /** The events that were accepted and have not ended yet, by their number. */
    private val active = MutableStateFlow(emptyMap<Long, E>())

    val reports: List<RouterTrace<E>> get() = recordedReports.value
    val failures: List<Throwable> get() = recordedFailures.value

    /** The events still being handled, written the way a report starts: "#1 OnSaveClick". */
    val stillActive: List<String> get() = active.value.map { (number, event) -> "#$number $event" }

    override fun observe(trace: RouterTrace<E>) {
        recordedReports.update { it + trace }

        when (trace) {
            is RouterTrace.Dispatched -> {
                if (trace.dispatch !is Dispatch.Ignore) active.update { it + (trace.number to trace.event) }
            }

            is RouterTrace.Started -> Unit

            is RouterTrace.EffectEmitted -> Unit

            is RouterTrace.Completed -> active.update { it - trace.number }

            is RouterTrace.Failed -> active.update { it - trace.number }

            is RouterTrace.Cancelled -> active.update { it - trace.number }
        }
    }

    fun failed(error: Throwable) = recordedFailures.update { it + error }

    /** Suspends until every event that was accepted has ended. */
    suspend fun awaitIdle() {
        active.first { it.isEmpty() }
    }
}
