# Handling failures

**Let event handlers throw.** An event handler that cannot do its job should not catch the exception
to turn it into a message:

> [!TIP]
> **Do:** let the failure through.
>
> ```kotlin
> override suspend fun handle(event: OnSaveClick, store: CounterStore) {
>     counters.save(store.current.count)
>     store.emit(CounterEffect.Saved)
> }
> ```

> [!CAUTION]
> **Don't:** catch it in every event handler to say what went wrong. Each event handler would repeat
> the same lines, and one that forgets them fails in silence.
>
> ```kotlin
> override suspend fun handle(event: OnSaveClick, store: CounterStore) {
>     try {
>         counters.save(store.current.count)
>         store.emit(CounterEffect.Saved)
>     } catch (error: OfflineException) {
>         store.emit(CounterEffect.ShowFailure(FailureReason.Offline))
>     }
> }
> ```

Catch a failure only to recover from it, for example to try another source or to
[dispatch another event](dispatching-an-event-from-a-handler.md).

The router gives every failure to the screen's `ErrorHandler`, with the event that was being handled
and the screen's store. Write one per screen, as a class of its own:

```kotlin
class CounterErrorHandler(
    private val logger: Logger,
) : ErrorHandler<CounterEvent, CounterUiState, CounterEffect> {

    override suspend fun handle(
        event: CounterEvent,
        error: Throwable,
        store: ScreenStore<CounterUiState, CounterEffect>,
    ) {
        logger.warn("$event failed", error)

        store.emit(CounterEffect.ShowFailure(error.toFailureReason()))
    }
}
```

The view model passes the error handler to the router, so the view model's definition does not grow
with the handling:

```kotlin
class CounterViewModel(
    handlers: Collection<EventHandler<*, CounterUiState, CounterEffect>>,
    errorHandler: CounterErrorHandler,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<CounterEvent, CounterUiState, CounterEffect> by EventRouter(
        scope = scope,
        initialState = CounterUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
    )
```

Like an event handler, an error handler takes what it depends on in its constructor, holds nothing
about the screen, and has a test of its own: inside a coroutine test, create a store, call `handle`
and look at the store. The
effect the error handler emits reaches the UI like any other. For handling short enough not to need
a class, a lambda works in its place, as in the quick start.

- **Turn the exception into your own type there**, such as a `FailureReason` enum, and let the UI
  choose the words from its resources. An exception's message is written for developers and is not
  translated; do not put it on screen.
- **A failure that must stay on screen is state.** Either update the state from the error handler,
  or catch the specific exception in the event handler and set a "failed, retry" state there, as the
  sample's editor does when a task cannot be loaded.
- **Cancellation is never a failure.** The router does not report a run cancelled by `Latest` or by
  the scope ending.
- **The error handler is a suspending function.** It can wait, for example for
  [an event it dispatched](dispatching-an-event-from-a-handler.md#dispatching-from-an-error-handler). The
  event that failed stays active until the error handler returns.
- **The error handler must not throw.** A failure there is a mistake in the code: it reaches the
  `CoroutineExceptionHandler` of your scope as an uncaught error.
- **Without an error handler, the router swallows nothing.** The exception reaches the
  `CoroutineExceptionHandler` of your scope or, if it has none, the platform's handler for uncaught
  exceptions. On Android that ends the app, which is the right default for an error nobody decided
  how to handle.

Whatever happens to one event handler, the others keep running and the router keeps taking events.

---

[Back to the README](../README.md#documentation)
