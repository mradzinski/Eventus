package io.github.mradzinski.eventus.sample.presentation.tasklist

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.silentLogger
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState.Content
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnRefreshHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnScreenOpenedHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnTaskClickHandler
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The view model as the UI uses it: events in, state and effects out, with the real handlers behind it. */
class TaskListViewModelTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))

    @Test
    fun `it starts loading and shows the tasks without being asked`() = runTest {
        val viewModel = viewModel()

        viewModel.state.value.content shouldBe Content.Loading

        testScheduler.runCurrent()

        viewModel.state.value.content shouldBe Content.Loaded(listOf(task(id = 1L)))
    }

    @Test
    fun `it keeps following the tasks for as long as it lives`() = runTest {
        val viewModel = viewModel()

        testScheduler.runCurrent()
        tasks.tasks.value = emptyList()
        testScheduler.runCurrent()

        viewModel.state.value.content shouldBe Content.Loaded(emptyList())
    }

    @Test
    fun `a handler that fails becomes an effect that says why`() = runTest {
        val viewModel = viewModel()

        tasks.failure = OfflineException()
        viewModel.onEvent(TaskListEvent.OnRefresh)
        testScheduler.runCurrent()

        viewModel.effects.test {
            awaitItem() shouldBe TaskListEffect.ShowFailure(FailureReason.Offline)
        }
    }

    @Test
    fun `a tap on a task becomes an effect that opens the editor`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(TaskListEvent.OnTaskClick(taskId = 1L))
        testScheduler.runCurrent()

        viewModel.effects.test {
            awaitItem() shouldBe TaskListEffect.OpenEditor(taskId = 1L)
        }
    }

    private fun TestScope.viewModel(): TaskListViewModel = TaskListViewModel(
        handlers = listOf(
            OnScreenOpenedHandler(tasks),
            OnRefreshHandler(tasks),
            OnTaskClickHandler(),
        ),
        errorHandler = TaskListErrorHandler(),
        logger = silentLogger(),
        scope = backgroundScope,
    )
}
