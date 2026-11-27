package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.sample.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnTaskClick
import io.github.mradzinski.eventus.sample.tasklist.TaskListUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test

class OnTaskClickHandlerTest {

    @Test
    fun `it asks to open the editor for that task`() = runEventHandlerTest(
        handler = OnTaskClickHandler(),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnTaskClick(taskId = 3L))

        effects shouldContainExactly listOf(TaskListEffect.OpenEditor(taskId = 3L))
    }
}
