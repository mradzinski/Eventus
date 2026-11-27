package io.github.mradzinski.eventus.sample.taskeditor

import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStore

/** A handler of the task editor. See `TaskListHandler` for why each screen declares one such interface. */
interface TaskEditorHandler<E : TaskEditorEvent> : EventHandler<E, TaskEditorUiState, TaskEditorEffect>

typealias TaskEditorStore = ScreenStore<TaskEditorUiState, TaskEditorEffect>

/** Changes the form. Does nothing while the editor shows something else, such as a failed load. */
fun TaskEditorStore.updateEditing(transform: (TaskEditorUiState.Editing) -> TaskEditorUiState.Editing) {
    update { state ->
        if (state is TaskEditorUiState.Editing) {
            transform(state)
        } else {
            state
        }
    }
}
