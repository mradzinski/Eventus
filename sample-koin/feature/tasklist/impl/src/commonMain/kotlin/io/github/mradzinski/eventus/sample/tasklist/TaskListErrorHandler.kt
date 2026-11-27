package io.github.mradzinski.eventus.sample.tasklist

import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.sample.ui.toFailureReason

/**
 * What the task list does when one of its handlers fails: it tells the user why, once. This is the only
 * place of the screen that reads an exception, and where a real app would also log it.
 */
class TaskListErrorHandler : ErrorHandler<TaskListEvent, TaskListUiState, TaskListEffect> {

    override suspend fun handle(
        event: TaskListEvent,
        error: Throwable,
        store: TaskListStore,
    ) {
        store.emit(TaskListEffect.ShowFailure(error.toFailureReason()))
    }
}
