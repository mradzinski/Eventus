package io.github.mradzinski.eventus.sample.presentation.tasklist.handlers

import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEffect
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent.OnAddClick
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListStore

internal class OnAddClickHandler : TaskListHandler<OnAddClick> {

    override val eventType = OnAddClick::class

    override suspend fun handle(event: OnAddClick, store: TaskListStore) {
        store.emit(TaskListEffect.OpenEditor(taskId = null))
    }
}
