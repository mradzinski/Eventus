# Using the Compose helpers

`eventus-compose` is an optional artifact with two helpers for a Compose UI. The library itself
knows nothing about a UI; the helpers live on the UI's side.

## Contents

- [Installing](#installing)
- [Performing effects](#performing-effects)
  - [Performing effects without the helper](#performing-effects-without-the-helper)
- [Notifying the router that the screen is visible](#notifying-the-router-that-the-screen-is-visible)

## Installing

```kotlin
commonMain.dependencies {
    implementation("io.github.mradzinski:eventus-compose:1.0.0-rc02")
}
```

The artifact has every target of the library except `iosX64`, which Compose Multiplatform does not
publish for. On desktop, add `kotlinx-coroutines-swing`, which provides the main dispatcher.

Collecting the state needs no helper. Use the lifecycle library's `collectAsStateWithLifecycle()`.

## Performing effects

`collectEffects` performs the effects of a router:

```kotlin
@Composable
fun NoteRoute(viewModel: NoteViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.collectEffects { effect ->
        when (effect) {
            is NoteEffect.ShowSaved -> snackbarHostState.showSnackbar("Saved")
            is NoteEffect.Close -> onClose()
        }
    }

    NoteScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}
```

An effect is something only the UI can do, so `collectEffects` performs an effect only while there
is a UI to do it:

- **`collectEffects` collects effects while the screen is started.** An effect emitted while the
  screen is stopped, with the app in the background for example, waits in the router until the
  screen starts again.
- **Each effect runs in a coroutine of its own.** One that suspends, such as showing a snackbar,
  does not hold up the next one.
- **The lambda may change between recompositions.** The helper always calls the latest lambda, so
  the lambda can use `onClose` and the like directly.

Call `collectEffects` once per router. A router gives each effect to one collector, so two
collectors would each see only some of them.

### Performing effects without the helper

If you do not use `eventus-compose`, collect the effects yourself, inside `repeatOnLifecycle`:

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current

LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NoteEffect.ShowSaved -> snackbarHostState.showSnackbar("Saved")
                is NoteEffect.Close -> onClose()
            }
        }
    }
}
```

Do not leave `repeatOnLifecycle` out. A `LaunchedEffect` runs for as long as the composable is in the
composition, which includes the time the app is in the background. Without `repeatOnLifecycle`, an
effect emitted then is performed with nobody looking.

This block performs the effects one after another, so an effect that suspends, such as showing a
snackbar, holds up the next one. `collectEffects` runs each effect in a coroutine of its own.

## Notifying the router that the screen is visible

Nothing in a router stops because a screen is hidden; see
[keeping work running](keeping-work-running.md). For the few screens with work too costly to keep
running then, `notifyVisibility` sends an event of your own when the screen becomes visible and when it
stops being visible:

```kotlin
viewModel.notifyVisibility { visible -> ChatEvent.OnVisibilityChange(visible) }
```

Use one event type that carries the visibility, and handle it with the `Latest` strategy. The
event handler starts the work when the screen is visible and returns at once when it is not:

```kotlin
override suspend fun handle(event: OnVisibilityChange, store: ScreenStore<ChatUiState, ChatEffect>) {
    if (!event.visible) return   // the run that was reading the socket is already cancelled

    socket.messages().collect { message -> store.update { it.copy(messages = it.messages + message) } }
}
```

Two separate events, one for visible and one for hidden, would not work: a new event cancels the run
before it only within its own type.

For any other moment of the lifecycle, the lifecycle library already has a one-line API:

```kotlin
LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ChatEvent.OnResumed) }
```

---

[Back to the README](../README.md#documentation)
