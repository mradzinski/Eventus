package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnRefresh
import io.github.mradzinski.eventus.sample.tasklist.TaskListUiState
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnRefreshHandlerTest {

    private val tasks = FakeTaskRepository()

    @Test
    fun `it refreshes the tasks and shows progress only while it does`() = runEventHandlerTest(
        handler = OnRefreshHandler(tasks),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnRefresh)

        states.map { it.refreshing } shouldContainExactly listOf(false, true, false)
        tasks.refreshCount shouldBe 1
    }

    @Test
    fun `a refresh that fails stops showing progress`() = runEventHandlerTest(
        handler = OnRefreshHandler(tasks),
        initialState = TaskListUiState(),
    ) {
        tasks.failure = OfflineException()

        dispatch(OnRefresh)

        failures.single().shouldBeInstanceOf<OfflineException>()
        state.refreshing shouldBe false
    }
}
