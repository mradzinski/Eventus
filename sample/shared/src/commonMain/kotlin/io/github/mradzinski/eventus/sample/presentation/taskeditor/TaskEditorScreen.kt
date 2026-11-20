package io.github.mradzinski.eventus.sample.presentation.taskeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mradzinski.eventus.sample.presentation.common.AppTopBar
import io.github.mradzinski.eventus.sample.presentation.common.FailureReason
import io.github.mradzinski.eventus.sample.presentation.common.failureText
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.task_editor_edit_title
import io.github.mradzinski.eventus.sample.resources.task_editor_field_done
import io.github.mradzinski.eventus.sample.resources.task_editor_field_notes
import io.github.mradzinski.eventus.sample.resources.task_editor_field_title
import io.github.mradzinski.eventus.sample.resources.task_editor_new_title
import io.github.mradzinski.eventus.sample.resources.task_editor_retry
import io.github.mradzinski.eventus.sample.resources.task_editor_save
import io.github.mradzinski.eventus.sample.resources.task_editor_title_missing
import org.jetbrains.compose.resources.stringResource

/** The widest the form gets, so it stays readable on a tablet or a desktop window. */
private val MaxContentWidth = 640.dp

/** The stateless half of the task editor: it draws [state] and reports what the user does through [onEvent]. */
@Composable
internal fun TaskEditorScreen(
    state: TaskEditorUiState,
    onEvent: (TaskEditorEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    offlineSwitch: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNewTask = state is TaskEditorUiState.Editing && state.taskId == null
    val title = if (isNewTask) {
        stringResource(Res.string.task_editor_new_title)
    } else {
        stringResource(Res.string.task_editor_edit_title)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = { onEvent(TaskEditorEvent.OnBackClick) },
            ) {
                offlineSwitch()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize(),
            ) {
                when (state) {
                    is TaskEditorUiState.Loading -> Loading()

                    is TaskEditorUiState.Editing -> Form(
                        form = state,
                        onEvent = onEvent,
                    )

                    is TaskEditorUiState.LoadFailed -> LoadFailed(
                        reason = state.reason,
                        onRetryClick = { onEvent(TaskEditorEvent.OnRetryClick) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Form(form: TaskEditorUiState.Editing, onEvent: (TaskEditorEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { onEvent(TaskEditorEvent.OnTitleChange(it)) },
            label = { Text(stringResource(Res.string.task_editor_field_title)) },
            isError = form.titleMissing,
            supportingText = { TitleHint(titleMissing = form.titleMissing) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.notes,
            onValueChange = { onEvent(TaskEditorEvent.OnNotesChange(it)) },
            label = { Text(stringResource(Res.string.task_editor_field_notes)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        // A task that has not been saved yet has nothing to mark as done.
        if (form.taskId != null) {
            DoneCheckbox(
                done = form.done,
                onDoneChange = { onEvent(TaskEditorEvent.OnDoneChange(it)) },
            )
        }

        Button(
            onClick = { onEvent(TaskEditorEvent.OnSaveClick) },
            enabled = !form.saving,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(Res.string.task_editor_save))
        }
    }
}

@Composable
private fun DoneCheckbox(done: Boolean, onDoneChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(
            value = done,
            role = Role.Checkbox,
            onValueChange = onDoneChange,
        ),
    ) {
        // The whole row is the control, so the box itself takes no taps and needs no label of its own.
        Checkbox(
            checked = done,
            onCheckedChange = null,
        )

        Text(
            text = stringResource(Res.string.task_editor_field_done),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp, end = 16.dp),
        )
    }
}

@Composable
private fun TitleHint(titleMissing: Boolean) {
    if (titleMissing) Text(stringResource(Res.string.task_editor_title_missing))
}

@Composable
private fun LoadFailed(reason: FailureReason, onRetryClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = failureText(reason),
            style = MaterialTheme.typography.bodyLarge,
        )

        Button(onClick = onRetryClick) {
            Text(stringResource(Res.string.task_editor_retry))
        }
    }
}

@Preview
@Composable
private fun TaskEditorScreenEditingPreview() {
    MaterialTheme {
        TaskEditorScreen(
            state = TaskEditorUiState.Editing(
                taskId = 1L,
                title = "",
                notes = "Ask for a morning slot.",
                done = true,
                titleMissing = true,
            ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}

@Preview
@Composable
private fun TaskEditorScreenLoadingPreview() {
    MaterialTheme {
        TaskEditorScreen(
            state = TaskEditorUiState.Loading,
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}

@Preview
@Composable
private fun TaskEditorScreenLoadFailedPreview() {
    MaterialTheme {
        TaskEditorScreen(
            state = TaskEditorUiState.LoadFailed(taskId = 1L, reason = FailureReason.Offline),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}
