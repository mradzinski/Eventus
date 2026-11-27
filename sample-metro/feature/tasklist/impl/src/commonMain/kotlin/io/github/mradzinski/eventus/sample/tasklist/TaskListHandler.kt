package io.github.mradzinski.eventus.sample.tasklist

import dev.zacsweers.metro.AppScope
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.metro.ContributesEventHandlers

/**
 * A handler of the task list. Each screen declares one interface like this for two reasons: a handler
 * then names only its event, and dependency injection can ask for "every handler of this screen" by
 * this type, which the generic [EventHandler] is too broad for at run time.
 */
@ContributesEventHandlers(AppScope::class)
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>
