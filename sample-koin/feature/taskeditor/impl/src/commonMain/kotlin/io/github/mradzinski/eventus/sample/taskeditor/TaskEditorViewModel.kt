package io.github.mradzinski.eventus.sample.taskeditor

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.routerScope
import io.github.mradzinski.eventus.sample.ui.RouterLog
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
    logger: Logger,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskEditorEvent, TaskEditorUiState, TaskEditorEffect> by EventRouter(
        scope = scope,
        initialState = TaskEditorUiState.Loading,
        handlers = handlers,
        errorHandler = errorHandler,
        observer = RouterLog(logger, tag = "TaskEditor"),
    ) {

    init {
        onEvent(TaskEditorEvent.OnScreenOpened(taskId))
    }
}
