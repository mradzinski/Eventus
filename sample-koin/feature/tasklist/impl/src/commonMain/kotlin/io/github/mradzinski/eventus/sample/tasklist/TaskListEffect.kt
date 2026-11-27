package io.github.mradzinski.eventus.sample.tasklist

import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.sample.ui.FailureReason

sealed interface TaskListEffect : UiEffect {

    /** Go to the editor, for the task with [taskId] or for a new task when it is null. */
    data class OpenEditor(val taskId: Long?) : TaskListEffect

    data class ShowFailure(val reason: FailureReason) : TaskListEffect
}
