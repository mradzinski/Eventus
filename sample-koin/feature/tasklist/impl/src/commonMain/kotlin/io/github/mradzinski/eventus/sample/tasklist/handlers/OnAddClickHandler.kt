package io.github.mradzinski.eventus.sample.tasklist.handlers

import io.github.mradzinski.eventus.sample.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.tasklist.TaskListEvent.OnAddClick
import io.github.mradzinski.eventus.sample.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.tasklist.TaskListStore

class OnAddClickHandler : TaskListHandler<OnAddClick> {

    override val eventType = OnAddClick::class

    override suspend fun handle(event: OnAddClick, store: TaskListStore) {
        store.emit(TaskListEffect.OpenEditor(taskId = null))
    }
}
