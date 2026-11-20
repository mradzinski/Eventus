package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnScreenStarted
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState.Content

/** Keeps the list up to date for as long as the screen is visible. */
internal class OnScreenStartedHandler(private val tasks: TaskRepository) : TaskListHandler<OnScreenStarted> {

    override val eventType = OnScreenStarted::class
    override val strategy = DispatchStrategy.WhileStarted

    override suspend fun handle(event: OnScreenStarted, store: TaskListStore) {
        tasks
            .observeTasks()
            .collect { loaded ->
                store.update { it.copy(content = Content.Loaded(loaded)) }
            }
    }
}
