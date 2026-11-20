package io.github.mradzinski.eventus.sample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorRoute
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListRoute

/**
 * Owns the back stack. Screens never navigate themselves: their view models emit an effect, the route
 * turns it into one of the callbacks below, and only this function changes where the app is.
 *
 * @param offlineSwitch The app's "Work offline" switch, handed to every screen for its bar.
 *
 * The back stack is kept in memory, which is enough for a sample. An app would also save it, so that it
 * survives the process being stopped.
 */
@Composable
internal fun AppNavigation(offlineSwitch: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<NavKey>(TaskListKey) }
    val goBack = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { goBack() },
        // Gives each destination its own saved state and its own view models, cleared when it leaves the stack.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<TaskListKey> {
                TaskListRoute(
                    offlineSwitch = offlineSwitch,
                    onOpenEditor = { taskId -> backStack.add(TaskEditorKey(taskId)) },
                )
            }

            entry<TaskEditorKey> { key ->
                TaskEditorRoute(
                    key = key,
                    offlineSwitch = offlineSwitch,
                    onClose = { goBack() },
                )
            }
        },
        modifier = modifier,
    )
}
