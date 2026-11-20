package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnTitleChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnTitleChangeHandlerTest {

    private val handler = OnTitleChangeHandler()

    @Test
    fun `it changes the title and clears the missing title warning`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = null, titleMissing = true))

        handler.handle(OnTitleChange(title = "Dentist"), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist", titleMissing = false)
    }

    @Test
    fun `it does nothing while the form is not shown`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Loading)

        handler.handle(OnTitleChange(title = "Dentist"), store)

        store.current shouldBe TaskEditorUiState.Loading
    }
}
