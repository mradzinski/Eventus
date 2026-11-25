# Choosing the coroutine scope

Every router is created with a coroutine scope. The scope decides two things:

- **How long the router lives.** The router stops when the scope is cancelled, and whoever creates
  the scope must cancel it.
- **Which thread the event handlers run on.**

Which scope to pass depends on where the router lives:

| The router lives in | Pass | Who cancels it |
|---|---|---|
| A view model | `routerScope()`, shared with the view model | The view model, when it is cleared |
| A class of your own | `routerScope()`, kept by that class | You, when the screen goes away |
| A test | The test's `backgroundScope` | The test, when it ends |

## Contents

- [The `routerScope()` function](#the-routerscope-function)
- [Giving a scope to a router in a view model](#giving-a-scope-to-a-router-in-a-view-model)
- [Giving a scope to a router outside a view model](#giving-a-scope-to-a-router-outside-a-view-model)
- [Giving a scope to a router in a test](#giving-a-scope-to-a-router-in-a-test)
- [Threads](#threads)
  - [An event from the main thread is handled at once](#an-event-from-the-main-thread-is-handled-at-once)
  - [Slow work belongs in the repository](#slow-work-belongs-in-the-repository)
  - [An event can be sent from any thread](#an-event-can-be-sent-from-any-thread)
  - [Platforms without a main thread dispatcher](#platforms-without-a-main-thread-dispatcher)

## The `routerScope()` function

`routerScope()` is a function of the library. It is not a class and not an extension: you call it
by its name, with no arguments, and it returns a plain `CoroutineScope` from `kotlinx.coroutines`.

```kotlin
fun routerScope(): CoroutineScope
```

- **Each call creates a new scope.** Two view models that each call it share nothing.
- **Event handlers in it run on the main thread**, and an event handler that fails does not stop the
  others.
- **Nothing cancels it for you.** It is an ordinary scope, so whoever holds it cancels it.

## Giving a scope to a router in a view model

Make `routerScope()` the default value of a `scope` parameter, and pass that parameter to both the
view model and the router. `ViewModel(scope)` cancels the scope when the view model is cleared, so
there is nothing for you to cancel.

```kotlin
class ProfileViewModel(
    handlers: Collection<ProfileHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<ProfileEvent, ProfileUiState, ProfileEffect> by EventRouter(
        scope = scope,
        initialState = ProfileUiState(),
        handlers = handlers,
    )
```

The app creates the view model without naming a scope:

```kotlin
val viewModel = ProfileViewModel(handlers)
```

On Android the view model, and so the router and its state, survives configuration changes.

## Giving a scope to a router outside a view model

A router does not need a view model. A class of your own can hold one, such as a presenter shared
with a SwiftUI screen, or the model of a desktop window. That class then owns the scope: cancel it
when the screen goes away, or every event handler that has not returned keeps running.

```kotlin
class ProfilePresenter(
    handlers: Collection<ProfileHandler<*>>,
    private val scope: CoroutineScope = routerScope(),
) : EventRouter<ProfileEvent, ProfileUiState, ProfileEffect> by EventRouter(
        scope = scope,
        initialState = ProfileUiState(),
        handlers = handlers,
    ) {

    /** Call this when the screen goes away. */
    fun close() = scope.cancel()
}
```

## Giving a scope to a router in a test

Pass the test's `backgroundScope` in place of the default:

```kotlin
val viewModel = ProfileViewModel(handlers, scope = backgroundScope)
```

See [testing a view model](testing.md#testing-a-view-model) for the whole test and for why it is
`backgroundScope` and not the test's own scope.

## Threads

With `routerScope()`, event handlers run on the main thread. The three points below follow from
that.

### An event from the main thread is handled at once

When the UI sends an event from the main thread, its event handler starts before `onEvent` returns.
That is what keeps a text field in step with its state: the event handler has written the new text
before the field is drawn again.

```kotlin
TextField(
    value = state.name,
    onValueChange = { viewModel.onEvent(ProfileEvent.OnNameChange(it)) },
)
```

### Slow work belongs in the repository

An event handler on the main thread must not block it. Make each repository safe to call from the
main thread, by having it move its own work to another dispatcher:

```kotlin
class DiskNoteRepository(
    private val files: NoteFiles,
    private val background: CoroutineDispatcher,
) : NoteRepository {

    override suspend fun load(id: String): Note = withContext(background) { files.read(id) }
}
```

Event handlers then stay simple, and never name a dispatcher:

```kotlin
class OnNoteOpenedHandler(private val notes: NoteRepository) : NoteHandler<OnNoteOpened> {

    override val eventType = OnNoteOpened::class

    override suspend fun handle(event: OnNoteOpened, store: NoteStore) {
        val note = notes.load(event.id)

        store.update { it.copy(note = note) }
    }
}
```

### An event can be sent from any thread

`onEvent` is safe to call from any thread. A callback that arrives on a background thread sends its
event directly, and the event handler still runs on the main thread:

```kotlin
socket.onMessage { message -> viewModel.onEvent(ChatEvent.OnMessageReceived(message)) }
```

### Platforms without a main thread dispatcher

Desktop apps need `kotlinx-coroutines-swing`, which provides the main dispatcher:

```kotlin
// build.gradle.kts of the desktop app
jvmMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}
```

On a platform that has no main dispatcher at all, event handlers run on the thread that sends the
event.

---

[Back to the README](../README.md#documentation)
