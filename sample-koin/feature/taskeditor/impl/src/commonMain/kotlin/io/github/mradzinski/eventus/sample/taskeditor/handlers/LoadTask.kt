package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.domain.TaskException
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.sample.ui.toFailureReason

/**
 * Loads the task with [taskId] into the form. Shared by the two handlers that load: opening the screen
 * and retrying. A failed load becomes state here instead of being thrown, because it has to stay on
 * screen with a retry button.
 */
suspend fun TaskRepository.loadInto(store: TaskEditorStore, taskId: Long) {
    store.update { TaskEditorUiState.Loading }

    val loaded = try {
        val task = task(taskId)

        TaskEditorUiState.Editing(
            taskId = task.id,
            title = task.title,
            notes = task.notes,
            done = task.done,
        )
    } catch (error: TaskException) {
        TaskEditorUiState.LoadFailed(taskId = taskId, reason = error.toFailureReason())
    }

    store.update { loaded }
}
