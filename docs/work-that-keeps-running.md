# Work that keeps running

Eventus knows nothing about a user interface. An event is something that happened and must be handled.
Not every handler changes the state. For example: the handler of an analytics event might only report
the event. So nothing in the router starts or stops because a screen is shown or hidden.

A handler that does not return by itself, such as one that collects a flow, runs until the router's
scope is cancelled. In a view model, that is when the view model is cleared. Send its event once, from
the view model, when it is created:

```kotlin
sealed interface TaskListEvent : UiEvent {

    data object OnScreenOpened : TaskListEvent
}

class OnScreenOpenedHandler(
    private val tasks: TaskRepository,
) : EventHandler<OnScreenOpened, TaskListUiState, TaskListEffect> {

    override val eventType = OnScreenOpened::class
    override val strategy = DispatchStrategy.Latest   // sent again, it starts over; it never runs twice

    override suspend fun handle(event: OnScreenOpened, store: ScreenStore<TaskListUiState, TaskListEffect>) {
        tasks.observeTasks().collect { loaded -> store.update { it.copy(tasks = loaded) } }
    }
}

class TaskListViewModel(/* … */) : ViewModel(scope), EventRouter</* … */> by EventRouter(/* … */) {

    init {
        onEvent(TaskListEvent.OnScreenOpened)
    }
}
```

The handler then has the lifecycle of the view model: it starts when the view model is created and is
cancelled when the view model is cleared.

If such a handler fails, it has ended, and nothing starts it again by itself. The failure reaches the
screen's `ErrorHandler` like any other. To start over, send the event again, for example from a retry
button.

## Work that must stop while the UI is hidden

Nothing is needed to clean up: every handler is cancelled with the view model. This part is only for
work that must pause sooner, while the view model is still alive but its screen is hidden. A view model
outlives a hidden screen, for example when the app is in the background, and some sources are too
costly to leave open that long: location, a socket, polling.

The router cannot know that the screen is hidden, so the UI notifies it, with an ordinary event of
your own. One event that carries the new visibility, handled with `Latest`, is enough: each new event
cancels the run before it.

```kotlin
data class OnVisibilityChange(val visible: Boolean) : MapEvent

class OnVisibilityChangeHandler(
    private val location: LocationSource,
) : EventHandler<OnVisibilityChange, MapUiState, MapEffect> {

    override val eventType = OnVisibilityChange::class
    override val strategy = DispatchStrategy.Latest

    override suspend fun handle(event: OnVisibilityChange, store: ScreenStore<MapUiState, MapEffect>) {
        if (!event.visible) return   // the run that was following the location is already cancelled

        location.updates().collect { position -> store.update { it.copy(position = position) } }
    }
}
```

The UI sends that event when it becomes visible and when it stops being visible. In Compose:

```kotlin
LifecycleStartEffect(viewModel) {
    viewModel.onEvent(MapEvent.OnVisibilityChange(visible = true))

    onStopOrDispose { viewModel.onEvent(MapEvent.OnVisibilityChange(visible = false)) }
}
```

---

[Back to the README](../README.md#documentation)
