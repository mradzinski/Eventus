package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnScreenOpenedHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L, title = "Dentist", notes = "Morning")))
    private val store = TaskEditorStore(TaskEditorUiState.Loading)
    private val handler = OnScreenOpenedHandler(tasks)

    @Test
    fun `without an id it shows an empty form for a new task`() = runTest {
        handler.handle(OnScreenOpened(taskId = null), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = null)
    }

    @Test
    fun `with an id it shows that task in the form`() = runTest {
        handler.handle(OnScreenOpened(taskId = 1L), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", notes = "Morning")
    }

    @Test
    fun `a task that does not exist becomes a failed load`() = runTest {
        handler.handle(OnScreenOpened(taskId = 9L), store)

        store.current shouldBe TaskEditorUiState.LoadFailed(taskId = 9L, reason = FailureReason.NotFound)
    }

    @Test
    fun `a load while offline becomes a failed load`() = runTest {
        tasks.failure = OfflineException()

        handler.handle(OnScreenOpened(taskId = 1L), store)

        store.current shouldBe TaskEditorUiState.LoadFailed(taskId = 1L, reason = FailureReason.Offline)
    }
}
