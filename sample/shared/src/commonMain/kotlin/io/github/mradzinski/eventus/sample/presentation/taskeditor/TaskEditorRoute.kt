package io.github.mradzinski.eventus.sample.presentation.taskeditor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mradzinski.eventus.sample.navigation.TaskEditorKey
import io.github.mradzinski.eventus.sample.presentation.common.textResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The stateful half of the task editor: it gets the view model for [key] and performs the effects. The
 * drawing is all in [TaskEditorScreen].
 *
 * @param offlineSwitch The app's "Work offline" switch, to show in the bar.
 * @param onClose Called to navigate back, after a save or when the user leaves.
 */
@Composable
internal fun TaskEditorRoute(
    key: TaskEditorKey,
    offlineSwitch: @Composable () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<TaskEditorViewModel> { parametersOf(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TaskEditorEffect.Close -> currentOnClose()

                is TaskEditorEffect.ShowFailure -> launch {
                    snackbarHostState.showSnackbar(getString(effect.reason.textResource()))
                }
            }
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
