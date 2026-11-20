package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnTaskDoneChange
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore

/**
 * Ticks or unticks a task. The tick shows at once, and goes away again if it could not be saved, but
 * none of that is done here: the list on screen is what the repository holds, so the repository is the
 * one that shows the change early and takes it back. This handler only has to start right away, which
 * the default strategy does for every tap, and to let a failure through to the screen's error handler.
 */
internal class OnTaskDoneChangeHandler(private val tasks: TaskRepository) : TaskListHandler<OnTaskDoneChange> {

    override val eventType = OnTaskDoneChange::class

    override suspend fun handle(event: OnTaskDoneChange, store: TaskListStore) {
        tasks.setDone(id = event.taskId, done = event.done)
    }
}
