package io.github.mradzinski.eventus.sample.taskeditor

import io.github.mradzinski.eventus.UiEvent

/** Everything the task editor can tell its view model. Each event has one handler in `handlers`. */
internal sealed interface TaskEditorEvent : UiEvent {

    /** Sent once by the view model, with the id of the task to edit or null for a new task. */
    data class OnScreenOpened(val taskId: Long?) : TaskEditorEvent

    data object OnRetryClick : TaskEditorEvent

    data class OnTitleChange(val title: String) : TaskEditorEvent

    data class OnNotesChange(val notes: String) : TaskEditorEvent

    data class OnDoneChange(val done: Boolean) : TaskEditorEvent

    data object OnSaveClick : TaskEditorEvent

    data object OnBackClick : TaskEditorEvent
}
