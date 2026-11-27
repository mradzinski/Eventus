package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.Dispatch
import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnTaskDoneChange
import io.github.mradzinski.eventus.sample.tasklist.TaskListUiState
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.task
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class OnTaskDoneChangeHandlerTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L), task(id = 2L)))
    private val handler = OnTaskDoneChangeHandler(tasks)

    @Test
    fun `it marks only that task as done`() = runEventHandlerTest(
        handler = handler,
        initialState = TaskListUiState(),
    ) {
        dispatch(OnTaskDoneChange(taskId = 2L, done = true))

        tasks.tasks.value shouldContainExactly listOf(task(id = 1L), task(id = 2L, done = true))
    }

    @Test
    fun `a change that cannot be saved fails so the error handler can say why`() = runEventHandlerTest(
        handler = handler,
        initialState = TaskListUiState(),
    ) {
        tasks.failure = OfflineException()

        dispatch(OnTaskDoneChange(taskId = 2L, done = true))

        failures.single().shouldBeInstanceOf<OfflineException>()
    }

    @Test
    fun `a new change replaces the one still being saved for that task and no other`() {
        val active = listOf(
            OnTaskDoneChange(taskId = 1L, done = true),
            OnTaskDoneChange(taskId = 2L, done = true),
        )

        val dispatch = handler.strategy.decide(OnTaskDoneChange(taskId = 2L, done = false), active)

        dispatch shouldBe Dispatch.Replace(listOf(OnTaskDoneChange(taskId = 2L, done = true)))
    }
}
