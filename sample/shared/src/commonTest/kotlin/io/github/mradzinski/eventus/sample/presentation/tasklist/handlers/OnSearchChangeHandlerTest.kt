package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnSearchChange
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.github.mradzinski.eventus.sample.presentation.tasklist.handlers.OnSearchChangeHandler.Companion.SEARCH_DEBOUNCE
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnSearchChangeHandlerTest {

    @Test
    fun `it shows the text at once and filters by it after the pause`() = runEventHandlerTest(
        handler = OnSearchChangeHandler(),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnSearchChange(query = " dentist "))

        state shouldBe TaskListUiState(query = " dentist ", appliedQuery = "")

        advanceBy(SEARCH_DEBOUNCE)

        state shouldBe TaskListUiState(query = " dentist ", appliedQuery = "dentist")
    }

    @Test
    fun `only the last of several quick keystrokes filters the list`() = runEventHandlerTest(
        handler = OnSearchChangeHandler(),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnSearchChange(query = "de"), OnSearchChange(query = "den"))
        advanceBy(SEARCH_DEBOUNCE)

        states.map { it.appliedQuery } shouldContainExactly listOf("", "", "", "den")
    }
}
