package io.github.mradzinski.eventus.sample.presentation.tasklist

import app.cash.turbine.test
import io.github.mradzinski.eventus.sample.data.OfflineException
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TaskListErrorHandlerTest {

    private val store = TaskListStore(TaskListUiState())
    private val errorHandler = TaskListErrorHandler()

    @Test
    fun `it tells the user why and leaves the state alone`() = runTest {
        errorHandler.handle(TaskListEvent.OnRefresh, OfflineException(), store)

        store.current shouldBe TaskListUiState()
        store.effects.test {
            awaitItem() shouldBe TaskListEffect.ShowFailure(FailureReason.Offline)
        }
    }

    @Test
    fun `an error it does not know becomes an unknown failure`() = runTest {
        errorHandler.handle(TaskListEvent.OnRefresh, IllegalStateException("boom"), store)

        store.effects.test {
            awaitItem() shouldBe TaskListEffect.ShowFailure(FailureReason.Unknown)
        }
    }
}
