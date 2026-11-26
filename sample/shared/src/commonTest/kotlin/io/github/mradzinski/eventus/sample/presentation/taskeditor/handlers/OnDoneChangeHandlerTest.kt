package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnDoneChange
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnDoneChangeHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))

    @Test
    fun `it shows the task as done and saves it`() = runEventHandlerTest(
        handler = OnDoneChangeHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = 1L),
    ) {
        dispatch(OnDoneChange(done = true))

        state shouldBe TaskEditorUiState.Editing(taskId = 1L, done = true)
        tasks.tasks.value shouldContainExactly listOf(task(id = 1L, done = true))
    }

    @Test
    fun `a save that fails shows the box ticked and then puts it back`() = runEventHandlerTest(
        handler = OnDoneChangeHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = 1L),
    ) {
        tasks.failure = OfflineException()

        dispatch(OnDoneChange(done = true))

        failures.single().shouldBeInstanceOf<OfflineException>()

        states shouldContainExactly listOf(
            TaskEditorUiState.Editing(taskId = 1L, done = false),
            TaskEditorUiState.Editing(taskId = 1L, done = true),
            TaskEditorUiState.Editing(taskId = 1L, done = false),
        )
    }

    @Test
    fun `a failed save keeps what the user typed in the meantime`() = runEventHandlerTest(
        handler = OnDoneChangeHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = 1L, title = "Dentist"),
    ) {
        tasks.failure = OfflineException()

        dispatch(OnDoneChange(done = true))

        failures.single().shouldBeInstanceOf<OfflineException>()
        state shouldBe TaskEditorUiState.Editing(taskId = 1L, title = "Dentist", done = false)
    }

    @Test
    fun `it does nothing for a task that has not been saved yet`() = runEventHandlerTest(
        handler = OnDoneChangeHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = null),
    ) {
        dispatch(OnDoneChange(done = true))

        state shouldBe TaskEditorUiState.Editing(taskId = null)
        tasks.tasks.value shouldContainExactly listOf(task(id = 1L))
    }

    @Test
    fun `it does nothing while the form is not shown`() = runEventHandlerTest(
        handler = OnDoneChangeHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnDoneChange(done = true))

        state shouldBe TaskEditorUiState.Loading
    }
}
