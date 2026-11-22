package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import io.github.mradzinski.eventus.koin.EventusKoinModule
import io.github.mradzinski.eventus.sample.data.Connectivity
import io.github.mradzinski.eventus.sample.data.InMemoryConnectivity
import io.github.mradzinski.eventus.sample.data.InMemoryTaskRepository
import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.navigation.TaskEditorKey
import io.github.mradzinski.eventus.sample.presentation.app.AppHandler
import io.github.mradzinski.eventus.sample.presentation.app.AppViewModel
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorViewModel
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The app's logger: one configuration that never changes, writing where [appLogWriter] says for each
 * platform. Every view model takes it and gives it to the log of
 * its router, with a tag of its own.
 */
internal val loggingModule: Module = module {
    single {
        Logger(
            config = StaticConfig(logWriterList = listOf(appLogWriter())),
            tag = "EventusSample",
        )
    }
}

internal val dataModule: Module = module {
    single<Connectivity> { InMemoryConnectivity() }
    single<TaskRepository> { InMemoryTaskRepository(connectivity = get()) }
}

/**
 * Every event handler and error handler of the app, as factories. The Eventus processor writes it on each
 * target from the handler classes it finds, so adding a handler class is all it takes to register one.
 * Each handler is bound to the handler interface of its screen, which is what the view models below ask for.
 */
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

/**
 * The view model asks for every [TaskListHandler], which gives the handlers of this screen and no other.
 * Asking for the library's generic `EventHandler` would not work: Koin looks definitions up by class,
 * without type arguments, so it would return the handlers of every screen.
 */
internal val taskListModule: Module = module {
    viewModel {
        TaskListViewModel(
            handlers = getAll<TaskListHandler<*>>(),
            errorHandler = get(),
            logger = get(),
        )
    }
}

internal val appShellModule: Module = module {
    viewModel {
        AppViewModel(
            handlers = getAll<AppHandler<*>>(),
            logger = get(),
        )
    }
}

internal val taskEditorModule: Module = module {
    viewModel { (key: TaskEditorKey) ->
        TaskEditorViewModel(
            taskId = key.taskId,
            handlers = getAll<TaskEditorHandler<*>>(),
            errorHandler = get(),
            logger = get(),
        )
    }
}

internal val sampleModules: List<Module> = listOf(
    loggingModule,
    dataModule,
    handlersModule,
    appShellModule,
    taskListModule,
    taskEditorModule,
)
