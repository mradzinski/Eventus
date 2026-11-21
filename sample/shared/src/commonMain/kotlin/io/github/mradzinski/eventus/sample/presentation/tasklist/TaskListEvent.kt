package io.github.mradzinski.eventus.sample.presentation.tasklist

import io.github.mradzinski.eventus.UiEvent

/** Everything the task list can tell its view model. Each event has one handler in `handlers`. */
internal sealed interface TaskListEvent : UiEvent {

    /** Sent once, by the view model, when it is created. */
    data object OnScreenOpened : TaskListEvent

    data class OnSearchChange(val query: String) : TaskListEvent

    data object OnRefresh : TaskListEvent

    data class OnTaskDoneChange(val taskId: Long, val done: Boolean) : TaskListEvent

    data class OnTaskClick(val taskId: Long) : TaskListEvent

    data object OnAddClick : TaskListEvent
}
