package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnTaskDoneChange
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnTaskDoneChangeHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L), task(id = 2L)))
    private val store = TaskListStore(TaskListUiState())
    private val handler = OnTaskDoneChangeHandler(tasks)

    @Test
    fun `it marks only that task as done`() = runTest {
        handler.handle(OnTaskDoneChange(taskId = 2L, done = true), store)

        tasks.tasks.value shouldContainExactly listOf(task(id = 1L), task(id = 2L, done = true))
    }

    @Test
    fun `a change that cannot be saved throws so the view model can say why`() = runTest {
        tasks.failure = OfflineException()

        shouldThrow<OfflineException> { handler.handle(OnTaskDoneChange(taskId = 2L, done = true), store) }
    }
}
