package io.github.mradzinski.eventus.sample.ui

import co.touchlab.kermit.Logger
import io.github.mradzinski.eventus.RouterObserver
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.UiEvent

/**
 * The log of every router in the app. Each view model passes one to its router with the [tag] of its
 * screen, so the lines of the task list, the editor and the app bar can be told apart.
 *
 * Each report already reads as a sentence, so it is logged as it is. A handler that failed is logged as
 * an error, with its exception.
 */
class RouterLog(logger: Logger, tag: String) : RouterObserver<UiEvent> {

    private val logger = logger.withTag(tag)

    override fun observe(trace: RouterTrace<UiEvent>) {
        when (trace) {
            is RouterTrace.Failed -> logger.e(trace.error) { trace.toString() }

            is RouterTrace.Dispatched,
            is RouterTrace.Started,
            is RouterTrace.EffectEmitted,
            is RouterTrace.Completed,
            is RouterTrace.Cancelled,
            -> logger.d { trace.toString() }
        }
    }
}
