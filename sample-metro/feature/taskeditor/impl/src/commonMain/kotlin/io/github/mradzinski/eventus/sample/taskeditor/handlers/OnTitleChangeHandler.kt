package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnTitleChange
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.updateEditing

class OnTitleChangeHandler : TaskEditorHandler<OnTitleChange> {

    override val eventType = OnTitleChange::class

    override suspend fun handle(event: OnTitleChange, store: TaskEditorStore) {
        store.updateEditing { it.copy(title = event.title, titleMissing = false) }
    }
}
