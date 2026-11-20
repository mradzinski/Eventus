package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState

/** Shows an empty form for a new task, or loads the task to edit. */
internal class OnScreenOpenedHandler(private val tasks: TaskRepository) : TaskEditorHandler<OnScreenOpened> {

    override val eventType = OnScreenOpened::class

    override suspend fun handle(event: OnScreenOpened, store: TaskEditorStore) {
        val taskId = event.taskId

        if (taskId == null) {
            store.update { TaskEditorUiState.Editing(taskId = null) }

            return
        }

        tasks.loadInto(store, taskId)
    }
}
