package io.github.mradzinski.eventus.sample.taskeditor.api

import androidx.navigation3.runtime.NavKey

/** The editor. A destination carries only an id: the screen loads what it needs by itself. */
data class TaskEditorKey(val taskId: Long?) : NavKey
