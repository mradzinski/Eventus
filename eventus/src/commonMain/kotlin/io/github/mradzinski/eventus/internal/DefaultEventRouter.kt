package io.github.mradzinski.eventus.internal

import io.github.mradzinski.eventus.Dispatch
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
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
     * Guards [active] and [version]. It is only ever held to read or change them: no strategy, no
     * handler, no error handler and no completion callback runs while it is held.
     */
    private val lock = SynchronizedObject()

    /** Every run that is running or waiting to run, oldest first. A run leaves the list when it ends. */
    private val active = mutableListOf<ActiveRun<E>>()

    /** Changes with every change of [active], so a decision made on an older list can be told apart. */
    private var version = 0

    /** How many runs the router is keeping track of. Only for the test that checks nothing is retained. */
    internal val trackedRunCount: Int
        get() = synchronized(lock) { active.size }

    override fun onEvent(event: E) {
        val handler = handlerFor(event)

        // A strategy is the app's code, so it is asked outside the lock, and asked again when the
        // active events changed while it was deciding.
        while (true) {
            val seen = synchronized(lock) { Snapshot(events = active.map { it.event }, version = version) }

            val dispatch = decide(event, handler, seen.events) ?: return

            if (carryOut(event, handler, dispatch, seen.version)) return
        }
    }

    private fun handlerFor(event: E): EventHandler<E, S, F> {
        // The handler was registered under the event's own class, so it accepts the event.
        @Suppress("UNCHECKED_CAST")
        val handler = handlers[event::class] as EventHandler<E, S, F>?

        return checkNotNull(handler) { "No handler registered for ${event::class.simpleName}" }
    }

    /**
     * Asks the strategy of [handler] what to do with [event]. A strategy that throws is a mistake in
     * the code, not a failure of the work: the event does not run, and the failure goes to the scope
     * as an uncaught error, never to the error handler.
     */
    private fun decide(
        event: E,
        handler: EventHandler<E, S, F>,
        active: List<UiEvent>,
    ): Dispatch? {
        // Every exception is caught to be thrown again where it does not land in the caller of onEvent.
        @Suppress("TooGenericExceptionCaught")
        return try {
            handler.strategy.decide(event, active)
        } catch (error: Exception) {
            val message = "The strategy of ${handler::class.simpleName} failed for ${event::class.simpleName}"

            scope.launch { throw IllegalStateException(message, error) }

            null
        }
    }

    /**
     * Does what [dispatch] says, unless the active events changed since the strategy saw them.
     *
     * @return False when they changed, and the strategy must be asked again.
     */
    private fun carryOut(
        event: E,
        handler: EventHandler<E, S, F>,
        dispatch: Dispatch,
        version: Int,
    ): Boolean {
        var replaced = emptyList<Job>()

        val run = synchronized(lock) {
            if (this.version != version) return false

            val after = when (dispatch) {
                is Dispatch.Ignore -> return true

                is Dispatch.Run -> emptyList()

                is Dispatch.WaitFor -> jobsOf(dispatch.events)

                is Dispatch.Replace -> {
                    replaced = jobsOf(dispatch.events)

                    emptyList()
                }
            }

            newRun(event, handler, after).also { run ->
                active += ActiveRun(event, run)
                this.version++
            }
        }

        run.invokeOnCompletion { forget(run) }
        replaced.forEach { it.cancel() }
        run.start()

        return true
    }

    /** The runs of the active events that are equal to one of [events]. Called with the lock held. */
    private fun jobsOf(events: List<UiEvent>): List<Job> {
        val named = events.toSet()

        return active
            .filter { it.event in named }
            .map { it.job }
    }

    /**
     * Creates the run of [event] without starting it, so that no handler code runs while the lock is
     * held. It first waits for the runs in [after]; `join` returns however they ended.
     */
    private fun newRun(
        event: E,
        handler: EventHandler<E, S, F>,
        after: List<Job>,
    ): Job = scope.launch(context = errorContext(event), start = CoroutineStart.LAZY) {
        after.joinAll()
        handler.handle(event, store)
    }

    private fun errorContext(event: E): CoroutineContext {
        val errorHandler = errorHandler ?: return EmptyCoroutineContext

        return CoroutineExceptionHandler { _, error -> errorHandler.handle(event, error, store) }
    }

    private fun forget(run: Job) {
        synchronized(lock) {
            active.removeAll { it.job === run }
            version++
        }
    }

    private class ActiveRun<E>(val event: E, val job: Job)

    private class Snapshot(val events: List<UiEvent>, val version: Int)
}
