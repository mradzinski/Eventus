package io.github.mradzinski.eventus.sample.tasklist

import io.github.mradzinski.eventus.koin.EventusKoinModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Every event handler and error handler of this Gradle module, as factories. The Eventus processor writes
 * it on each target from the handler classes it finds, so adding a handler class is all it takes to
 * register one. Each handler is bound to [TaskListHandler], which is what the view model below asks for.
 */
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

/**
 * All the app needs to know of the task list: the one Koin module of this feature.
 *
 * The view model asks for every [TaskListHandler], which gives the handlers of this screen and no other.
 * Asking for the library's generic `EventHandler` would not work: Koin looks definitions up by class,
 * without type arguments, so it would return the handlers of every screen.
 */
val taskListModule: Module = module {
    includes(handlersModule)

    viewModel {
        TaskListViewModel(
            handlers = getAll<TaskListHandler<*>>(),
            errorHandler = get(),
            logger = get(),
        )
    }
}
