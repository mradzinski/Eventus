package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnTaskClick
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnTaskClickHandlerTest {

    private val store = TaskListStore(TaskListUiState())
    private val handler = OnTaskClickHandler()

    @Test
    fun `it asks to open the editor for that task`() = runTest {
        handler.handle(OnTaskClick(taskId = 3L), store)

        store.effects.test {
            awaitItem() shouldBe TaskListEffect.OpenEditor(taskId = 3L)
        }
    }
}
