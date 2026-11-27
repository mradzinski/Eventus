package io.github.mradzinski.eventus.sample.tasklist

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import io.github.mradzinski.eventus.sample.ui.RouterLog
import kotlinx.coroutines.CoroutineScope

/**
 * Holds no logic: it gives the task list's router to the UI and ends it with the view model. What each
 * event does is in its handler, and what a failure does is in [TaskListErrorHandler].
 *
 * It sends [TaskListEvent.OnScreenOpened] as soon as it is created, which starts following the tasks. That
 * lasts as long as the view model does, so the list is up to date when the user comes back to it.
 *
 * What the router does with each event goes to the app's log, under the tag `TaskList`.
 */
internal class TaskListViewModel(
    handlers: Collection<TaskListHandler<*>>,
    errorHandler: TaskListErrorHandler,
    logger: Logger,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
        observer = RouterLog(logger, tag = "TaskList"),
    ) {

    init {
        onEvent(TaskListEvent.OnScreenOpened)
    }
}
