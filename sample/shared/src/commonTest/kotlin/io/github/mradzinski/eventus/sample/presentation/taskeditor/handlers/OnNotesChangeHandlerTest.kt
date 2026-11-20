package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnNotesChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnNotesChangeHandlerTest {

    private val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = 1L, title = "Dentist"))
    private val handler = OnNotesChangeHandler()

    @Test
    fun `it changes the notes and nothing else`() = runTest {
        handler.handle(OnNotesChange(notes = "Morning"), store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", notes = "Morning")
    }
}
