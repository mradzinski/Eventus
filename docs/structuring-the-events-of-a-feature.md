# Structuring the events of a feature

A feature is often one Gradle module with several screens: a task list and a task editor, for
example. Each screen has its own view model, and so its own router, state and effects. The events
can still be one type for the whole feature.

## Contents

- [Giving each screen a sealed type](#giving-each-screen-a-sealed-type)
- [Adding a view model above the screens](#adding-a-view-model-above-the-screens)
- [What this structure gives you](#what-this-structure-gives-you)
- [What to keep in mind](#what-to-keep-in-mind)

## Giving each screen a sealed type

Give the feature one sealed event type, and give each screen a sealed type inside it:

```kotlin
sealed interface TasksEvent : UiEvent {

    sealed interface List : TasksEvent {
        data object OnRefresh : List
        data class OnTaskClick(val taskId: String) : List
    }

    sealed interface Editor : TasksEvent {
        data class OnTitleChange(val title: String) : Editor
        data object OnSaveClick : Editor
    }
}
```

Each screen then names its own part of the type, in its handler interface and in its view model:

```kotlin
interface TaskListHandler<E : TasksEvent.List> : EventHandler<E, TaskListUiState, TaskListEffect>

interface TaskEditorHandler<E : TasksEvent.Editor> : EventHandler<E, TaskEditorUiState, TaskEditorEffect>

typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>

class TaskListViewModel(
    handlers: Collection<TaskListHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TasksEvent.List, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
    )
```

An event handler is written as on any other screen:

```kotlin
class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<TasksEvent.List.OnRefresh> {

    override val eventType = TasksEvent.List.OnRefresh::class

    override suspend fun handle(event: TasksEvent.List.OnRefresh, store: TaskListStore) { /* … */ }
}
```

## Adding a view model above the screens

A feature can have a view model of its own above its screens, for what belongs to neither, such as
its back button. Give that view model's events a sealed type too:

```kotlin
sealed interface TasksEvent : UiEvent {

    sealed interface List : TasksEvent { /* … */ }

    sealed interface Editor : TasksEvent { /* … */ }

    sealed interface Feature : TasksEvent {
        data object OnBackButtonClick : Feature
    }
}

class TasksViewModel(/* … */) : EventRouter<TasksEvent.Feature, TasksUiState, TasksEffect> by /* … */
```

The same goes for a part of one screen that has its own view model: it gets its own sealed type.

## What this structure gives you

- **The compiler keeps each screen to its own events.** `TaskListViewModel.onEvent` takes a
  `TasksEvent.List`, so sending it `TasksEvent.Editor.OnSaveClick` does not compile.
- **The build-time check still covers every event.** [`eventus-ksp`](checking-and-wiring-event-handlers-at-build-time.md)
  looks inside nested sealed types, so each event of each screen needs exactly one event handler.
- **The feature has one type to show.** Code that works with every event of the feature, such as an
  [observer that logs them](logging-events.md), takes `TasksEvent`.

## What to keep in mind

- **A view model routes its own sealed type, never the feature's.** A router of `TasksEvent` accepts
  every event of the feature and throws for one it has no event handler for. The build-time check
  fails the build for such a view model. See [several view models in one
  feature](key-concepts.md#several-view-models-in-one-feature).
- **An event belongs to one screen.** An event has exactly one handler, so two screens cannot both
  handle the same event class. When two screens react to the same action, such as a tap on Back, give
  each screen its own event.
- **Each screen keeps its own state.** A router creates and owns the state of its screen. For one
  screen to act on what another did, see [reaching another screen](reaching-another-screen.md).

---

[Back to the README](../README.md#documentation)
