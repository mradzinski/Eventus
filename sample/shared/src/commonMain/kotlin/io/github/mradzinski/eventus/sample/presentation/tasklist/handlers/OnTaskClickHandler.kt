package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnTaskClick
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore

/** Navigation is an effect: the handler says where to go and passes only the task's id. */
internal class OnTaskClickHandler : TaskListHandler<OnTaskClick> {

    override val eventType = OnTaskClick::class

    override suspend fun handle(event: OnTaskClick, store: TaskListStore) {
        store.emit(TaskListEffect.OpenEditor(taskId = event.taskId))
    }
}
