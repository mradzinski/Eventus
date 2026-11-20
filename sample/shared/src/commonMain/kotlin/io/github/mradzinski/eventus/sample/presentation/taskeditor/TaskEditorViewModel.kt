package io.github.mradzinski.eventus.sample.presentation.taskeditor

import androidx.lifecycle.ViewModel
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import kotlinx.coroutines.CoroutineScope

/**
 * Gives the editor's router to the UI and starts loading the task as soon as it is created, so the load
 * is not repeated when the screen is drawn again.
 *
 * @param taskId The task to edit, or null to create one.
 */
internal class TaskEditorViewModel(
    taskId: Long?,
    handlers: Collection<TaskEditorHandler<*>>,
    errorHandler: TaskEditorErrorHandler,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskEditorEvent, TaskEditorUiState, TaskEditorEffect> by EventRouter(
        scope = scope,
        initialState = TaskEditorUiState.Loading,
        handlers = handlers,
        errorHandler = errorHandler,
    ) {

    init {
        onEvent(TaskEditorEvent.OnScreenOpened(taskId))
    }
}
