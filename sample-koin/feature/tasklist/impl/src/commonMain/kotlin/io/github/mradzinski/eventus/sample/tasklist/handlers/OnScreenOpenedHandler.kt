package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.tasklist.TaskListUiState.Content

/**
 * Keeps the list up to date. It never returns by itself, so it runs until the view model is cleared.
 * `Latest` means that sending the event again would start over, not observe twice.
 */
internal class OnScreenOpenedHandler(private val tasks: TaskRepository) : TaskListHandler<OnScreenOpened> {

    override val eventType = OnScreenOpened::class
    override val strategy = DispatchStrategy.Latest<OnScreenOpened>()

    override suspend fun handle(event: OnScreenOpened, store: TaskListStore) {
        tasks
            .observeTasks()
            .collect { loaded ->
                store.update { it.copy(content = Content.Loaded(loaded)) }
            }
    }
}
