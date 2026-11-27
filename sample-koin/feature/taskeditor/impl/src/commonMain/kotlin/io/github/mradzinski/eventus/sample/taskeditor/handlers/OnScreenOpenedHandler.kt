package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState

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
