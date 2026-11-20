package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnDoneChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnDoneChangeHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))
    private val handler = OnDoneChangeHandler(tasks)

    @Test
    fun `it shows the task as done and saves it`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = 1L))

        handler.handle(OnDoneChange(done = true), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = 1L, done = true)
        tasks.tasks.value shouldContainExactly listOf(task(id = 1L, done = true))
    }

    @Test
    fun `a save that fails throws after the box was shown ticked and then put back`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = 1L))

        tasks.failure = OfflineException()

        store.state.test {
            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = 1L, done = false)

            shouldThrow<OfflineException> { handler.handle(OnDoneChange(done = true), store) }

            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = 1L, done = true)
            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = 1L, done = false)
        }
    }

    @Test
    fun `a failed save keeps what the user typed in the meantime`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = 1L, title = "Dentist"))

        tasks.failure = OfflineException()

        shouldThrow<OfflineException> { handler.handle(OnDoneChange(done = true), store) }

        store.current shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", done = false)
    }

    @Test
    fun `it does nothing for a task that has not been saved yet`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = null))

        handler.handle(OnDoneChange(done = true), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = null)
        tasks.tasks.value shouldContainExactly listOf(task(id = 1L))
    }

    @Test
    fun `it does nothing while the form is not shown`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Loading)

        handler.handle(OnDoneChange(done = true), store)

        store.current shouldBe TaskEditorUiState.Loading
    }
}
