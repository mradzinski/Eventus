package io.github.mradzinski.eventus.sample.presentation.taskeditor

import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason

/** The editor shows exactly one of these at a time, so it cannot be loading and editing at once. */
internal sealed interface TaskEditorUiState : UiState {

    data object Loading : TaskEditorUiState

    /**
     * @property taskId The task being edited, or null while creating a new one.
     * @property titleMissing True after an attempt to save without a title, until the title changes.
     */
    data class Editing(
        val taskId: Long?,
        val title: String = "",
        val notes: String = "",
        val done: Boolean = false,
        val titleMissing: Boolean = false,
        val saving: Boolean = false,
    ) : TaskEditorUiState

    /** The task could not be loaded. It is state, not an effect: it stays on screen until the user retries. */
    data class LoadFailed(val taskId: Long, val reason: FailureReason) : TaskEditorUiState
}
