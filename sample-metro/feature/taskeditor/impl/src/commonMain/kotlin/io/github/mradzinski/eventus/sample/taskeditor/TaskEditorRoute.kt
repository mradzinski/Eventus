package io.github.mradzinski.eventus.sample.taskeditor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.mradzinski.eventus.compose.collectEffects
import io.github.mradzinski.eventus.sample.taskeditor.api.TaskEditorKey
import io.github.mradzinski.eventus.sample.ui.textResource
import org.jetbrains.compose.resources.getString

/**
 * The stateful half of the task editor: it gets the view model for [key] and performs the effects. The
 * drawing is all in [TaskEditorScreen].
 *
 * @param offlineSwitch The app's "Work offline" switch, to show in the bar.
 * @param onClose Called to navigate back, after a save or when the user leaves.
 */
@Composable
fun TaskEditorRoute(
    key: TaskEditorKey,
    offlineSwitch: @Composable () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<TaskEditorViewModel, TaskEditorViewModel.Factory> { create(key.taskId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.collectEffects { effect ->
        when (effect) {
            is TaskEditorEffect.Close -> onClose()
            is TaskEditorEffect.ShowFailure -> snackbarHostState.showSnackbar(getString(effect.reason.textResource()))
        }
    }

    TaskEditorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        offlineSwitch = offlineSwitch,
        modifier = modifier,
    )
}
