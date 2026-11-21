# Dependency injection

Eventus needs no dependency-injection library, and works with any. Two facts make the wiring simple:

- **Handlers hold nothing about the screen.** State and effects arrive through `handle`, so a handler
  can be created fresh for each view model (`factory`) with no scope of its own.
- **The view model takes a `Collection` of handlers.** Adding an event never changes the view model.

## Give each screen a handler interface

One line per screen pays for itself:

```kotlin
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>

class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> {

    override val eventType = OnRefresh::class
    override val strategy = DispatchStrategy.DropIfBusy

    override suspend fun handle(event: OnRefresh, store: TaskListStore) { /* … */ }
}
```

Handlers get shorter, and your container gets a type that means "the handlers of this screen".

## Koin

```kotlin
val taskListModule = module {
    factoryOf(::OnScreenOpenedHandler) bind TaskListHandler::class
    factoryOf(::OnRefreshHandler) bind TaskListHandler::class
    factoryOf(::OnTaskClickHandler) bind TaskListHandler::class
    // …one line per handler

    viewModel { TaskListViewModel(handlers = getAll<TaskListHandler<*>>()) }
}
```

Do not ask Koin for `getAll<EventHandler<*, TaskListUiState, TaskListEffect>>()`. Koin looks
definitions up by class and ignores type arguments, so that call returns the handlers of every screen
in the app. The per-screen interface is what keeps them apart. Register the view model with
`viewModel { }`, never `single { }`: a singleton view model is never cleared, and its router would
never stop.

The handler lines need not be written by hand: `eventus-ksp` generates them. See
[handlers checked and wired by the build](handlers-checked-and-wired-by-the-build.md).

## Containers that resolve at compile time

Containers such as Metro and Dagger keep type arguments, so there you can multibind each handler into
`Set<EventHandler<*, TaskListUiState, TaskListEffect>>` and inject that set into the view model.

## By hand

```kotlin
TaskListViewModel(
    handlers = listOf(
        OnScreenOpenedHandler(tasks),
        OnRefreshHandler(tasks),
        OnTaskClickHandler(),
    ),
)
```

---

[Back to the README](../README.md#documentation)
