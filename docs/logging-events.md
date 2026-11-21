# Logging events

`EventRouter` is an interface, so anything that should see every event is a wrapper around it and
needs nothing from the library:

```kotlin
class LoggingRouter<E : UiEvent, S : UiState, F : UiEffect>(
    private val delegate: EventRouter<E, S, F>,
    private val log: (String) -> Unit,
) : EventRouter<E, S, F> by delegate {

    override fun onEvent(event: E) {
        log("event: $event")

        delegate.onEvent(event)
    }
}
```

Use it where the router is created, by wrapping the router the view model delegates to. Nothing else in
the screen changes:

```kotlin
class TaskListViewModel(
    handlers: Collection<TaskListHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by LoggingRouter(
        delegate = EventRouter(
            scope = scope,
            initialState = TaskListUiState(),
            handlers = handlers,
        ),
        log = { println(it) },
    )
```

It logs each event as it is sent and then passes it on, so an event that `DropIfBusy` ignores is logged
too. Failures do not pass through it: log those in the screen's `ErrorHandler`.

---

[Back to the README](../README.md#documentation)
