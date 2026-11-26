package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnSaveClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnSaveClickHandlerTest {

    private val tasks = FakeTaskRepository()

    @Test
    fun `it saves the trimmed form and asks to close the editor`() = runEventHandlerTest(
        handler = OnSaveClickHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = 7L, title = " Dentist ", notes = " Morning "),
    ) {
        dispatch(OnSaveClick)

        tasks.tasks.value shouldContainExactly listOf(task(id = 7L, title = "Dentist", notes = "Morning"))
        effects shouldContainExactly listOf(TaskEditorEffect.Close)
    }

    @Test
    fun `it shows progress only while it saves`() = runEventHandlerTest(
        handler = OnSaveClickHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = null, title = "Dentist"),
    ) {
        dispatch(OnSaveClick)

        states shouldContainExactly listOf(
            TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = false),
            TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = true),
            TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = false),
        )
    }

    @Test
    fun `without a title it warns and saves nothing`() = runEventHandlerTest(
        handler = OnSaveClickHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = null, title = "  "),
    ) {
        dispatch(OnSaveClick)

        state shouldBe TaskEditorUiState.Editing(taskId = null, title = "  ", titleMissing = true)
        tasks.tasks.value.shouldBeEmpty()
        effects.shouldBeEmpty()
    }

    @Test
    fun `a save that fails leaves the form open as it was`() = runEventHandlerTest(
        handler = OnSaveClickHandler(tasks),
        initialState = TaskEditorUiState.Editing(taskId = null, title = "Dentist"),
    ) {
        tasks.failure = OfflineException()

        dispatch(OnSaveClick)

        failures.single().shouldBeInstanceOf<OfflineException>()
        state shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist")
        effects.shouldBeEmpty()
    }

    @Test
    fun `it does nothing while the form is not shown`() = runEventHandlerTest(
        handler = OnSaveClickHandler(tasks),
        initialState = TaskEditorUiState.Loading,
    ) {
        dispatch(OnSaveClick)

        state shouldBe TaskEditorUiState.Loading
        tasks.tasks.value.shouldBeEmpty()
    }
}
