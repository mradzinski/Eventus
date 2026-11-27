package io.github.mradzinski.eventus.sample.taskeditor

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
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
@AssistedInject
class TaskEditorViewModel(
    @Assisted taskId: Long?,
    handlers: Set<TaskEditorHandler<*>>,
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

    /** Creates the view model for one task. Metro writes it; the route calls it with the task's id. */
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {

        fun create(@Assisted taskId: Long?): TaskEditorViewModel
    }
}
