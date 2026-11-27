package io.github.mradzinski.eventus.sample.tasklist

import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStore

/**
 * A handler of the task list. Each screen declares one interface like this for two reasons: a handler
 * then names only its event, and dependency injection can ask for "every handler of this screen" by
 * this type, which the generic [EventHandler] is too broad for at run time.
 */
internal interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

internal typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>
