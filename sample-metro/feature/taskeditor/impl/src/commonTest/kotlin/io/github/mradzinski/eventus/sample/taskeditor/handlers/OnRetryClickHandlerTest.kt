package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnRetryClick
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.task
import io.github.mradzinski.eventus.sample.ui.FailureReason
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnRetryClickHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L, title = "Dentist")))

    @Test
    fun `after a failed load it loads the same task again`() = runEventHandlerTest(
        handler = OnRetryClickHandler(tasks),
        initialState = TaskEditorUiState.LoadFailed(taskId = 1L, reason = FailureReason.Offline),
    ) {
        dispatch(OnRetryClick)

        state shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist")
    }

    @Test
    fun `it leaves the form alone when nothing failed to load`() = runEventHandlerTest(
        handler = OnRetryClickHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = 1L, title = "Edited"),
    ) {
        dispatch(OnRetryClick)

        state shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Edited")
    }
}
