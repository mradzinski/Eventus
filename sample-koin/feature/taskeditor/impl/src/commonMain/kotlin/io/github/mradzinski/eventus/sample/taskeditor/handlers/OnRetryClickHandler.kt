package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnRetryClick
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState

/** Loads the task again after a failed load. The id comes from the state, not from the event. */
class OnRetryClickHandler(private val tasks: TaskRepository) : TaskEditorHandler<OnRetryClick> {

    override val eventType = OnRetryClick::class
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnRetryClick>()

    override suspend fun handle(event: OnRetryClick, store: TaskEditorStore) {
        val failed = store.current as? TaskEditorUiState.LoadFailed ?: return

        tasks.loadInto(store, failed.taskId)
    }
}
