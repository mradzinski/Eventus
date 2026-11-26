package io.github.mradzinski.eventus.internal

import io.github.mradzinski.eventus.Dispatch
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.RouterObserver
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The router behind the public `EventRouter` function. Its behavior is documented there. */
internal class DefaultEventRouter<E : UiEvent, S : UiState, F : UiEffect>(
    scope: CoroutineScope,
    initialState: S,
    handlers: Collection<EventHandler<*, S, F>>,
    private val errorHandler: ErrorHandler<E, S, F>?,
    observer: RouterObserver<E>? = null,
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
     * Guards [active], [version], [lastNumber] and the queue of [reports]. It is only ever held to
     * read or change them: no strategy, no handler, no error handler, no observer and no completion
     * callback runs while it is held.
     */
    private val lock = SynchronizedObject()

    /** Every run that is running or waiting to run, oldest first. A run leaves the list when it ends. */
    private val active = mutableListOf<ActiveRun<E>>()

    /** Changes with every change of [active], so a decision made on an older list can be told apart. */
    private var version = 0

    /** The number given to the last event that was dispatched. Only counted for an observer. */
    private var lastNumber = 0L

    /** Null without an observer: the router then numbers nothing and builds no report. */
    private val reports: ReportQueue<E>? = observer?.let {
        ReportQueue(observer = it, lock = lock, onFailure = ::failObserver)
    }

    /** How many runs the router is keeping track of. Only for the test that checks nothing is retained. */
    internal val trackedRunCount: Int
        get() = synchronized(lock) { active.size }

    /**
     * Dispatches an event dispatched with [ScreenStore.dispatch]. The store does not know the screen's event
     * type, so an event of another screen can arrive here; it has no handler and fails as any such
     * event does.
     */
    @Suppress("UNCHECKED_CAST")
    private val onDispatch: (UiEvent) -> Job = { event -> dispatch(event as E) }

    /** The store every event handler receives when there is no observer. */
    private val routed = store.routedBy(onDispatch = onDispatch)

    /** A job that has already ended, returned for an event that was ignored. */
    private val notRun: Job = Job().also { it.complete() }

    override fun onEvent(event: E) {
        dispatch(event)
    }

    private fun dispatch(event: E): Job {
        val handler = handlerFor(event)

        // A strategy is the app's code, so it is asked outside the lock, and asked again when the
        // active events changed while it was deciding.
        while (true) {
            val seen = synchronized(lock) { Snapshot(events = active.map { it.event }, version = version) }

            val dispatch = decide(event, handler, seen.events) ?: return notRun

            carryOut(event, handler, dispatch, seen.version)?.let { return it }
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
     * @return The job of the event, which has already ended for an ignored one. Null when the active
     * events changed, and the strategy must be asked again.
     */
    private fun carryOut(
        event: E,
        handler: EventHandler<E, S, F>,
        dispatch: Dispatch,
        version: Int,
    ): Job? {
        var named = emptyList<ActiveRun<E>>()
        var batch: ArrayList<RouterTrace<E>>? = null

        val run = synchronized(lock) {
            if (this.version != version) return null

            named = runsNamedBy(dispatch)

            // Numbered and queued under the lock, so decisions are reported in the order they were made.
            val number = if (reports == null) 0 else ++lastNumber

            batch = reports?.queue(RouterTrace.Dispatched(event, number, dispatch, named.map { it.number }))

            if (dispatch is Dispatch.Ignore) return@synchronized null
            if (dispatch is Dispatch.Replace) named.forEach { it.replacedBy = it.replacedBy ?: number }

            ActiveRun(event, number).also { run ->
                run.job = newRun(
                    run = run,
                    handler = handler,
                    after = if (dispatch is Dispatch.WaitFor) named else emptyList(),
                )

                active += run

                this.version++
            }
        }

        reports?.deliver(batch)

        if (run == null) return notRun

        run.job.invokeOnCompletion { cause -> end(run, cause) }

        if (dispatch is Dispatch.Replace) named.forEach { it.job.cancel() }

        run.job.start()

        return run.job
    }

    /** The active runs of the events that [dispatch] names, by equality. Called with the lock held. */
    private fun runsNamedBy(dispatch: Dispatch): List<ActiveRun<E>> {
        val events = when (dispatch) {
            is Dispatch.Run -> return emptyList()
            is Dispatch.Ignore -> return emptyList()
            is Dispatch.WaitFor -> dispatch.events
            is Dispatch.Replace -> dispatch.events
        }

        val named = events.toSet()

        return active.filter { it.event in named }
    }

    /**
     * Creates the job of [run] without starting it, so that no handler code runs while the lock is
     * held. It first waits for the runs in [after]; `join` returns however they ended.
     */
    private fun newRun(
        run: ActiveRun<E>,
        handler: EventHandler<E, S, F>,
        after: List<ActiveRun<E>>,
    ): Job {
        val store = storeOf(run)

        return scope.launch(context = errorContext(run), start = CoroutineStart.LAZY) {
            after.forEach { it.job.join() }

            reports?.let { reports ->
                reports.deliver(
                    synchronized(lock) {
                        reports.queue(RouterTrace.Started(run.event, run.number))
                    },
                )
            }

            try {
                handler.handle(run.event, store)
            } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                // Every failure of an event handler is the error handler's to see, whatever its type.
                if (error is CancellationException || errorHandler == null) throw error

                errorHandler.handle(run.event, error, store)
                run.handled = error

                throw error
            }
        }
    }

    /**
     * The store that the handler of [run] and the error handler receive. It dispatches events to this
     * router. With an observer each run gets its own, so each effect can be reported with the event it
     * came from.
     */
    private fun storeOf(run: ActiveRun<E>): ScreenStore<S, F> {
        val reports = reports ?: return routed

        return store.routedBy(onDispatch = onDispatch) { effect ->
            val report = RouterTrace.EffectEmitted(run.event, run.number, effect)

            reports.deliver(
                synchronized(lock) { reports.queue(report) },
            )
        }
    }

    /**
     * Without an error handler a failure goes to the exception handler of the scope. With one, the
     * failure it has handled ends here. Any other failure of [run] came from the error handler itself,
     * which is a mistake in the code: it fails in the scope as an uncaught error.
     */
    private fun errorContext(run: ActiveRun<E>): CoroutineContext {
        if (errorHandler == null) return EmptyCoroutineContext

        return CoroutineExceptionHandler { _, error ->
            if (error === run.handled) return@CoroutineExceptionHandler

            scope.launch { throw IllegalStateException("The error handler failed for: ${run.event}", error) }
        }
    }

    /** Forgets [run] and reports how it ended, under one lock so that the two cannot be told apart. */
    private fun end(run: ActiveRun<E>, cause: Throwable?) {
        val batch = synchronized(lock) {
            active.remove(run)
            version++

            reports?.queue(run.ended(cause, scopeIsActive = scope.isActive))
        }

        reports?.deliver(batch)
    }

    /**
     * An observer that throws is a mistake in the code, like a strategy that throws: it fails in the
     * scope as an uncaught error. Once the scope is cancelled the failure is dropped.
     */
    private fun failObserver(trace: RouterTrace<E>, error: Exception) {
        scope.launch { throw IllegalStateException("The observer failed for: $trace", error) }
    }

    /** A run that is running or waiting. Its [job] is set, and [replacedBy] changed, under the lock. */
    private class ActiveRun<E : UiEvent>(val event: E, val number: Long) {

        lateinit var job: Job

        /** The number of the first event whose strategy replaced this run, if any. */
        var replacedBy: Long? = null

        /** The failure of this run that the error handler has handled, set before the job ends. */
        var handled: Throwable? = null

        /** The report of how this run ended, from the [cause] its job completed with. */
        fun ended(cause: Throwable?, scopeIsActive: Boolean): RouterTrace<E> = when (cause) {
            null -> RouterTrace.Completed(event, number)
            is CancellationException -> RouterTrace.Cancelled(event, number, reason(scopeIsActive))
            else -> RouterTrace.Failed(event, number, cause)
        }

        private fun reason(scopeIsActive: Boolean): RouterTrace.Cancelled.Reason {
            val replacedBy = replacedBy

            return when {
                replacedBy != null -> RouterTrace.Cancelled.Reason.ReplacedBy(replacedBy)
                scopeIsActive -> RouterTrace.Cancelled.Reason.ByHandler
                else -> RouterTrace.Cancelled.Reason.ScopeCancelled
            }
        }
    }

    private class Snapshot(val events: List<UiEvent>, val version: Int)
}
