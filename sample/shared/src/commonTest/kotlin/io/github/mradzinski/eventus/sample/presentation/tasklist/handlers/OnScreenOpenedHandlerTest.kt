package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState.Content
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class OnScreenOpenedHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))
    private val store = TaskListStore(TaskListUiState())
    private val handler = OnScreenOpenedHandler(tasks)

    @Test
    fun `it shows the tasks and each later change of them`() = runTest {
        backgroundScope.launch { handler.handle(OnScreenOpened, store) }
        testScheduler.runCurrent()

        store.current.content shouldBe Content.Loaded(listOf(task(id = 1L)))

        tasks.tasks.value = listOf(task(id = 1L), task(id = 2L))
        testScheduler.runCurrent()

        store.current.content shouldBe Content.Loaded(listOf(task(id = 1L), task(id = 2L)))
    }
}
