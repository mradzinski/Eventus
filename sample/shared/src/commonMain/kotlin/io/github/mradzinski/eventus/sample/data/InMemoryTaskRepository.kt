package io.github.mradzinski.eventus.sample.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps the tasks in memory and behaves like an app in front of a slow server.
 *
 * It holds two copies. [server] is what the server has: a call reaches it after [latency], and not at
 * all while the device is offline. [tasks] is what the app shows, and the only copy anyone observes.
 * Most calls change both once the server has answered. [setDone] is different: it changes what the app
 * shows first, so a tick appears the moment it is made, and takes that back if the server refuses.
 */
internal class InMemoryTaskRepository(
    private val connectivity: Connectivity,
    private val latency: Duration = DEFAULT_LATENCY,
) : TaskRepository {

    private val tasks = MutableStateFlow(SAMPLE_TASKS)

    /** What the server has. Only touched from the main thread, like everything in this sample. */
    private var server: List<Task> = SAMPLE_TASKS

    override fun observeTasks(): Flow<List<Task>> = tasks

    override suspend fun refresh() {
        reachServer()

        tasks.value = server
    }

    override suspend fun task(id: Long): Task {
        reachServer()

        return server.firstOrNull { it.id == id } ?: throw TaskNotFoundException(id)
    }

    override suspend fun save(
        id: Long?,
        title: String,
        notes: String,
    ): Task {
        reachServer()

        val existing = server.firstOrNull { it.id == id }

        val saved = Task(
            id = id ?: nextId(),
            title = title,
            notes = notes,
            done = existing?.done ?: false,
        )

        server = server.withSaved(saved)
        tasks.update { it.withSaved(saved) }

        return saved
    }

    override suspend fun setDone(id: Long, done: Boolean) {
        // Shown at once: everyone observing the tasks sees the tick before the server has heard of it.
        tasks.update { it.withDone(id, done) }

        try {
            reachServer()
        } catch (error: OfflineException) {
            // The server never got it. Show what the server has, which is also right when several
            // changes to the same task were waiting.
            val confirmed = server.firstOrNull { it.id == id }?.done ?: !done

            tasks.update { it.withDone(id, confirmed) }

            throw error
        }

        server = server.withDone(id, done)
    }

    private suspend fun reachServer() {
        delay(latency)

        if (!connectivity.online.value) throw OfflineException()
    }

    /** This list with [saved] in the place of the task it replaces, or at the end when it is new. */
    private fun List<Task>.withSaved(saved: Task): List<Task> {
        val index = indexOfFirst { it.id == saved.id }

        if (index < 0) return this + saved

        return toMutableList().apply { this[index] = saved }
    }

    private fun List<Task>.withDone(id: Long, done: Boolean): List<Task> = map { task ->
        if (task.id == id) {
            task.copy(done = done)
        } else {
            task
        }
    }

    private fun nextId(): Long = (server.maxOfOrNull { it.id } ?: 0L) + 1L

    private companion object {

        val DEFAULT_LATENCY = 600.milliseconds

        val SAMPLE_TASKS = listOf(
            Task(id = 1L, title = "Book the dentist", notes = "Ask for a morning slot.", done = false),
            Task(id = 2L, title = "Renew the passport", notes = "It expires in March.", done = false),
            Task(id = 3L, title = "Water the plants", notes = "", done = true),
            Task(id = 4L, title = "Plan the team offsite", notes = "Three options by Friday.", done = false),
        )
    }
}
