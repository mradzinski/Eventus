package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnNotesChange
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnNotesChangeHandlerTest {

    @Test
    fun `it changes the notes and nothing else`() = runEventHandlerTest(
        handler = OnNotesChangeHandler(),
        initialState = TaskEditorUiState.Editing(taskId = 1L, title = "Dentist"),
    ) {
        dispatch(OnNotesChange(notes = "Morning"))

        state shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", notes = "Morning")
    }
}
