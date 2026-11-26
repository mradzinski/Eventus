# Emitting an effect

An effect is something only the UI can do, once: show a message, navigate, vibrate. An event handler
asks for it by emitting the effect through the store, with `emit`:

```kotlin
class OnSaveClickHandler(
    private val counters: CounterRepository,
) : CounterHandler<OnSaveClick> {

    override val eventType = OnSaveClick::class

    override suspend fun handle(event: OnSaveClick, store: CounterStore) {
        counters.save(store.current.count)

        store.emit(CounterEffect.Saved)
    }
}
```

`emit` does not wait for the UI. It hands the effect to the store and the event handler goes on at
once. The store keeps the effect until the UI collects it.

Not sure whether something is an effect? See
[choosing between an effect and state](choosing-between-an-effect-and-state.md).

## Contents

- [Declaring the effects of a screen](#declaring-the-effects-of-a-screen)
- [Emitting from an error handler](#emitting-from-an-error-handler)
- [Performing the effects in the UI](#performing-the-effects-in-the-ui)
- [What you can rely on](#what-you-can-rely-on)
- [Testing an event handler that emits an effect](#testing-an-event-handler-that-emits-an-effect)

## Declaring the effects of a screen

Give the screen one sealed type that implements `UiEffect`, with one case for each thing the UI can
be asked to do:

```kotlin
sealed interface CounterEffect : UiEffect {

    data object Saved : CounterEffect
    data class ShowFailure(val reason: FailureReason) : CounterEffect
}
```

An effect carries what the UI needs to perform it and nothing else: an id to navigate to, the reason
for a failure. See [navigating](navigating.md) for the navigation case.

## Emitting from an error handler

An error handler receives the same store, so it emits an effect the same way. That is the usual
place to tell the user that something failed:

```kotlin
class CounterErrorHandler : ErrorHandler<CounterEvent, CounterUiState, CounterEffect> {

    override suspend fun handle(event: CounterEvent, error: Throwable, store: CounterStore) {
        store.emit(CounterEffect.ShowFailure(error.toFailureReason()))
    }
}
```

See [handling failures](handling-failures.md) for what reaches the error handler.

## Performing the effects in the UI

The UI collects the router's `effects` and performs each one. Collect inside `repeatOnLifecycle`, so
that an effect is performed only while the screen is started:

```kotlin
@Composable
fun CounterRoute(viewModel: CounterViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is CounterEffect.Saved -> { /* show a confirmation */ }
                    is CounterEffect.ShowFailure -> { /* show why */ }
                }
            }
        }
    }

    // …the screen
}
```

With [`eventus-compose`](using-the-compose-helpers.md), that block is one call:

```kotlin
viewModel.collectEffects { effect ->
    when (effect) {
        is CounterEffect.Saved -> { /* show a confirmation */ }
        is CounterEffect.ShowFailure -> { /* show why */ }
    }
}
```

Each effect goes to one collector, so collect the effects of a screen in one place:

> [!TIP]
> **Do:** collect once, at the top of the screen, and hand each effect to whatever performs it.
>
> ```kotlin
> @Composable
> fun CounterRoute(viewModel: CounterViewModel, onSaved: () -> Unit) {
>     val snackbars = remember { SnackbarHostState() }
>
>     viewModel.collectEffects { effect ->
>         when (effect) {
>             is CounterEffect.Saved -> onSaved()
>             is CounterEffect.ShowFailure -> snackbars.showSnackbar(effect.reason.toMessage())
>         }
>     }
>
>     // …the screen
> }
> ```

> [!CAUTION]
> **Don't:** collect in two places. The two collectors compete, and each effect reaches only one of
> them: a failure can arrive at the collector that only knows `Saved`, and nobody shows it.
>
> ```kotlin
> @Composable
> fun CounterRoute(viewModel: CounterViewModel, onSaved: () -> Unit) {
>     viewModel.collectEffects { effect ->
>         if (effect is CounterEffect.Saved) onSaved()
>     }
>
>     FailureMessages(viewModel)
> }
>
> @Composable
> fun FailureMessages(viewModel: CounterViewModel) {
>     val snackbars = remember { SnackbarHostState() }
>
>     viewModel.collectEffects { effect ->
>         if (effect is CounterEffect.ShowFailure) snackbars.showSnackbar(effect.reason.toMessage())
>     }
> }
> ```

## What you can rely on

- **Each effect is delivered once, to one collector.** A new collector does not get an effect that
  another collector already took.
- **Effects arrive in the order they were emitted.**
- **An effect waits for the UI.** An effect emitted while nothing collects is kept, however many
  there are: during a rotation, or while the app is in the background. The next collector gets it.
- **`emit` never suspends and never fails.** It can be called from any thread.
- **An effect that was emitted stays emitted.** It is still delivered when its event fails or is
  cancelled afterward.
- **An effect is lost only when the UI is cancelled while performing it.** A snackbar that was
  visible as the screen stopped is not shown again. Put a message the user must see in the state.
- **The reports name the event an effect came from**: `#3 OnSaveClick emitted Saved`. See
  [logging events](logging-events.md).

## Testing an event handler that emits an effect

`runEventHandlerTest` records every effect in `effects`, in order:

```kotlin
@Test
fun `saving tells the UI that the count was saved`() = runEventHandlerTest(
    handler = OnSaveClickHandler(FakeCounterRepository()),
    initialState = CounterUiState(count = 3),
) {
    dispatch(OnSaveClick)

    effects shouldContainExactly listOf(CounterEffect.Saved)
}
```

To test the effect of a failure, pass the screen's error handler as well:

```kotlin
@Test
fun `a save that fails tells the UI why`() = runEventHandlerTest(
    handler = OnSaveClickHandler(FakeCounterRepository(failing = true)),
    initialState = CounterUiState(count = 3),
    errorHandler = CounterErrorHandler(),
) {
    dispatch(OnSaveClick)

    effects shouldContainExactly listOf(CounterEffect.ShowFailure(FailureReason.Unknown))
}
```

See [testing](testing.md) for everything the block gives you.

---

[Back to the README](../README.md#documentation)
