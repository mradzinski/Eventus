package io.github.mradzinski.eventus.sample.presentation.taskeditor

import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason

internal sealed interface TaskEditorEffect : UiEffect {

    /** Leave the editor: the task was saved, or the user went back. */
    data object Close : TaskEditorEffect

    data class ShowFailure(val reason: FailureReason) : TaskEditorEffect
}
