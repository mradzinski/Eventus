# Navigating

Navigation is an effect. An event handler says where to go and passes only an id; the UI layer that
owns the back stack does the navigating.

```kotlin
class OnTaskClickHandler : EventHandler<OnTaskClick, TaskListUiState, TaskListEffect> {

    override val eventType = OnTaskClick::class

    override suspend fun handle(event: OnTaskClick, store: ScreenStore<TaskListUiState, TaskListEffect>) {
        store.emit(TaskListEffect.OpenEditor(taskId = event.taskId))
    }
}
```

> [!TIP]
> **Do:** carry only what the next screen needs to find its data: an id.
>
> ```kotlin
> data class OpenEditor(val taskId: Long) : TaskListEffect
> ```

> [!CAUTION]
> **Don't:** carry the object itself. The next screen would show a copy that is out of date as soon
> as the task changes.
>
> ```kotlin
> data class OpenEditor(val task: Task) : TaskListEffect
> ```

The route turns the effect into a callback, and whoever owns the back stack implements the callback.
The sample does this with Navigation 3: see `AppNavigation.kt` and `TaskListRoute.kt`.

---

[Back to the README](../README.md#documentation)
