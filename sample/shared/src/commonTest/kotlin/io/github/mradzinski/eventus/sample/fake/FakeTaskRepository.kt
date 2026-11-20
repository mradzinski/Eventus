package io.github.mradzinski.eventus.sample.fake

import io.github.mradzinski.eventus.sample.data.Task
import io.github.mradzinski.eventus.sample.data.TaskException
import io.github.mradzinski.eventus.sample.data.TaskNotFoundException
import io.github.mradzinski.eventus.sample.data.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A repository a test fills by hand. Set [failure] to make every call to the "server" throw it. */
internal class FakeTaskRepository(initial: List<Task> = emptyList()) : TaskRepository {

    val tasks = MutableStateFlow(initial)

    var failure: TaskException? = null

    var refreshCount = 0
        private set

    override fun observeTasks(): Flow<List<Task>> = tasks

    override suspend fun refresh() {
        failIfAsked()

        refreshCount++
    }

    override suspend fun task(id: Long): Task {
        failIfAsked()

        return tasks.value.firstOrNull { it.id == id } ?: throw TaskNotFoundException(id)
    }

    override suspend fun save(
        id: Long?,
        title: String,
        notes: String,
    ): Task {
        failIfAsked()

        val saved = Task(
            id = id ?: (tasks.value.size + 1L),
            title = title,
            notes = notes,
            done = false,
        )

        tasks.update { current -> current.filterNot { it.id == saved.id } + saved }

        return saved
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        failIfAsked()

        tasks.update { current ->
            current.map { task ->
                if (task.id == id) {
                    task.copy(done = done)
                } else {
                    task
                }
            }
        }
    }

    private fun failIfAsked() {
        failure?.let { throw it }
    }
}

internal fun task(
    id: Long,
    title: String = "Task $id",
    notes: String = "",
    done: Boolean = false,
): Task = Task(id = id, title = title, notes = notes, done = done)
