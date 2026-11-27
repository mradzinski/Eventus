package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnSaveClick
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.sample.taskeditor.updateEditing

/**
 * Saves the form and leaves the editor. `DropIfAlreadyRunning` ignores a second tap while the save is under way.
 * A save that fails throws: the screen's error handler tells the user, and the form stays as it was.
 */
internal class OnSaveClickHandler(private val tasks: TaskRepository) : TaskEditorHandler<OnSaveClick> {

    override val eventType = OnSaveClick::class
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnSaveClick>()

    override suspend fun handle(event: OnSaveClick, store: TaskEditorStore) {
        val form = store.current as? TaskEditorUiState.Editing ?: return

        if (form.title.isBlank()) {
            store.updateEditing { it.copy(titleMissing = true) }

            return
        }

        store.updateEditing { it.copy(saving = true) }

        try {
            tasks.save(
                id = form.taskId,
                title = form.title.trim(),
                notes = form.notes.trim(),
            )

            store.emit(TaskEditorEffect.Close)
        } finally {
            store.updateEditing { it.copy(saving = false) }
        }
    }
}
