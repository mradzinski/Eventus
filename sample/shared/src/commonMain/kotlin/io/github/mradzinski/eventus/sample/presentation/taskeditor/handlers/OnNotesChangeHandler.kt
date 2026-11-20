package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnNotesChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.updateEditing

internal class OnNotesChangeHandler : TaskEditorHandler<OnNotesChange> {

    override val eventType = OnNotesChange::class

    override suspend fun handle(event: OnNotesChange, store: TaskEditorStore) {
        store.updateEditing { it.copy(notes = event.notes) }
    }
}
