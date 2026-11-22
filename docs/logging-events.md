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

Use `LoggingRouter` where the router is created, by wrapping the router the view model delegates to.
Nothing else in the screen changes:

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

`LoggingRouter` logs each event when the event is sent and then passes the event on, so it also logs
an event that `DropIfAlreadyRunning` ignores. Failures do not pass through `LoggingRouter`: log those
in the screen's `ErrorHandler`.

---

[Back to the README](../README.md#documentation)
