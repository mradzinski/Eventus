package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnSearchChange
import io.github.mradzinski.eventus.sample.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.tasklist.TaskListStore
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Shows each keystroke at once and filters the list once the user pauses. `Latest` cancels the wait of
 * the previous keystroke, so only the last one filters.
 */
internal class OnSearchChangeHandler : TaskListHandler<OnSearchChange> {

    override val eventType = OnSearchChange::class
    override val strategy = DispatchStrategy.Latest<OnSearchChange>()

    override suspend fun handle(event: OnSearchChange, store: TaskListStore) {
        store.update { it.copy(query = event.query) }

        delay(SEARCH_DEBOUNCE)

        store.update { it.copy(appliedQuery = event.query.trim()) }
    }

    internal companion object {
        val SEARCH_DEBOUNCE = 300.milliseconds
    }
}
