package io.github.mradzinski.eventus.sample.data

import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.testing.FakeConnectivity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class InMemoryTaskRepositoryTest {

    private val connectivity = FakeConnectivity()
    private val repository = InMemoryTaskRepository(connectivity)

    @Test
    fun `a task marked as done shows as done before the server has answered`() = runTest {
        launch { repository.setDone(id = FIRST_TASK, done = true) }
        testScheduler.runCurrent()

        shownAsDone(FIRST_TASK) shouldBe true

        testScheduler.advanceUntilIdle()

        shownAsDone(FIRST_TASK) shouldBe true
        repository.task(FIRST_TASK).done shouldBe true
    }

    @Test
    fun `a task marked as done while offline goes back and the call fails`() = runTest {
        connectivity.setOnline(false)

        launch {
            shouldThrow<OfflineException> { repository.setDone(id = FIRST_TASK, done = true) }
        }
        testScheduler.runCurrent()

        shownAsDone(FIRST_TASK) shouldBe true

        testScheduler.advanceUntilIdle()

        shownAsDone(FIRST_TASK) shouldBe false
    }

    @Test
    fun `two changes to one task that both fail leave what the server has`() = runTest {
        connectivity.setOnline(false)

        launch {
            shouldThrow<OfflineException> { repository.setDone(id = FIRST_TASK, done = true) }
        }
        launch {
            shouldThrow<OfflineException> { repository.setDone(id = FIRST_TASK, done = false) }
        }
        testScheduler.advanceUntilIdle()

        shownAsDone(FIRST_TASK) shouldBe false
    }

    @Test
    fun `a saved task keeps its place in the list`() = runTest {
        val before = repository
            .observeTasks()
            .first()

        repository.save(id = FIRST_TASK, title = "Renamed", notes = "")

        val after = repository
            .observeTasks()
            .first()

        after.map { it.id } shouldBe before.map { it.id }
        after.first().title shouldBe "Renamed"
    }

    private suspend fun shownAsDone(id: Long): Boolean = repository
        .observeTasks()
        .first()
        .first { it.id == id }
        .done

    private companion object {
        const val FIRST_TASK = 1L
    }
}
