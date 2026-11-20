package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnAddClick
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnAddClickHandlerTest {

    private val store = TaskListStore(TaskListUiState())
    private val handler = OnAddClickHandler()

    @Test
    fun `it asks to open the editor for a new task`() = runTest {
        handler.handle(OnAddClick, store)

        store.effects.test {
            awaitItem() shouldBe TaskListEffect.OpenEditor(taskId = null)
        }
    }
}
