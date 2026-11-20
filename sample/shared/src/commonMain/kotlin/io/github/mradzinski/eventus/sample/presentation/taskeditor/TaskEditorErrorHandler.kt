package io.github.mradzinski.eventus.sample.presentation.taskeditor

import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.sample.presentation.common.toFailureReason

/**
 * What the editor does when one of its handlers fails: it tells the user why, once, and leaves the form
 * as it is. A task that cannot be loaded is not handled here: that has to stay on screen with a retry
 * button, so the handlers that load turn it into state themselves.
 */
internal class TaskEditorErrorHandler : ErrorHandler<TaskEditorEvent, TaskEditorUiState, TaskEditorEffect> {

    override fun handle(
        event: TaskEditorEvent,
        error: Throwable,
        store: TaskEditorStore,
    ) {
        store.emit(TaskEditorEffect.ShowFailure(error.toFailureReason()))
    }
}
