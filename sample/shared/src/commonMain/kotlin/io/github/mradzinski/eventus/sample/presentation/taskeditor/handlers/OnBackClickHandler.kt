package io.github.mradzinski.eventus.sample.presentation.taskeditor.handlers

import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent.OnBackClick
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorStore

internal class OnBackClickHandler : TaskEditorHandler<OnBackClick> {

    override val eventType = OnBackClick::class

    override suspend fun handle(event: OnBackClick, store: TaskEditorStore) {
        store.emit(TaskEditorEffect.Close)
    }
}
