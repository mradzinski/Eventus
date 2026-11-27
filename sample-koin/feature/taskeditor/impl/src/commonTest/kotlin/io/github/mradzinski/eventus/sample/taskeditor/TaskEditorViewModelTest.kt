package io.github.mradzinski.eventus.sample.taskeditor

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.taskeditor.handlers.OnSaveClickHandler
import io.github.mradzinski.eventus.sample.taskeditor.handlers.OnScreenOpenedHandler
import io.github.mradzinski.eventus.sample.taskeditor.handlers.OnTitleChangeHandler
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.silentLogger
import io.github.mradzinski.eventus.sample.testing.task
import io.github.mradzinski.eventus.sample.ui.FailureReason
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The view model as the UI uses it: events in, state and effects out, with the real handlers behind it. */
class TaskEditorViewModelTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L, title = "Dentist")))

    @Test
    fun `it loads its task as soon as it is created`() = runTest {
        val viewModel = viewModel(taskId = 1L)

        viewModel.state.value shouldBe TaskEditorUiState.Loading

        testScheduler.runCurrent()

        viewModel.state.value shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist")
    }

    @Test
    fun `an edited task is saved and the editor closes`() = runTest {
        val viewModel = viewModel(taskId = 1L)

        testScheduler.runCurrent()
        viewModel.onEvent(TaskEditorEvent.OnTitleChange(title = "Dentist at nine"))
        viewModel.onEvent(TaskEditorEvent.OnSaveClick)
        testScheduler.runCurrent()

        tasks.tasks.value shouldBe listOf(task(id = 1L, title = "Dentist at nine"))
        viewModel.effects.test {
            awaitItem() shouldBe TaskEditorEffect.Close
        }
    }

    @Test
    fun `a save that fails becomes an effect that says why and the form stays`() = runTest {
        val viewModel = viewModel(taskId = null)

        testScheduler.runCurrent()
        viewModel.onEvent(TaskEditorEvent.OnTitleChange(title = "Dentist"))
        testScheduler.runCurrent()
        tasks.failure = OfflineException()
        viewModel.onEvent(TaskEditorEvent.OnSaveClick)
        testScheduler.runCurrent()

        viewModel.state.value shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist")
        viewModel.effects.test {
            awaitItem() shouldBe TaskEditorEffect.ShowFailure(FailureReason.Offline)
        }
    }

    private fun TestScope.viewModel(taskId: Long?): TaskEditorViewModel = TaskEditorViewModel(
        taskId = taskId,
        handlers = listOf(
            OnScreenOpenedHandler(tasks),
            OnTitleChangeHandler(),
            OnSaveClickHandler(tasks),
        ),
        errorHandler = TaskEditorErrorHandler(),
        logger = silentLogger(),
        scope = backgroundScope,
    )
}
