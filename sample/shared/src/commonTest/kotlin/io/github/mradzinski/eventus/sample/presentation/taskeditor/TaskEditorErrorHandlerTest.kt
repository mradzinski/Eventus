package io.github.mradzinski.eventus.sample.presentation.taskeditor

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TaskEditorErrorHandlerTest {

    private val form = TaskEditorUiState.Editing(taskId = 1L, title = "Dentist")
    private val store = TaskEditorStore(form)
    private val errorHandler = TaskEditorErrorHandler()

    @Test
    fun `it tells the user why and leaves the form as it is`() = runTest {
        errorHandler.handle(TaskEditorEvent.OnSaveClick, OfflineException(), store)

        store.current shouldBe form
        store.effects.test {
            awaitItem() shouldBe TaskEditorEffect.ShowFailure(FailureReason.Offline)
        }
    }
}
