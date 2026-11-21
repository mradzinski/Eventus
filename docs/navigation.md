# Navigation

Navigation is an effect. A handler says where to go and passes only an id; the UI layer that owns the
back stack does the navigating.

```kotlin
class OnTaskClickHandler : EventHandler<OnTaskClick, TaskListUiState, TaskListEffect> {

    override val eventType = OnTaskClick::class

    override suspend fun handle(event: OnTaskClick, store: ScreenStore<TaskListUiState, TaskListEffect>) {
        store.emit(TaskListEffect.OpenEditor(taskId = event.taskId))
    }
}
```

The route turns the effect into a callback, and whoever owns the back stack implements the callback.
The sample does this with Navigation 3: see `AppNavigation.kt` and `TaskListRoute.kt`.

---

[Back to the README](../README.md#documentation)
