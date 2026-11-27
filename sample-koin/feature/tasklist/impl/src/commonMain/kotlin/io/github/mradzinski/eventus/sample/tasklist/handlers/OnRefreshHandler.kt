package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnRefresh
import io.github.mradzinski.eventus.sample.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.tasklist.TaskListStore

/** Fetches the tasks again. `DropIfAlreadyRunning` ignores the button while a refresh is under way. */
class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> {

    override val eventType = OnRefresh::class
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnRefresh>()

    override suspend fun handle(event: OnRefresh, store: TaskListStore) {
        store.update { it.copy(refreshing = true) }

        try {
            tasks.refresh()
        } finally {
            store.update { it.copy(refreshing = false) }
        }
    }
}
