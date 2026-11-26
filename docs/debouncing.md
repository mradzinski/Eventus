# Debouncing

To wait until the user has stopped typing, sliding or resizing before doing the expensive part, use
the `Latest` strategy and put a `delay` where the event handler should pause:

```kotlin
class OnSearchChangeHandler(
    private val tasks: TaskRepository,
) : EventHandler<OnSearchChange, TaskListUiState, TaskListEffect> {

    override val eventType = OnSearchChange::class
    override val strategy = DispatchStrategy.Latest<OnSearchChange>()

    override suspend fun handle(event: OnSearchChange, store: ScreenStore<TaskListUiState, TaskListEffect>) {
        store.update { it.copy(query = event.query) }    // at once: the field shows the keystroke

        delay(300.milliseconds)                          // a new keystroke cancels the run here

        val results = tasks.search(event.query)          // only the last keystroke gets this far

        store.update { it.copy(results = results) }
    }
}
```

`Latest` is what makes the `delay` a debounce: each new event cancels the run that is still waiting,
so only the run of the last event reaches the code after the pause. If the search itself is still
under way when the next keystroke arrives, it is cancelled too, so an old result does not replace a
newer one. The router keeps no reference to a cancelled run, so the run and everything it held can
be collected as soon as it has stopped.

Cancellation takes effect where the handler is suspended, so this relies on `tasks.search` being
cancellable, as suspending functions from coroutine-based libraries are. If `tasks.search` wraps
something that cannot be cancelled (a callback API, a blocking call), the call runs to its end, and
the old run then goes on and stores its stale result. Check before storing in that case:

```kotlin
val results = tasks.search(event.query)

currentCoroutineContext().ensureActive()   // stops here if a newer event has cancelled this run
store.update { it.copy(results = results) }
```

Two things to get right:

- **Update what the user sees before the `delay`, not after.** A text field shows the text held in
  the state. This is why debouncing is a line inside the handler and not a setting on it: the
  handler decides which part is immediate and which part waits.
- **Use `Latest`, not the default.** With `Concurrent`, nothing cancels the earlier runs: every
  keystroke searches, just 300 ms late.

The first of the two, in code:

> [!TIP]
> **Do:** store the text first, then wait.
>
> ```kotlin
> store.update { it.copy(query = event.query) }
>
> delay(300.milliseconds)
> ```

> [!CAUTION]
> **Don't:** wait before storing the text. The field falls behind the keyboard and drops characters.
>
> ```kotlin
> delay(300.milliseconds)
>
> store.update { it.copy(query = event.query) }
> ```

In a test the wait costs nothing, because the test moves its own clock. Check the state before the
pause and after it:

```kotlin
@Test
fun `it shows the text at once and searches after the pause`() = runEventHandlerTest(
    handler = OnSearchChangeHandler(tasks),
    initialState = TaskListUiState(),
) {
    dispatch(OnSearchChange("den"))

    state.query shouldBe "den"
    state.results shouldBe emptyList()

    advanceBy(300.milliseconds)

    state.results shouldBe listOf(dentist)
}
```

See [testing an event handler that waits](testing.md#testing-an-event-handler-that-waits).

For a button that must not act twice, debouncing is the wrong tool: it would make the first tap wait.
Use `DropIfAlreadyRunning`, which runs the first tap at once and ignores the rest until it has
finished.

---

[Back to the README](../README.md#documentation)
