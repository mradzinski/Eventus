package io.github.mradzinski.eventus.sample.presentation.tasklist

import io.github.mradzinski.eventus.ScreenStoppedEvent
import io.github.mradzinski.eventus.UiEvent

/** Everything the task list can tell its view model. Each event has one handler in `handlers`. */
internal sealed interface TaskListEvent : UiEvent {

    data object OnScreenStarted : TaskListEvent

    /** Has no handler: being a [ScreenStoppedEvent] is what stops the observation `OnScreenStarted` began. */
    data object OnScreenStopped : TaskListEvent, ScreenStoppedEvent

    data class OnSearchChange(val query: String) : TaskListEvent

    data object OnRefresh : TaskListEvent

    data class OnTaskDoneChange(val taskId: Long, val done: Boolean) : TaskListEvent

    data class OnTaskClick(val taskId: Long) : TaskListEvent

    data object OnAddClick : TaskListEvent
}
