package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState.Content
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnScreenOpenedHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))

    @Test
    fun `it shows the tasks and each later change of them`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(tasks),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnScreenOpened)

        state.content shouldBe Content.Loaded(listOf(task(id = 1L)))

        tasks.tasks.value = listOf(task(id = 1L), task(id = 2L))

        state.content shouldBe Content.Loaded(listOf(task(id = 1L), task(id = 2L)))
    }
}
