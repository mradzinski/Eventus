package io.github.mradzinski.eventus.sample.taskeditor.handlers

import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEffect
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorEvent.OnBackClick
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.taskeditor.TaskEditorStore

internal class OnBackClickHandler : TaskEditorHandler<OnBackClick> {

    override val eventType = OnBackClick::class

    override suspend fun handle(event: OnBackClick, store: TaskEditorStore) {
        store.emit(TaskEditorEffect.Close)
    }
}
