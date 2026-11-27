package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnNotesChange
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.updateEditing

class OnNotesChangeHandler : TaskEditorHandler<OnNotesChange> {

    override val eventType = OnNotesChange::class

    override suspend fun handle(event: OnNotesChange, store: TaskEditorStore) {
        store.updateEditing { it.copy(notes = event.notes) }
    }
}
