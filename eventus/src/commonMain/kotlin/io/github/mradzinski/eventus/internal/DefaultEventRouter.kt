package io.github.mradzinski.eventus.internal

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The router behind the public `EventRouter` function. Its behavior is documented there. */
internal class DefaultEventRouter<E : UiEvent, S : UiState, F : UiEffect>(
    scope: CoroutineScope,
    initialState: S,
    handlers: Collection<EventHandler<*, S, F>>,
    private val errorHandler: ErrorHandler<E, S, F>?,
) : EventRouter<E, S, F> {

    private val store = ScreenStore<S, F>(initialState)

    override val state: StateFlow<S>
        get() = store.state

    override val effects: Flow<F>
        get() = store.effects

    private val handlers: Map<KClass<*>, EventHandler<*, S, F>> = handlers
        .groupBy { it.eventType }
        .mapValues { (type, registered) ->
            check(registered.size == 1) {
                "Multiple handlers registered for ${type.simpleName}: ${registered.map { it::class.simpleName }}"
            }

            registered.single()
        }

    /**
     * Where every handler runs. The supervisor job makes each run fail on its own, whatever job the
     * caller's scope has, and is what lets a run's exception reach the error handler.
     */
    private val scope = CoroutineScope(scope.coroutineContext + SupervisorJob(parent = scope.coroutineContext[Job]))

    /**
     * Guards the two maps below. It is only ever held for a map operation: no handler, no error handler
     * and no completion callback runs while it is held.
     */
    private val lock = SynchronizedObject()

    /** The active run of each event type handled with `Latest` or `DropIfBusy`. */
    private val runs = mutableMapOf<KClass<*>, Job>()

    /** The queue of each event type handled with `Sequential`. An entry is never removed. */
    private val queues = mutableMapOf<KClass<*>, SequentialQueue<E>>()

    /** How many runs the router is keeping track of. Only for the test that checks nothing is retained. */
    internal val trackedRunCount: Int
        get() = synchronized(lock) { runs.size }

    override fun onEvent(event: E) {
        val handler = handlerFor(event)

        when (handler.strategy) {
            DispatchStrategy.Concurrent -> newRun(event, handler).start()
            DispatchStrategy.Latest -> replaceRun(event, handler)
            DispatchStrategy.Sequential -> enqueue(event, handler)
            DispatchStrategy.DropIfBusy -> runUnlessBusy(event, handler)
        }
    }

    private fun handlerFor(event: E): EventHandler<E, S, F> {
        // The handler was registered under the event's own class, so it accepts the event.
        @Suppress("UNCHECKED_CAST")
        val handler = handlers[event::class] as EventHandler<E, S, F>?

        return checkNotNull(handler) { "No handler registered for ${event::class.simpleName}" }
    }

    /**
     * Creates the coroutine that handles [event], without starting it. Every run is created this way,
     * so that it can be put in a map before any of the handler's code has run, also on a dispatcher
     * that would otherwise run the handler inside `launch`.
     */
    private fun newRun(event: E, handler: EventHandler<E, S, F>): Job =
        scope.launch(context = errorContext(event), start = CoroutineStart.LAZY) {
            handler.handle(event, store)
        }

    private fun errorContext(event: E): CoroutineContext {
        val errorHandler = errorHandler ?: return EmptyCoroutineContext

        return CoroutineExceptionHandler { _, error -> errorHandler.handle(event, error, store) }
    }

    /** Starts a run for [event] in place of the active one of its type, which is cancelled. */
    private fun replaceRun(event: E, handler: EventHandler<E, S, F>) {
        val type = event::class
        val run = newRun(event, handler)
        val previous = synchronized(lock) { runs.put(type, run) }

        run.forgetWhenDone(type)
        previous?.cancel()
        run.start()
    }

    private fun runUnlessBusy(event: E, handler: EventHandler<E, S, F>) {
        val type = event::class

        val run = synchronized(lock) {
            // A run that was created but has not started yet counts as busy too.
            if (runs[type]?.isCompleted == false) return

            newRun(event, handler).also { runs[type] = it }
        }

        run.forgetWhenDone(type)
        run.start()
    }

    private fun enqueue(event: E, handler: EventHandler<E, S, F>) {
        val queue = synchronized(lock) {
            queues.getOrPut(event::class) { newQueue(handler) }
        }

        queue.events.trySend(event)
        queue.worker.start()
    }

    /**
     * Creates the queue of one event type and the worker that empties it, one event at a time. Each
     * event still runs in a coroutine of its own, next to the worker and not inside it, so a run that
     * fails ends alone and the worker goes on to the next event.
     */
    private fun newQueue(handler: EventHandler<E, S, F>): SequentialQueue<E> {
        val events = Channel<E>(Channel.UNLIMITED)

        val worker = scope.launch(start = CoroutineStart.LAZY) {
            for (event in events) {
                val run = newRun(event, handler)

                run.start()
                run.join()
            }
        }

        // Once the scope is gone nothing will read the queue, so it stops taking events.
        worker.invokeOnCompletion { events.close() }

        return SequentialQueue(events, worker)
    }

    /** Stops tracking this run when it ends, unless a newer run has taken its place. */
    private fun Job.forgetWhenDone(type: KClass<*>) {
        invokeOnCompletion {
            synchronized(lock) {
                if (runs[type] === this) runs.remove(type)
            }
        }
    }

    private class SequentialQueue<E>(val events: Channel<E>, val worker: Job)
}
