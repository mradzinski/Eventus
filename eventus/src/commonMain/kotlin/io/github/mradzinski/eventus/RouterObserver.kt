package io.github.mradzinski.eventus

/**
 * Is told what an [EventRouter] does with each event: what the strategy of its handler answered, when
 * its handler started, which effects it emitted, and how its run ended. Pass one to `EventRouter(...)`
 * to log a screen, to see why an event did nothing, or to check in a test what the router did.
 *
 * Every report prints as a sentence, so one line is a log:
 *
 * ```
 * observer = { println(it) }
 *
 * // #1 OnSaveClick runs
 * // #1 OnSaveClick started
 * // #2 OnDeleteClick is ignored
 * // #1 OnSaveClick emitted Close
 * // #1 OnSaveClick completed
 * ```
 *
 * Reports arrive in order and one at a time, also when events are sent from several threads, but not
 * always on the same thread. A report carries no time: a logger stamps its own lines.
 */
public fun interface RouterObserver<in E : UiEvent> {

    /**
     * Receives one report.
     *
     * The router calls this where the thing happened: on the thread that sent the event, inside a run,
     * or on the thread that ended a run. So it must be quick, and it must not wait for anything.
     *
     * If it throws, the event still runs. The failure is not passed to the screen's [ErrorHandler]: it
     * is a mistake in the code, so it fails in the router's scope as an uncaught error. Once that scope
     * is canceled there is nowhere left to raise it, and it is dropped.
     */
    public fun observe(trace: RouterTrace<E>)
}
