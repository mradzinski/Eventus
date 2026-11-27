package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnScreenOpened
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.task
import io.github.mradzinski.eventus.sample.ui.FailureReason
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnScreenOpenedHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L, title = "Dentist", notes = "Morning")))

    @Test
    fun `without an id it shows an empty form for a new task`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnScreenOpened(taskId = null))

        state shouldBe TaskEditorUiState.Editing(taskId = null)
    }

    @Test
    fun `with an id it shows that task in the form`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnScreenOpened(taskId = 1L))

        state shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", notes = "Morning")
    }

    @Test
    fun `a task that does not exist becomes a failed load`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnScreenOpened(taskId = 9L))

        state shouldBe TaskEditorUiState.LoadFailed(taskId = 9L, reason = FailureReason.NotFound)
    }

    @Test
    fun `a load while offline becomes a failed load`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        tasks.failure = OfflineException()

        dispatch(OnScreenOpened(taskId = 1L))

        state shouldBe TaskEditorUiState.LoadFailed(taskId = 1L, reason = FailureReason.Offline)
    }
}
