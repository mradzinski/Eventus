package io.github.mradzinski.eventus.sample.presentation.tasklist

import androidx.lifecycle.ViewModel
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import kotlinx.coroutines.CoroutineScope

/**
 * Holds no logic: it gives the task list's router to the UI and ends it with the view model. What each
 * event does is in its handler, and what a failure does is in [TaskListErrorHandler].
 */
internal class TaskListViewModel(
    handlers: Collection<TaskListHandler<*>>,
    errorHandler: TaskListErrorHandler,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
    )
