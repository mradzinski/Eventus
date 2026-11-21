[![Maven Central](https://img.shields.io/maven-central/v/io.github.mradzinski/eventus?style=flat-square)](https://central.sonatype.com/artifact/io.github.mradzinski/eventus)
[![Build](https://img.shields.io/github/actions/workflow/status/mradzinski/eventus/build.yml?branch=main&style=flat-square&label=build)](https://github.com/mradzinski/eventus/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/codecov/c/github/mradzinski/eventus?style=flat-square&label=coverage)](https://codecov.io/gh/mradzinski/eventus)

# Eventus

An event router for MVVM screens on Kotlin Multiplatform. The UI sends events, each event goes to
its own small handler class, and the screen's state and one-time effects come back as flows. It
runs on **Android, iOS, Desktop and Web** from common code.

- One event, one handler class, one test class: a screen with fifty events is fifty small files, not one
  view model with fifty functions
- Five ways to run a handler when its event arrives again: at once, latest only, in order, ignore while
  busy, or for as long as the screen is visible
- The view model extends nothing from this library: it delegates to a router
- State is one immutable value in a `StateFlow`; effects are delivered once, and kept until something
  collects them
- Handlers get the screen's state from the router, so no dependency-injection scope can be set up wrong
- A handler that fails does not stop the screen, and every failure reaches one place
- Optimistic updates in one call: show the result at once, put it back if the work fails
- Depends only on `kotlinx.coroutines`: no Compose, no lifecycle, no dependency-injection library
- Safe to call from any thread

## Contents

- [Before you begin](#before-you-begin)
- [Install](#install)
- [Quick start](#quick-start)
- [Key concepts](#key-concepts)
- [Dispatch strategies](#dispatch-strategies)
- [Debouncing](#debouncing)
- [Optimistic updates](#optimistic-updates)
- [Effect or state?](#effect-or-state)
- [Failures](#failures)
- [The screen's lifecycle](#the-screens-lifecycle)
- [Navigation](#navigation)
- [Reaching another screen](#reaching-another-screen)
- [Dependency injection](#dependency-injection)
- [Handlers checked and wired by the build](#handlers-checked-and-wired-by-the-build)
- [Testing](#testing)
- [The coroutine scope](#the-coroutine-scope)
- [Logging events](#logging-events)
- [Where Eventus sits in your app](#where-eventus-sits-in-your-app)
- [When Eventus is too much](#when-eventus-is-too-much)
- [What Eventus does not do](#what-eventus-does-not-do)
- [Project layout](#project-layout)
- [Changelog](#changelog)
- [License](#license)

## Before you begin

- **Kotlin 2.4.21 or newer.** The library is built with it; older compilers are not supported.
- **`kotlinx-coroutines` 1.11.0 or newer**, which the library brings in by itself.
- Targets: `android` (minSdk 23), `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `js`, `wasmJs`.
- The examples use the multiplatform `androidx.lifecycle` `ViewModel` and Compose, because that is how
  most apps will use the library. Neither is required.

## Install

```kotlin
// build.gradle.kts of the module that holds your screens
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mradzinski:eventus:1.0.0-rc01")
        }
    }
}
```

Two more artifacts are optional: `eventus-ksp`, which finds a missing handler when the app is built, and
`eventus-koin`, which lets it write your Koin module of handlers. See
[handlers checked and wired by the build](#handlers-checked-and-wired-by-the-build).

## Quick start

**1. Describe the screen.** Its state, the events the UI can send and the effects the UI must
perform, each as a sealed type or a data class of your own.

```kotlin
sealed interface CounterEvent : UiEvent {

    data object OnIncrementClick : CounterEvent
    data object OnSaveClick : CounterEvent
}

data class CounterUiState(val count: Int = 0) : UiState

sealed interface CounterEffect : UiEffect {

    data object Saved : CounterEffect
    data class ShowFailure(val reason: FailureReason) : CounterEffect
}
```

**2. Write one handler per event.** A handler takes what it really depends on in its constructor.
The screen's state and effects come in with each call, as the `store`.

```kotlin
class OnIncrementClickHandler : EventHandler<OnIncrementClick, CounterUiState, CounterEffect> {

    override val eventType = OnIncrementClick::class

    override suspend fun handle(event: OnIncrementClick, store: ScreenStore<CounterUiState, CounterEffect>) {
        store.update { it.copy(count = it.count + 1) }
    }
}

class OnSaveClickHandler(
    private val counters: CounterRepository,
) : EventHandler<OnSaveClick, CounterUiState, CounterEffect> {

    override val eventType = OnSaveClick::class
    override val strategy = DispatchStrategy.DropIfBusy   // a second tap while saving is ignored

    override suspend fun handle(event: OnSaveClick, store: ScreenStore<CounterUiState, CounterEffect>) {
        counters.save(store.current.count)   // throws when it fails: the screen's ErrorHandler takes it
        store.emit(CounterEffect.Saved)
    }
}
```

**3. Give the router to the UI through your view model.** The view model has no logic. It delegates
`state`, `effects` and `onEvent` to the router.

```kotlin
class CounterViewModel(
    handlers: Collection<EventHandler<*, CounterUiState, CounterEffect>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<CounterEvent, CounterUiState, CounterEffect> by EventRouter(
        scope = scope,
        initialState = CounterUiState(),
        handlers = handlers,
        errorHandler = { _, error, store -> store.emit(CounterEffect.ShowFailure(error.toFailureReason())) },
    )
```

**4. Use it from the UI.** Draw the state, send events, perform effects.

```kotlin
@Composable
fun CounterRoute(viewModel: CounterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CounterEffect.Saved -> { /* show a confirmation */ }
                is CounterEffect.ShowFailure -> { /* show why */ }
            }
        }
    }

    CounterScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}
```

## Key concepts

| Type | What it is |
|---|---|
| `UiState` | The state of one screen: everything the UI needs to draw it, as one immutable value. |
| `UiEvent` | Something the UI tells the screen: a tap, a text change, the screen becoming visible. |
| `UiEffect` | Something the UI must do once and then forget: show a message, navigate. |
| `EventHandler<E, S, F>` | The logic for one event type `E` of a screen whose state is `S` and whose effects are `F`. |
| `ErrorHandler<E, S, F>` | What the screen does when one of its event handlers fails. One per screen. |
| `ScreenStore<S, F>` | The state and effects of one screen. The router creates it and hands it to every handler, which read it (`current`), change it (`update`, `updateOptimistically`) and send effects through it (`emit`). |
| `EventRouter<E, S, F>` | What the UI sees: `state`, `effects` and `onEvent`. Created by the `EventRouter(...)` function. |
| `DispatchStrategy` | How a handler runs when its event arrives again while it is still running. |
| `ScreenStoppedEvent` | A `UiEvent` that also tells the router the screen is no longer visible. |
| `routerScope()` | Creates the coroutine scope a view model and its router share. |

Three rules follow from how these fit together:

- **Declare a screen's events as one sealed type.** The router matches each event by its exact
  class, so a handler registered for a parent type does not receive the events of its subtypes.
- **Keep the state immutable.** `store.update { it.copy(...) }` builds a new value. A value that was
  changed in place (an item added to a list the state already holds) is equal to the old one, so it
  is never emitted and the UI never sees the change.
- **The UI never gets the `ScreenStore`.** It reads `state` and `effects` and calls `onEvent`. Only
  handlers, and the error handler, write to the store.

If your app already has types called `UiState`, `UiEvent` or `UiEffect`, import these with an alias
(`import io.github.mradzinski.eventus.UiState as EventusUiState`) or replace yours with them.

## Dispatch strategies

A handler chooses its strategy by overriding `strategy`. Each strategy applies per event type: two
different events never wait for, cancel or drop each other.

| Strategy | When the event arrives again during a run | Use it for |
|---|---|---|
| `Concurrent` (default) | Both runs go on at once. | Anything quick, and anything that may overlap. |
| `Latest` | The active run is cancelled and a new one starts. | Search boxes, sliders: input that makes the previous work pointless. See [debouncing](#debouncing). |
| `Sequential` | The event waits its turn. Events run in the order they were sent. | Toggles and counters, where order matters. |
| `DropIfBusy` | The event is ignored. | Submit, refresh, confirm: actions that must not run twice. |
| `WhileStarted` | The active run is replaced. | Observing data while the screen is visible. See [the screen's lifecycle](#the-screens-lifecycle). |

### What the router keeps, and for how long

The router lives exactly as long as the coroutine scope you give it, and it has nothing to close.
While it lives, this is everything it holds on to:

| Strategy | What is kept | Until |
|---|---|---|
| `Concurrent` | Nothing. A run is forgotten as soon as it starts. | — |
| `Latest`, `DropIfBusy` | The one active run of each event type. | That run ends. |
| `WhileStarted` | The one active run of each event type. | That run ends, is replaced, or a `ScreenStoppedEvent` cancels it. |
| `Sequential` | One queue and one idle worker coroutine per event type, created when that event is first sent. | The scope ends. They are **not** released when the queue empties. |

So a screen that uses `Sequential` for three event types holds three suspended coroutines until its
view model is cleared. That costs little, but it is the one thing the router keeps past the work it
was asked to do. A `Sequential` run that fails does not stop its queue: the next event still runs.

## Debouncing

To wait until the user has stopped typing, sliding or resizing before doing the expensive part, use
`DispatchStrategy.Latest` and put a `delay` where the handler should pause:

```kotlin
class OnSearchChangeHandler(
    private val tasks: TaskRepository,
) : EventHandler<OnSearchChange, TaskListUiState, TaskListEffect> {

    override val eventType = OnSearchChange::class
    override val strategy = DispatchStrategy.Latest

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
cancellable, as suspending functions from coroutine-based libraries are. If it wraps something that
cannot be cancelled (a callback API, a blocking call), the call runs to its end, the old run goes on
after it, and it would store its stale result. Check before storing in that case:

```kotlin
val results = tasks.search(event.query)

currentCoroutineContext().ensureActive()   // stops here if a newer event has cancelled this run
store.update { it.copy(results = results) }
```

Two things to get right:

- **Update what the user sees before the `delay`, not after.** A text field shows the text held in
  the state. If the handler waits before storing it, the field falls behind the keyboard and drops
  characters. This is why debouncing is a line inside the handler and not a setting on it: the
  handler decides which part is immediate and which part waits.
- **Use `Latest`, not the default.** With `Concurrent`, nothing cancels the earlier runs: every
  keystroke searches, just 300 ms late.

In a test the wait costs nothing. `runTest` skips delays, so calling `handle` directly returns at once
with the final state. To check both steps, collect `store.state` around the call:

```kotlin
@Test
fun `it shows the text at once and searches after the pause`() = runTest {
    val store = ScreenStore<TaskListUiState, TaskListEffect>(TaskListUiState())

    store.state.test {   // Turbine
        awaitItem()

        handler.handle(OnSearchChange("den"), store)

        assertEquals("den", awaitItem().query)
        assertEquals(listOf(dentist), awaitItem().results)
    }
}
```

For a button that must not act twice, debouncing is the wrong tool: it would make the first tap wait.
Use `DropIfBusy`, which runs the first tap at once and ignores the rest until it has finished.

## Optimistic updates

When the user ticks a box or taps a heart, show it at once and do the saving afterwards. If the saving
fails, put it back and say why. Where the "at once" happens depends on who owns the data.

### State that only the screen holds

If the screen loaded a value once and keeps its own copy, the handler changes that copy with
`updateOptimistically`:

```kotlin
class OnFavoriteChangeHandler(
    private val profiles: ProfileRepository,
) : EventHandler<OnFavoriteChange, ProfileUiState, ProfileEffect> {

    override val eventType = OnFavoriteChange::class
    override val strategy = DispatchStrategy.Sequential

    override suspend fun handle(event: OnFavoriteChange, store: ScreenStore<ProfileUiState, ProfileEffect>) {
        val profileId = store.current.profileId
        val favoriteBefore = store.current.favorite

        store.updateOptimistically(
            stateUpdate = { it.copy(favorite = event.favorite) },      // what the screen shows at once
            stateIfFailed = { it.copy(favorite = favoriteBefore) },    // what it goes back to
        ) {
            profiles.setFavorite(profileId, event.favorite)            // the work itself
        }
    }
}
```

- **`stateUpdate` and `stateIfFailed` only change what the screen shows.** Neither does any work, and
  neither can: they are not suspending, so a repository call inside them does not compile. The work
  goes in the block at the end and nowhere else.
- **If the work finishes, the change stays.** Nothing else happens.
- **If the work throws, the state is put back and the exception goes on**, so the screen's `ErrorHandler`
  still tells the user why. If the work is cancelled, the state is put back as well.
- **`stateIfFailed` receives the state as it is then, not a copy from before.** Undo only your own
  change, as above, and everything else that changed meanwhile (text the user typed, say) is kept.
- **Choose the strategy for repeated taps.** `Sequential` applies them one after the other, each
  starting from what the previous one left. `DropIfBusy` ignores taps until the save is done. With
  the default, two saves for one value can overlap and undo each other in the wrong order.

### Data the screen observes

If the screen shows what a repository holds and follows its changes, the repository is the owner, so
it is the one that shows the change early and takes it back. The handler stays one line:

```kotlin
// The repository: the single source of truth for tasks.
override suspend fun setDone(id: Long, done: Boolean) {
    tasks.update { it.withDone(id, done) }    // every observer sees the tick at once

    try {
        sendToServer(id, done)
    } catch (error: OfflineException) {
        tasks.update { it.withDone(id, confirmedByServer(id)) }    // take it back
        throw error                                                // the handler lets it through to the ErrorHandler
    }
}

// The handler: the default strategy starts every tap at once.
override suspend fun handle(event: OnTaskDoneChange, store: TaskListStore) {
    tasks.setDone(id = event.taskId, done = event.done)
}
```

Do not use `updateOptimistically` here. The observing handler replaces that part of the state every
time the repository emits, so it would overwrite the optimistic value in the middle of the save.
Keeping one owner also gives one place to order, or merge, several writes to the same item.

The sample does both: the list's checkboxes go through the repository, and the editor's "Done" box,
which is the editor's own copy, uses `updateOptimistically`.

## Effect or state?

Ask one question: **if the screen were drawn again from scratch, should this still show?**

- Yes: it is state. A loading indicator, a validation message under a field, a "could not load, retry"
  screen.
- No: it is an effect. A snackbar, a navigation, a haptic tap.

An effect modeled as state shows again after a rotation. A state modeled as an effect disappears
after one.

Effects are delivered in order, once, to one collector. Effects emitted while nothing collects (the
app is in the background, say) are kept and delivered to the next collector. So is an effect that
arrives at the very moment its collector is cancelled. An effect is lost only if the collector is
cancelled while it is handling that effect. Collect them in one place per screen.

## Failures

**Let handlers throw.** A handler that cannot do its job should not catch the exception to turn it
into a message. The router gives every failure to the screen's `ErrorHandler`, with the event that
was being handled and the screen's store. Write one per screen, as a class of its own:

```kotlin
class CounterErrorHandler(
    private val logger: Logger,
) : ErrorHandler<CounterEvent, CounterUiState, CounterEffect> {

    override fun handle(event: CounterEvent, error: Throwable, store: ScreenStore<CounterUiState, CounterEffect>) {
        logger.warn("$event failed", error)

        store.emit(CounterEffect.ShowFailure(error.toFailureReason()))
    }
}
```

The view model passes it to the router, so its own definition does not grow with the handling:

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

Like an event handler, it takes what it depends on in its constructor, holds nothing about the
screen, and has a test of its own: create a store, call `handle`, look at the store. The effect it
emits reaches the UI like any other. For handling short enough not to need a class, a lambda works
in its place, as in the quick start.

- **Turn the exception into your own type there**, such as a `FailureReason` enum, and let the UI
  choose the words from its resources. An exception's message is written for developers and is not
  translated; do not put it on screen.
- **A failure that must stay on screen is state.** Either update the state from the error handler, or catch
  the specific exception in the handler and set a "failed, retry" state there, as the sample's
  editor does when a task cannot be loaded.
- **Cancellation is never a failure.** A run cancelled by `Latest`, by a `ScreenStoppedEvent` or by
  the scope ending is not reported.
- **Without an error handler, nothing is swallowed.** The exception reaches the `CoroutineExceptionHandler`
  of your scope or, if it has none, the platform's handler for uncaught exceptions. On Android that
  ends the app, which is the right default for an error nobody decided how to handle.

Whatever happens to one handler, the others keep running and the router keeps taking events.

## The screen's lifecycle

Work that should last while the screen is visible, such as observing a repository, belongs in a
handler with `DispatchStrategy.WhileStarted`. It starts when you send its event and is cancelled when
you send any event that implements `ScreenStoppedEvent`:

```kotlin
sealed interface TaskListEvent : UiEvent {

    data object OnScreenStarted : TaskListEvent
    data object OnScreenStopped : TaskListEvent, ScreenStoppedEvent
}

class OnScreenStartedHandler(
    private val tasks: TaskRepository,
) : EventHandler<OnScreenStarted, TaskListUiState, TaskListEffect> {

    override val eventType = OnScreenStarted::class
    override val strategy = DispatchStrategy.WhileStarted

    override suspend fun handle(event: OnScreenStarted, store: ScreenStore<TaskListUiState, TaskListEffect>) {
        tasks.observeTasks().collect { loaded -> store.update { it.copy(tasks = loaded) } }
    }
}
```

In Compose, the two events come from the lifecycle:

```kotlin
LifecycleStartEffect(viewModel) {
    viewModel.onEvent(TaskListEvent.OnScreenStarted)

    onStopOrDispose { viewModel.onEvent(TaskListEvent.OnScreenStopped) }
}
```

A `ScreenStoppedEvent` is the one event that needs no handler. Give it one only if stopping should
also do something of its own, such as saving a draft.

## Navigation

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

## Reaching another screen

Sooner or later something outside a screen has to make that screen do something: a settings screen
whose change the home screen must show, or a button in a shared app bar that acts on the list below
it. A router belongs to one screen, and Eventus has no channel that carries an event from one screen
to another. There are two ways to do it, and both leave each screen's router private.

**Go down by default.** It is the one that keeps working as an app grows. Go up only for a control
that belongs to one screen and is merely drawn somewhere shared.

### Down, through what the screens share (the default)

Give the part that acts its own event and handler, and let the handler change something the other
screen already observes:

```kotlin
// The settings screen's own handler. It never mentions the home screen.
class OnUnitsChangeHandler(
    private val settings: SettingsRepository,
) : EventHandler<OnUnitsChange, SettingsUiState, SettingsEffect> {

    override val eventType = OnUnitsChange::class

    override suspend fun handle(event: OnUnitsChange, store: ScreenStore<SettingsUiState, SettingsEffect>) {
        settings.setUnits(event.units)
    }
}
```

The home screen shows the new units because it observes the same repository, not because anyone told
it to. Two screens never talk to each other; they both talk to what lies below them.

Why this is the default:

- **It works whether the other screen exists or not**, and for any number of screens that care.
- **Each part is tested alone**: this handler with a fake repository, the other screen with the same
  fake.
- **Its cost does not grow.** A tenth screen that needs the value observes it, and nothing else
  changes.

Not everything shared is data. "The list is in selection mode" is not something a repository should
know. Put that kind of state in a small holder owned by the app, which the screens observe the same
way. The rule is "owned below the screens", not "owned by a repository".

### Up, through the UI (the exception)

Sending an event needs only the screen's `onEvent`, which is a plain function. So a screen can hand a
control that calls it to whoever draws the shared part:

```kotlin
// A bar shared by every screen. It knows nothing about any of them.
@Composable
fun AppTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) { /* … */ }

// The list puts the bar in its own scaffold and gives it its own button.
Scaffold(
    topBar = {
        AppTopBar(title = "Tasks") {
            IconButton(onClick = { onEvent(TaskListEvent.OnRefresh) }) { /* icon */ }
        }
    },
) { /* … */ }
```

This is right when all of these hold:

- the action belongs to one screen: its state, its progress and its failure message are that screen's;
- that screen is on display whenever the control is;
- no other screen has to reflect the result.

The sample uses both routes. Saving a task in the editor has to show in the list underneath, which is
not on display at that moment, so it goes down: the editor writes to the repository and the list
observes it. Refresh goes up: it is the list's own action, its button is shown only while the list
is, and nothing else depends on it, so the list passes its own button into the shared bar.

It stops being right as soon as one of them fails, and the signs are easy to spot: the control is
needed while its screen is not showing, two screens want to fill the same place, or the same control
is being passed down through layer after layer. Move the action down then.

### Why there is no bus

A channel where any code posts an event and the right router picks it up would be easy to add, and
it would cost what the rest of the design protects:

- **The sender would not know who receives the event, or whether anyone does.** With `onEvent`, an
  event nobody handles is an error at once.
- **The receiver may not exist.** A screen that is not on the back stack has no router. The event
  would be lost, or kept and replayed later at a moment nobody chose.
- **Any screen could drive any other.** The state of a screen would no longer be explained by its
  own events and what it observes.

If neither route fits, the cause is usually that something is owned by a screen and should be owned
by the layer below it.

## Dependency injection

Eventus needs no dependency-injection library, and works with any. Two facts make the wiring simple:

- **Handlers hold nothing about the screen.** State and effects arrive through `handle`, so a handler
  can be created fresh for each view model (`factory`) with no scope of its own.
- **The view model takes a `Collection` of handlers.** Adding an event never changes the view model.

### Give each screen a handler interface

One line per screen pays for itself:

```kotlin
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>

class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> {

    override val eventType = OnRefresh::class
    override val strategy = DispatchStrategy.DropIfBusy

    override suspend fun handle(event: OnRefresh, store: TaskListStore) { /* … */ }
}
```

Handlers get shorter, and your container gets a type that means "the handlers of this screen".

### Koin

```kotlin
val taskListModule = module {
    factoryOf(::OnScreenStartedHandler) bind TaskListHandler::class
    factoryOf(::OnRefreshHandler) bind TaskListHandler::class
    factoryOf(::OnTaskClickHandler) bind TaskListHandler::class
    // …one line per handler

    viewModel { TaskListViewModel(handlers = getAll<TaskListHandler<*>>()) }
}
```

Do not ask Koin for `getAll<EventHandler<*, TaskListUiState, TaskListEffect>>()`. Koin looks
definitions up by class and ignores type arguments, so that call returns the handlers of every screen
in the app. The per-screen interface is what keeps them apart. Register the view model with
`viewModel { }`, never `single { }`: a singleton view model is never cleared, and its router would
never stop.

The handler lines need not be written by hand: `eventus-ksp` generates them. See
[handlers checked and wired by the build](#handlers-checked-and-wired-by-the-build).

### Containers that resolve at compile time

Containers such as Metro and Dagger keep type arguments, so there you can multibind each handler into
`Set<EventHandler<*, TaskListUiState, TaskListEffect>>` and inject that set into the view model.

### By hand

```kotlin
TaskListViewModel(
    handlers = listOf(
        OnScreenStartedHandler(tasks),
        OnRefreshHandler(tasks),
        OnTaskClickHandler(),
    ),
)
```

## Handlers checked and wired by the build

`eventus-ksp` is an optional [KSP](https://kotlinlang.org/docs/ksp-overview.html) processor. It runs
while your app is built, and is never part of the app. It does two things in the Gradle module you add
it to:

- **It checks the handlers.** An event without a handler class fails the build, not the app.
- **It writes the Koin module of the handlers**, when you ask for it with one annotated property.

### Set up

Add the KSP plugin, 2.3.12 or newer, and the processor once for every target of the module:

```kotlin
// build.gradle.kts of the module that holds your screens
plugins {
    id("com.google.devtools.ksp") version "2.3.14"
}

dependencies {
    add("kspAndroid", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspJvm", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspIosArm64", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspIosSimulatorArm64", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
}
```

A configuration is named after its target: `ksp` and the target's name with a capital first letter. In
a module with a single platform, the one line is `ksp("io.github.mradzinski:eventus-ksp:1.0.0-rc01")`.

Building one target runs the processor for that target only, so a day of running the Android app runs
it once per build, not once per target.

### What the check finds

For every sealed event type declared in the module, each of these fails the build, with the file and
line:

- **An event with no handler class**, in the sealed type or in a sealed type nested in it.
  A `ScreenStoppedEvent` is the one event that may go without, as it may at run time.
- **An event with two handler classes.**
- **A handler the router can never call**: one whose event type is a sealed or abstract type, or a type
  parameter. The router sends an event to the handler of its exact class.

```text
e: [ksp] TaskListEvent.kt:14: No handler for com.example.TaskListEvent.OnRefresh. Add a class that
implements EventHandler for it.
```

The processor sees one Gradle module. Keep the handlers of a screen in the module that declares its
events.

### The Koin module

Add the annotation to the module's common dependencies:

```kotlin
commonMain.dependencies {
    implementation("io.github.mradzinski:eventus-koin:1.0.0-rc01")
}
```

Then declare the module once, in common code, and leave its content to the processor:

```kotlin
// The Eventus processor generates the `actual` property on each target.
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module
```

Android Studio cannot see the generated `actual` property and may warn that the `expect` property has
none. The `@Suppress` line silences that warning; the build does not need it.

On each target the processor writes the `actual` property. It holds one factory for every handler
class, bound to the [handler interface of its screen](#give-each-screen-a-handler-interface), and one
for every class that implements `ErrorHandler`:

```kotlin
// generated
internal actual val handlersModule: Module = module {
    factory { com.example.tasklist.OnRefreshHandler(get()) } bind com.example.tasklist.TaskListHandler::class
    factory { com.example.tasklist.OnTaskClickHandler() } bind com.example.tasklist.TaskListHandler::class
    // …every handler of every screen in this Gradle module
    factory { com.example.tasklist.TaskListErrorHandler(get()) }
}
```

What is left for you is the view model of each screen, and giving `handlersModule` to Koin with your
other modules:

```kotlin
val taskListModule = module {
    viewModel {
        TaskListViewModel(
            handlers = getAll<TaskListHandler<*>>(),
            errorHandler = get(),
        )
    }
}
```

A new event then takes a new event class and a new handler class, and nothing else.

Four rules, each reported as a build error when broken:

- **The property is an `expect` property of Koin's type `Module`**, and a Gradle module has one. It
  holds the handlers of every screen in that Gradle module.
- **Every handler implements the handler interface of its screen**, not `EventHandler` directly. That
  interface is what the view model asks Koin for.
- **No handler is `private`.** The generated code is in another file. `internal` is fine.
- **Every target has its `ksp` line.** A target without one does not compile: the compiler reports
  that the `expect` property has no `actual` declaration there.

Each constructor parameter of a handler is resolved with Koin's `get()`. `expect` exists only in a
multiplatform module, so a module with a single platform gets the check but not the generated module.

## Testing

### A handler

A handler test needs no router, no view model and no dependency injection. Create a store, call
`handle`, and look at the store.

```kotlin
class OnSaveClickHandlerTest {

    private val counters = FakeCounterRepository()
    private val handler = OnSaveClickHandler(counters)

    @Test
    fun `it saves the count and says so`() = runTest {
        val store = ScreenStore<CounterUiState, CounterEffect>(CounterUiState(count = 3))

        handler.handle(OnSaveClick, store)

        assertEquals(3, counters.saved)
        store.effects.test {   // Turbine
            assertEquals(CounterEffect.Saved, awaitItem())
        }
    }
}
```

To see the states a handler passes through, collect `store.state` around the call. For a handler that
never returns by itself (`WhileStarted`), launch it in `backgroundScope` and let `runTest` cancel it.

### A view model

Pass the test's scope, so the test decides when handlers run:

```kotlin
@Test
fun `a failed save tells the user why`() = runTest {
    val viewModel = CounterViewModel(
        handlers = listOf(OnSaveClickHandler(FailingCounterRepository())),
        scope = backgroundScope,
    )

    viewModel.onEvent(OnSaveClick)
    testScheduler.runCurrent()

    viewModel.effects.test {
        assertEquals(CounterEffect.ShowFailure(FailureReason.Offline), awaitItem())
    }
}
```

Use `backgroundScope`, not the test's own scope: the router keeps the scope busy until it is
cancelled, and `runTest` waits for its own scope to finish.

### Every event has a handler

An event that no handler is registered for throws `IllegalStateException` when it is sent, which may
be three taps into a screen. With [`eventus-ksp`](#handlers-checked-and-wired-by-the-build) the build
finds a handler that was never written, and its generated Koin module registers every handler there is,
so this test is not needed.

Without it, find the missing handler with one test per screen. It compares the events of the
sealed type with the handlers your container really provides, so it catches a handler that was never
written as well as one that was never registered:

```kotlin
// JVM only: listing the subclasses of a sealed type needs kotlin-reflect.
@Test
fun `every task list event has a registered handler`() {
    val koin = koinApplication { modules(appModules) }.koin
    val handled = koin.getAll<TaskListHandler<*>>().map { it.eventType }.toSet()

    val events = TaskListEvent::class.sealedSubclasses
        .filterNot { it.isSubclassOf(ScreenStoppedEvent::class) }   // may go without a handler
        .toSet()

    assertEquals(events, handled)
}
```

Two handlers for the same event are caught without a test: the router refuses to be created.

## The coroutine scope

The router runs every handler in a child of the scope you pass, and stops when that scope is
cancelled. Whoever creates the scope must cancel it.

- **In a view model**, pass the same scope to both: `ViewModel(scope)` cancels it when the view model
  is cleared. On Android the view model, and so the router and its state, survives configuration
  changes.
- **`routerScope()`** gives a scope that runs handlers on the main thread, at once when the event is
  sent from the main thread, which is what keeps a text field in step with its state. On a platform
  without a main dispatcher, handlers run on the thread that sends the event. Desktop apps need
  `kotlinx-coroutines-swing` for the main dispatcher.
- **Outside a view model** (a SwiftUI screen holding a shared presenter, a desktop window), you
  own the scope: cancel it when the screen goes away, or its `Sequential` workers and `WhileStarted`
  runs live on.
- **Do the slow work elsewhere.** Repositories should be safe to call from the main thread and move
  their own work to another dispatcher. Handlers then stay simple.

`onEvent` is safe to call from any thread.

## Logging events

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

## Where Eventus sits in your app

Eventus belongs to the presentation layer and nowhere else.

- **Screens and their handlers depend on Eventus.** In a modular app, that is your feature modules.
- **Handlers depend inward**, on repository interfaces or use cases. They never reach a data source.
- **Domain and data code never import Eventus.** A repository that takes a `ScreenStore` is a sign
  that presentation logic has leaked downward.
- **The UI depends on the router interface only**: `state`, `effects`, `onEvent`.

You do not need a use-case layer to use Eventus: a handler already gives an operation a name, a file
and a test. Add a use case when the same orchestration appears in a third handler, or when something
that is not a screen (a widget, a background job) needs it too.

## When Eventus is too much

A screen with a state and two buttons does not need a sealed event type and two handler classes. A
view model with two functions is clearer. Eventus earns its keep on screens with many events, with
events that need different concurrency rules, or in an app where every screen should be built the
same way so that anyone can find the code for any tap. Using it for some screens and not others is
fine.

## What Eventus does not do

- **It does not restore state after the process is stopped.** Pass a restored value as `initialState`
  if you save one.
- **The library alone does not check at compile time that every event has a handler.** The optional
  processor does; see [handlers checked and wired by the build](#handlers-checked-and-wired-by-the-build).
- **It does not navigate**, inject dependencies or collect flows for the UI. It gives you the pieces
  those are built from.
- **It is not an event bus.** A router belongs to one screen. Events do not travel between screens;
  see [reaching another screen](#reaching-another-screen) for what to do instead.

## Project layout

| Path | What |
|---|---|
| `eventus/` | The library. All code is in `commonMain`. |
| `eventus/api/` | The recorded public API. `checkKotlinAbi` fails when the code no longer matches it. |
| `eventus-koin/` | The `@EventusKoinModule` annotation, and nothing else. |
| `eventus-ksp/` | The symbol processor: the handler check and the generated Koin module. JVM only, as a build tool. |
| `sample/shared/` | The sample app: a task list and a task editor, with navigation, Koin and tests. One module, layered by package, to keep it short. |
| `sample/androidApp/`, `sample/desktopApp/` | Launchers. |
| `sample/iosApp/` | The Xcode project of the iOS launcher. |
| `tooling/ktlint-rules/` | The project's own formatting rules, applied by ktlint next to its standard ones. |

Run the sample:

```bash
./gradlew :sample:desktopApp:run
```

```bash
./gradlew :sample:androidApp:installDebug
```

For iOS, open `sample/iosApp/iosApp.xcodeproj` in Xcode and run it.

Run the library's checks (tests on every platform, lint, coverage and the public API check). The web
tests need Chrome, and the iOS tests need a Mac with an iOS simulator:

```bash
./gradlew :eventus:check
```

Run the checks of the two optional artifacts:

```bash
./gradlew :eventus-koin:check :eventus-ksp:check
```

Format your changes before you send them. This applies the project's style, including its own rules
(a blank line at the start of a class body, after local declarations and before a `return`):

```bash
./gradlew ktlintFormat
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

Eventus is released under the [Apache License 2.0](LICENSE). If you ship it in an app, please keep the
[NOTICE](NOTICE) file with it, as the license asks.
