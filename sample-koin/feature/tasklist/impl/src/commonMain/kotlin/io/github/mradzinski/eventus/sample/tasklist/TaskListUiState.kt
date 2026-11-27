package io.github.mradzinski.eventus.sample.tasklist

import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.sample.domain.Task

/**
 * @property query What the search box shows, updated on every keystroke.
 * @property appliedQuery What the list is filtered by: [query] once the user has stopped typing.
 */
data class TaskListUiState(
    val query: String = "",
    val appliedQuery: String = "",
    val refreshing: Boolean = false,
    val content: Content = Content.Loading,
) : UiState {

    sealed interface Content {

        /** Nothing has arrived yet. Not the same as having no tasks. */
        data object Loading : Content

        data class Loaded(val tasks: List<Task>) : Content
    }
}

/** The tasks to list: the loaded ones whose title contains [TaskListUiState.appliedQuery]. */
fun TaskListUiState.Content.Loaded.matching(query: String): List<Task> {
    if (query.isBlank()) return tasks

    return tasks.filter { it.title.contains(query, ignoreCase = true) }
}
