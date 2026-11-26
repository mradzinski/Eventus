package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnTitleChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnTitleChangeHandlerTest {

    @Test
    fun `it changes the title and clears the missing title warning`() = runEventHandlerTest(
        handler = OnTitleChangeHandler(),
        initialState = TaskEditorUiState.Editing(taskId = null, titleMissing = true),
    ) {
        dispatch(OnTitleChange(title = "Dentist"))

        state shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist", titleMissing = false)
    }

    @Test
    fun `it does nothing while the form is not shown`() = runEventHandlerTest(
        handler = OnTitleChangeHandler(),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnTitleChange(title = "Dentist"))

        state shouldBe TaskEditorUiState.Loading
    }
}
