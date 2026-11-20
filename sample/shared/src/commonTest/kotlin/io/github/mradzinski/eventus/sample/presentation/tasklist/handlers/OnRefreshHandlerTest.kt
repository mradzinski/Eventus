package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.fake.FakeTaskRepository
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnRefresh
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListUiState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnRefreshHandlerTest {

    private val tasks = FakeTaskRepository()
    private val store = TaskListStore(TaskListUiState())
    private val handler = OnRefreshHandler(tasks)

    @Test
    fun `it refreshes the tasks and shows progress only while it does`() = runTest {
        store.state.test {
            awaitItem().refreshing shouldBe false

            handler.handle(OnRefresh, store)

            awaitItem().refreshing shouldBe true
            awaitItem().refreshing shouldBe false
        }

        tasks.refreshCount shouldBe 1
    }

    @Test
    fun `a refresh that fails throws and stops showing progress`() = runTest {
        tasks.failure = OfflineException()

        shouldThrow<OfflineException> { handler.handle(OnRefresh, store) }

        store.current.refreshing shouldBe false
    }
}
