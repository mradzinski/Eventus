package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.fake.task
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnSaveClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnSaveClickHandlerTest {

    private val tasks = FakeTaskRepository()
    private val handler = OnSaveClickHandler(tasks)

    @Test
    fun `it saves the trimmed form and asks to close the editor`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = 7L, title = " Dentist ", notes = " Morning "))

        handler.handle(OnSaveClick, store)

        tasks.tasks.value shouldContainExactly listOf(task(id = 7L, title = "Dentist", notes = "Morning"))
        store.effects.test {
            awaitItem() shouldBe TaskEditorEffect.Close
        }
    }

    @Test
    fun `it shows progress only while it saves`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = null, title = "Dentist"))

        store.state.test {
            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = false)

            handler.handle(OnSaveClick, store)

            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = true)
            awaitItem() shouldBe TaskEditorUiState.Editing(taskId = null, title = "Dentist", saving = false)
        }
    }

    @Test
    fun `without a title it warns and saves nothing`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Editing(taskId = null, title = "  "))

        handler.handle(OnSaveClick, store)

        store.current shouldBe TaskEditorUiState.Editing(taskId = null, title = "  ", titleMissing = true)
        tasks.tasks.value.shouldBeEmpty()
        store.effects.test {
            expectNoEvents()
        }
    }

    @Test
    fun `a save that fails throws and leaves the form open as it was`() = runTest {
        val form = TaskEditorUiState.Editing(taskId = null, title = "Dentist")
        val store = TaskEditorStore(form)

        tasks.failure = OfflineException()

        shouldThrow<OfflineException> { handler.handle(OnSaveClick, store) }

        store.current shouldBe form
        store.effects.test {
            expectNoEvents()
        }
    }

    @Test
    fun `it does nothing while the form is not shown`() = runTest {
        val store = TaskEditorStore(TaskEditorUiState.Loading)

        handler.handle(OnSaveClick, store)

        store.current shouldBe TaskEditorUiState.Loading
        tasks.tasks.value.shouldBeEmpty()
    }
}
