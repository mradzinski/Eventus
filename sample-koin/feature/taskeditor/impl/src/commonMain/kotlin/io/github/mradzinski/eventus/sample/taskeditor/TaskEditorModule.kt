package io.github.mradzinski.eventus.sample.taskeditor

import io.github.mradzinski.eventus.koin.EventusKoinModule
import io.github.mradzinski.eventus.sample.taskeditor.api.TaskEditorKey
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Every event handler and error handler of this Gradle module, as factories. The Eventus processor writes
 * it on each target from the handler classes it finds. Each handler is bound to [TaskEditorHandler], which
 * is what the view model below asks for.
 */
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

/** All the app needs to know of the task editor: the one Koin module of this feature. */
val taskEditorModule: Module = module {
    includes(handlersModule)

    viewModel { (key: TaskEditorKey) ->
        TaskEditorViewModel(
            taskId = key.taskId,
            handlers = getAll<TaskEditorHandler<*>>(),
            errorHandler = get(),
            logger = get(),
        )
    }
}
