package io.github.mradzinski.eventus

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a coroutine scope for a view model and its [EventRouter].
 *
 * Handlers started in it run on the main thread, right away when the event is sent from the main
 * thread, and one of them failing does not cancel the others. On a platform that has no main
 * dispatcher, they run on whatever thread sends the event.
 *
 * Whoever creates the scope must cancel it. A view model does that by passing the scope to its
 * `ViewModel(scope)` constructor, which cancels it when the view model is cleared.
 */
public fun routerScope(): CoroutineScope {
    val dispatcher = try {
        Dispatchers.Main.immediate
    } catch (_: NotImplementedError) {
        // Native platforms without a main dispatcher.
        EmptyCoroutineContext
    } catch (_: IllegalStateException) {
        // The JVM without a module that provides the main dispatcher.
        EmptyCoroutineContext
    }

    return CoroutineScope(SupervisorJob() + dispatcher)
}
