package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnTaskDoneChange
import io.github.mradzinski.eventus.sample.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.tasklist.TaskListStore

/**
 * Ticks or unticks a task. The tick shows at once, and goes away again if it could not be saved, but
 * none of that is done here: the list on screen is what the repository holds, so the repository is the
 * one that shows the change early and takes it back.
 *
 * The strategy is `Latest` for each task. A second tap on a task cancels the save of the first one and
 * starts right away, so the checkbox follows the finger and only the last choice reaches the server. A
 * tap on another task cancels nothing.
 */
class OnTaskDoneChangeHandler(private val tasks: TaskRepository) : TaskListHandler<OnTaskDoneChange> {

    override val eventType = OnTaskDoneChange::class
    override val strategy = DispatchStrategy.Latest<OnTaskDoneChange> { it.taskId }

    override suspend fun handle(event: OnTaskDoneChange, store: TaskListStore) {
        tasks.setDone(id = event.taskId, done = event.done)
    }
}
