package io.github.mradzinski.eventus.sample.presentation.tasklist

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mradzinski.eventus.compose.collectEffects
import io.github.mradzinski.eventus.sample.presentation.common.textResource
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel

/**
 * The stateful half of the task list: it gets the view model, shows its state and performs the effects. The drawing is all in [TaskListScreen].
 *
 * @param offlineSwitch The app's "Work offline" switch, to show in the bar.
 * @param onOpenEditor Called to navigate to the editor, with the id of the task to edit or null for a new one.
 */
@Composable
internal fun TaskListRoute(
    offlineSwitch: @Composable () -> Unit,
    onOpenEditor: (taskId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<TaskListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.collectEffects { effect ->
        when (effect) {
            is TaskListEffect.OpenEditor -> onOpenEditor(effect.taskId)
            is TaskListEffect.ShowFailure -> snackbarHostState.showSnackbar(getString(effect.reason.textResource()))
        }
    }

    TaskListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        offlineSwitch = offlineSwitch,
        modifier = modifier,
    )
}
