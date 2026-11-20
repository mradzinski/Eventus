package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnSearchChange
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnSearchChangeHandlerTest {

    private val store = TaskListStore(TaskListUiState())
    private val handler = OnSearchChangeHandler()

    @Test
    fun `it shows the text at once and filters by it after the pause`() = runTest {
        store.state.test {
            awaitItem() shouldBe TaskListUiState()

            handler.handle(OnSearchChange(query = " dentist "), store)

            awaitItem() shouldBe TaskListUiState(query = " dentist ", appliedQuery = "")
            awaitItem() shouldBe TaskListUiState(query = " dentist ", appliedQuery = "dentist")
        }
    }
}
