package io.github.mradzinski.eventus.sample

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import com.github.terrakok.navigation3.browser.HierarchicalBrowserNavigation
import com.github.terrakok.navigation3.browser.buildBrowserHistoryFragment
import io.github.mradzinski.eventus.sample.taskeditor.api.TaskEditorKey
import io.github.mradzinski.eventus.sample.tasklist.api.TaskListKey

/**
 * Ties the app's back stack to the browser: the browser's Back button closes the visible screen, as the
 * app's own Back arrow does, and the address bar names the screen.
 *
 * Navigation 3 does not do this by itself yet (JetBrains tracks it as CMP-8924). Until it does, this uses
 * the small library JetBrains' own documentation points to. Forward and typing an address are not
 * supported by it.
 */
@Composable
internal fun BrowserHistory(backStack: List<NavKey>) {
    HierarchicalBrowserNavigation { fragmentOf(backStack.last()) }
}

/** Names a screen in the address bar: the part of the address after `#`, or `null` for a screen with no name. */
private fun fragmentOf(key: NavKey): String? = when (key) {
    is TaskListKey -> buildBrowserHistoryFragment("tasks")
    is TaskEditorKey -> buildBrowserHistoryFragment("task", mapOf("id" to key.taskId.toString()))
    else -> null
}
