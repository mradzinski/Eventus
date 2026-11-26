package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnBackClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test

class OnBackClickHandlerTest {

    @Test
    fun `it asks to close the editor`() = runEventHandlerTest(
        handler = OnBackClickHandler(),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnBackClick)

        effects shouldContainExactly listOf(TaskEditorEffect.Close)
    }
}
