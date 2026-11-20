package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnRefresh
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore

/** Fetches the tasks again. `DropIfBusy` ignores the button while a refresh is under way. */
internal class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> {

    override val eventType = OnRefresh::class
    override val strategy = DispatchStrategy.DropIfBusy

    override suspend fun handle(event: OnRefresh, store: TaskListStore) {
        store.update { it.copy(refreshing = true) }

        try {
            tasks.refresh()
        } finally {
            store.update { it.copy(refreshing = false) }
        }
    }
}
