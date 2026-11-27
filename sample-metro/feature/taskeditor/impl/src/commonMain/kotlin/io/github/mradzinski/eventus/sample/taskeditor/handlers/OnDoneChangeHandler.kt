package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnDoneChange
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorUiState

/**
 * Marks the task as done from the editor. The box changes at once and goes back if saving fails.
 *
 * Unlike the list, the editor loaded its task once and does not observe it, so the copy in its state is
 * the only one this screen has: the handler changes it itself, with `updateOptimistically`. The failure
 * still reaches the screen's error handler, which tells the user why.
 *
 * `Sequential`, so that taps made while a save is under way are applied one after the other and each
 * one starts from what the previous one left.
 */
class OnDoneChangeHandler(private val tasks: TaskRepository) : TaskEditorHandler<OnDoneChange> {

    override val eventType = OnDoneChange::class
    override val strategy = DispatchStrategy.Sequential<OnDoneChange>()

    override suspend fun handle(event: OnDoneChange, store: TaskEditorStore) {
        val form = store.current as? TaskEditorUiState.Editing ?: return
        val taskId = form.taskId ?: return
        val doneBefore = form.done

        store.updateOptimistically(
            stateUpdate = { it.withDone(event.done) },
            stateIfFailed = { it.withDone(doneBefore) },
        ) {
            tasks.setDone(id = taskId, done = event.done)
        }
    }

    private fun TaskEditorUiState.withDone(done: Boolean): TaskEditorUiState = if (this is TaskEditorUiState.Editing) {
        copy(done = done)
    } else {
        this
    }
}
