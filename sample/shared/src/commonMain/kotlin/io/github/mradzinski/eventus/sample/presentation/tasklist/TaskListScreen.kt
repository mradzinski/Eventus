package io.github.mradzinski.eventus.sample.presentation.tasklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mradzinski.eventus.sample.data.Task
import io.github.mradzinski.eventus.sample.presentation.common.AppTopBar
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.task_list_add
import io.github.mradzinski.eventus.sample.resources.task_list_empty
import io.github.mradzinski.eventus.sample.resources.task_list_mark_done
import io.github.mradzinski.eventus.sample.resources.task_list_no_matches
import io.github.mradzinski.eventus.sample.resources.task_list_refresh
import io.github.mradzinski.eventus.sample.resources.task_list_search
import io.github.mradzinski.eventus.sample.resources.task_list_title
import org.jetbrains.compose.resources.stringResource

/** The widest the content gets, so the list stays readable on a tablet or a desktop window. */
private val MaxContentWidth = 640.dp

/** The stateless half of the task list: it draws [state] and reports what the user does through [onEvent]. */
@Composable
internal fun TaskListScreen(
    state: TaskListUiState,
    onEvent: (TaskListEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    offlineSwitch: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(Res.string.task_list_title)) {
                RefreshButton(onEvent)

                offlineSwitch()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AddTaskButton(onEvent) },
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
                RefreshIndicator(refreshing = state.refreshing)

                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(TaskListEvent.OnSearchChange(it)) },
                    label = { Text(stringResource(Res.string.task_list_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when (val content = state.content) {
                    is TaskListUiState.Content.Loading -> Loading()

                    is TaskListUiState.Content.Loaded -> {
                        val matchingTasks = remember(content, state.appliedQuery) {
                            content.matching(state.appliedQuery)
                        }

                        Tasks(
                            tasks = matchingTasks,
                            appliedQuery = state.appliedQuery,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskButton(onEvent: (TaskListEvent) -> Unit) {
    FloatingActionButton(onClick = { onEvent(TaskListEvent.OnAddClick) }) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(Res.string.task_list_add),
        )
    }
}

/** Fetches the tasks from the server again. Only the list has it, so only the list puts it in the bar. */
@Composable
private fun RefreshButton(onEvent: (TaskListEvent) -> Unit) {
    IconButton(onClick = { onEvent(TaskListEvent.OnRefresh) }) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = stringResource(Res.string.task_list_refresh),
        )
    }
}

/** Keeps its height while idle, so the list does not jump when a refresh starts. */
@Composable
private fun RefreshIndicator(refreshing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
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
private fun Tasks(
    tasks: List<Task>,
    appliedQuery: String,
    onEvent: (TaskListEvent) -> Unit,
) {
    if (tasks.isEmpty()) {
        NoTasks(appliedQuery = appliedQuery)

        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = tasks,
            key = { it.id },
        ) { task ->
            TaskRow(
                task = task,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun NoTasks(appliedQuery: String) {
    val message = if (appliedQuery.isBlank()) {
        stringResource(Res.string.task_list_empty)
    } else {
        stringResource(Res.string.task_list_no_matches, appliedQuery)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(task: Task, onEvent: (TaskListEvent) -> Unit) {
    val markDoneLabel = stringResource(Res.string.task_list_mark_done, task.title)

    val titleDecoration = if (task.done) {
        TextDecoration.LineThrough
    } else {
        TextDecoration.None
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(TaskListEvent.OnTaskClick(task.id)) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { done -> onEvent(TaskListEvent.OnTaskDoneChange(taskId = task.id, done = done)) },
            modifier = Modifier.semantics { contentDescription = markDoneLabel },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = titleDecoration,
            )

            if (task.notes.isNotBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun TaskListScreenLoadedPreview() {
    MaterialTheme {
        TaskListScreen(
            state = TaskListUiState(
                refreshing = true,
                content = TaskListUiState.Content.Loaded(
                    tasks = listOf(
                        Task(id = 1L, title = "Book the dentist", notes = "Ask for a morning slot.", done = false),
                        Task(id = 2L, title = "Water the plants", notes = "", done = true),
                    ),
                ),
            ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}

@Preview
@Composable
private fun TaskListScreenLoadingPreview() {
    MaterialTheme {
        TaskListScreen(
            state = TaskListUiState(),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}

@Preview
@Composable
private fun TaskListScreenNoMatchesPreview() {
    MaterialTheme {
        TaskListScreen(
            state = TaskListUiState(
                query = "taxes",
                appliedQuery = "taxes",
                content = TaskListUiState.Content.Loaded(tasks = emptyList()),
            ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
            offlineSwitch = {},
        )
    }
}
