package io.github.mradzinski.eventus.sample.data

import kotlinx.coroutines.flow.Flow

/**
 * The tasks of the app. Every function is safe to call from the main thread, and the ones that talk to
 * the server throw a [TaskException] when they fail.
 */
interface TaskRepository {

    /** The tasks, emitted again each time one of them changes. */
    fun observeTasks(): Flow<List<Task>>

    /** Fetches the tasks from the server again. */
    suspend fun refresh()

    /** The task with [id]. */
    suspend fun task(id: Long): Task

    /** Creates the task when [id] is null, and updates the task with that id otherwise. */
    suspend fun save(
        id: Long?,
        title: String,
        notes: String,
    ): Task

    /**
     * Marks the task with [id] as done or not done. [observeTasks] shows the change at once, before it
     * is saved, and shows the old value again if saving fails, in which case this throws.
     */
    suspend fun setDone(id: Long, done: Boolean)
}

/** Why an operation on tasks failed. */
sealed class TaskException(message: String) : Exception(message)

class OfflineException : TaskException("The device is offline")

class TaskNotFoundException(id: Long) : TaskException("No task with id $id")
