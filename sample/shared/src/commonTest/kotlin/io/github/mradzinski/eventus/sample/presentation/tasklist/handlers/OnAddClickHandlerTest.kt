package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnAddClick
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test

class OnAddClickHandlerTest {

    @Test
    fun `it asks to open the editor for a new task`() = runEventHandlerTest(
        handler = OnAddClickHandler(),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnAddClick)

        effects shouldContainExactly listOf(TaskListEffect.OpenEditor(taskId = null))
    }
}
