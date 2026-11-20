package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnRetryClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnRetryClickHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L, title = "Dentist")))
    private val handler = OnRetryClickHandler(tasks)

    @Test
    fun `after a failed load it loads the same task again`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.LoadFailed(taskId = 1L, reason = FailureReason.Offline))

        handler.handle(OnRetryClick, store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist")
    }

    @Test
    fun `it leaves the form alone when nothing failed to load`() = runTest {
        val form = TaskEditorUiState.Editing(taskId = 1L, title = "Edited")
        val store = TaskEditorStore(form)

        handler.handle(OnRetryClick, store)

        store.current shouldBe form
    }
}
