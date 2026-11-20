package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnBackClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnBackClickHandlerTest {

    private val store = TaskEditorStore(TaskEditorUiState.Loading)
    private val handler = OnBackClickHandler()

    @Test
    fun `it asks to close the editor`() = runTest {
        handler.handle(OnBackClick, store)

        store.effects.test {
            awaitItem() shouldBe TaskEditorEffect.Close
        }
    }
}
