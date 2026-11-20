package io.github.mradzinski.eventus.sample.navigation

import androidx.navigation3.runtime.NavKey

/** The task list, where the app starts. */
internal data object TaskListKey : NavKey

/** The editor. A destination carries only an id: the screen loads what it needs by itself. */
internal data class TaskEditorKey(val taskId: Long?) : NavKey
