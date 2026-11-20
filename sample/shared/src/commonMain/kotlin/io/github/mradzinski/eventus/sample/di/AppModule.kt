package io.github.mradzinski.eventus.sample.di

import io.github.mradzinski.eventus.sample.data.Connectivity
import io.github.mradzinski.eventus.sample.data.InMemoryConnectivity
import io.github.mradzinski.eventus.sample.data.InMemoryTaskRepository
import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.navigation.TaskEditorKey
import io.github.mradzinski.eventus.sample.presentation.app.AppHandler
import io.github.mradzinski.eventus.sample.presentation.app.AppViewModel
import io.github.mradzinski.eventus.sample.presentation.app.handlers.OnAppStartedHandler
import io.github.mradzinski.eventus.sample.presentation.app.handlers.OnOfflineChangeHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorErrorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorViewModel
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnBackClickHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnDoneChangeHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnNotesChangeHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnRetryClickHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnSaveClickHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnScreenOpenedHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers.OnTitleChangeHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListErrorHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListViewModel
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnAddClickHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnRefreshHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnScreenStartedHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnSearchChangeHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnTaskClickHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnTaskDoneChangeHandler
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

internal val dataModule: Module = module {
    single<Connectivity> { InMemoryConnectivity() }
    single<TaskRepository> { InMemoryTaskRepository(connectivity = get()) }
}

/**
 * Each handler costs one line: Koin builds it from its constructor, and `bind` also registers it as a
 * handler of its screen. The view model then asks for every [TaskListHandler], which gives the handlers
 * of this screen and no other. Asking for the library's generic `EventHandler` would not work: Koin looks
 * definitions up by class, without type arguments, so it would return the handlers of every screen.
 *
 * Handlers hold no state of the screen, so `factory` is all they need.
 */
internal val taskListModule: Module = module {
    factoryOf(::OnScreenStartedHandler) bind TaskListHandler::class
    factoryOf(::OnSearchChangeHandler) bind TaskListHandler::class
    factoryOf(::OnRefreshHandler) bind TaskListHandler::class
    factoryOf(::OnTaskDoneChangeHandler) bind TaskListHandler::class
    factoryOf(::OnTaskClickHandler) bind TaskListHandler::class
    factoryOf(::OnAddClickHandler) bind TaskListHandler::class

    factoryOf(::TaskListErrorHandler)

    viewModel {
        TaskListViewModel(
            handlers = getAll<TaskListHandler<*>>(),
            errorHandler = get(),
        )
    }
}

internal val appShellModule: Module = module {
    factoryOf(::OnAppStartedHandler) bind AppHandler::class
    factoryOf(::OnOfflineChangeHandler) bind AppHandler::class

    viewModel { AppViewModel(handlers = getAll<AppHandler<*>>()) }
}

internal val taskEditorModule: Module = module {
    factoryOf(::OnScreenOpenedHandler) bind TaskEditorHandler::class
    factoryOf(::OnRetryClickHandler) bind TaskEditorHandler::class
    factoryOf(::OnTitleChangeHandler) bind TaskEditorHandler::class
    factoryOf(::OnNotesChangeHandler) bind TaskEditorHandler::class
    factoryOf(::OnDoneChangeHandler) bind TaskEditorHandler::class
    factoryOf(::OnSaveClickHandler) bind TaskEditorHandler::class
    factoryOf(::OnBackClickHandler) bind TaskEditorHandler::class
    factoryOf(::TaskEditorErrorHandler)

    viewModel { (key: TaskEditorKey) ->
        TaskEditorViewModel(
            taskId = key.taskId,
            handlers = getAll<TaskEditorHandler<*>>(),
            errorHandler = get(),
        )
    }
}

internal val sampleModules: List<Module> = listOf(dataModule, appShellModule, taskListModule, taskEditorModule)
