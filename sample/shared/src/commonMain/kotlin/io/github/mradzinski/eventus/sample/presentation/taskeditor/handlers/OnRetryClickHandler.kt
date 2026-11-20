package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.data.TaskRepository
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnRetryClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorUiState

/** Loads the task again after a failed load. The id comes from the state, not from the event. */
internal class OnRetryClickHandler(private val tasks: TaskRepository) : TaskEditorHandler<OnRetryClick> {

    override val eventType = OnRetryClick::class
    override val strategy = DispatchStrategy.DropIfBusy

    override suspend fun handle(event: OnRetryClick, store: TaskEditorStore) {
        val failed = store.current as? TaskEditorUiState.LoadFailed ?: return

        tasks.loadInto(store, failed.taskId)
    }
}
