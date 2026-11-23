# Using Eventus (agent guide)

This guide is for an AI agent, or a developer in a hurry, adding Eventus to a Kotlin Multiplatform
project or changing a project that uses it. It is one file on purpose: everything you need to write
correct Eventus code is here, and you do not need to read the other docs first.

If your environment supports skills, the section "Skills to save" near the end has six checklists
you can keep, one for each common task. Keep a copy of this file with them.

Eventus is new. Do not rely on memory for its API: use the signatures in this file. Every public
name of the library is in this file. If you are about to use one that is not, it does not exist.

## What Eventus is

A router for the events of one screen, for apps that use Model-View-ViewModel (MVVM). The UI calls
`onEvent(event)`. The router runs the one event handler, an `EventHandler` class, registered for
that event's class. The handler changes the screen's state or emits an effect through a
`ScreenStore`. The UI reads `state` (a `StateFlow`) and `effects` (a `Flow`).

It is not an event bus, a navigation library, a dependency-injection library or a base `ViewModel`
class.

## Which artifact do I need?

All artifacts have the group `io.github.mradzinski` and share one version. This file was written
for `1.0.0-rc01`. Before you add a dependency, check Maven Central for the newest version and that
it is published. If it is not there, or you cannot check, tell the user and do not invent a version.

| You want to… | Artifact | Where it goes |
|---|---|---|
| Use the library (always) | `eventus` | `commonMain` `implementation` |
| Fail the build when an event has no handler (recommended) | `eventus-ksp` | One KSP configuration for each target |
| Have the Koin module of your handlers generated | `eventus-koin` and `eventus-ksp` | `commonMain` `implementation`, plus the KSP lines |
| Have the Metro bindings of your handlers generated (from `1.0.0-rc02`) | `eventus-metro` and `eventus-ksp` | `commonMain` `implementation`, plus the KSP lines |
| Collect effects in a Compose UI with one call | `eventus-compose` | `commonMain` `implementation` |

Requirements: Kotlin 2.4.21 or newer. Targets: `android` (minSdk 24), `jvm`, `iosArm64`,
`iosSimulatorArm64`, `iosX64`, `js`, `wasmJs`. `eventus-compose` has no `iosX64`.

```kotlin
// build.gradle.kts of the Gradle module that holds the screens
plugins {
    id("com.google.devtools.ksp") version "2.3.14"   // only for eventus-ksp; 2.3.12 or newer
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.mradzinski:eventus:1.0.0-rc01")
            implementation("io.github.mradzinski:eventus-compose:1.0.0-rc01")   // optional
            implementation("io.github.mradzinski:eventus-koin:1.0.0-rc01")      // optional
        }
    }
}

dependencies {
    // One line for each target the module declares: "ksp" + the target's name, capitalized.
    // The other targets are "kspIosX64", "kspJs" and "kspWasmJs".
    add("kspAndroid", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspJvm", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspIosArm64", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
    add("kspIosSimulatorArm64", "io.github.mradzinski:eventus-ksp:1.0.0-rc01")
}
```

In a module with a single platform, the one line is `ksp("io.github.mradzinski:eventus-ksp:…")`.
In a multiplatform module, plain `ksp(...)` is a build error: use the per-target lines.

## The rules

Follow every one. Each is a way real code breaks.

1. **One event class, one handler class.** Never write a `when` over events in a view model, and
   never register two handlers for one event.
2. **Declare a screen's events as one sealed type that extends `UiEvent`.** The router matches an
   event by its exact class. A handler for a sealed or abstract parent is never called, and a sealed
   parent nested in the event type needs no handler: only concrete event classes do.
3. **A handler holds no state of the screen.** Its constructor takes only real dependencies, such
   as a repository. It reads and writes the screen through the `store` parameter of `handle`.
4. **The state is immutable.** Change it with `store.update { it.copy(...) }`. A state mutated in
   place equals the old one, so it is never emitted.
5. **Handlers throw.** Do not catch an exception to turn it into a message. Let it reach the
   screen's `ErrorHandler`. Never catch `CancellationException` without rethrowing it. A cancelled
   run is never passed to the `ErrorHandler`.
6. **The view model has no logic and no base class from Eventus.** It implements `EventRouter` by
   delegation. The one thing its body may do is send the screen's opening event from `init`.
7. **The UI never gets the `ScreenStore`.** The UI uses `state`, `effects` and `onEvent` only.
8. **Collect `effects` in one place.** Each effect goes to one collector. A second collector takes
   effects away from the first.
9. **Every strategy other than `Concurrent` names a type in angle brackets where it is created**,
   followed by parentheses or by a key in braces: `Latest<OnQueryChange>()` or
   `Latest<OnLikeChange> { it.trackId }`. `Concurrent` is a value and takes neither. A handler may
   also use a strategy that was created once as a shared `val`. A strategy written by hand has no
   ready-made name in it: `DispatchStrategy<OnRefresh> { event, active -> … }`.
10. **Events and their handlers live in the same Gradle module.** The build-time check looks at one
    module.
11. **Nothing in Eventus knows about a screen being visible.** A run lasts until the handler
    returns or the router's scope is cancelled.

## The API

Package `io.github.mradzinski.eventus`.

```kotlin
interface UiState
interface UiEvent
interface UiEffect

interface EventHandler<E : UiEvent, S : UiState, F : UiEffect> {
    val eventType: KClass<E>
    val strategy: DispatchStrategy<E> get() = DispatchStrategy.Concurrent
    suspend fun handle(event: E, store: ScreenStore<S, F>)
}

class ScreenStore<S : UiState, F : UiEffect>(initialState: S) {
    val state: StateFlow<S>
    val current: S
    val effects: Flow<F>
    fun update(transform: (S) -> S)
    fun emit(effect: F)
    suspend fun <T> updateOptimistically(stateUpdate: (S) -> S, stateIfFailed: (S) -> S, work: suspend () -> T): T
}

fun interface ErrorHandler<E : UiEvent, S : UiState, F : UiEffect> {
    fun handle(event: E, error: Throwable, store: ScreenStore<S, F>)
}

interface EventRouter<E : UiEvent, S : UiState, F : UiEffect> {
    val state: StateFlow<S>
    val effects: Flow<F>
    fun onEvent(event: E)   // throws IllegalStateException if the event has no handler
}

fun <E : UiEvent, S : UiState, F : UiEffect> EventRouter(
    scope: CoroutineScope,
    initialState: S,
    handlers: Collection<EventHandler<*, S, F>>,
    errorHandler: ErrorHandler<E, S, F>? = null,
    observer: RouterObserver<E>? = null,
): EventRouter<E, S, F>

// A scope for a view model and its router to share: a SupervisorJob plus the main dispatcher,
// or no dispatcher on a platform that has no main one. The router runs handlers on the dispatcher
// of the scope it is given.
fun routerScope(): CoroutineScope

// `in E`: a strategy made for a parent type can be used by the handler of any event of that type.
fun interface DispatchStrategy<in E : UiEvent> {
    fun decide(event: E, active: List<UiEvent>): Dispatch   // `active`: every active event of the screen

    companion object {
        val Concurrent: DispatchStrategy<UiEvent>   // a value: no brackets, no parentheses

        // Functions: write Latest<OnQueryChange>() with the parentheses, or Latest<OnX> { key }.
        inline fun <reified T : UiEvent> Latest(): DispatchStrategy<T>
        inline fun <reified T : UiEvent> Latest(noinline key: (T) -> Any?): DispatchStrategy<T>
        inline fun <reified T : UiEvent> Sequential(): DispatchStrategy<T>
        inline fun <reified T : UiEvent> Sequential(noinline key: (T) -> Any?): DispatchStrategy<T>
        inline fun <reified T : UiEvent> DropIfAlreadyRunning(): DispatchStrategy<T>
        inline fun <reified T : UiEvent> DropIfAlreadyRunning(noinline key: (T) -> Any?): DispatchStrategy<T>
    }
}

sealed interface Dispatch {
    data object Run : Dispatch
    data object Ignore : Dispatch
    data class Replace(val events: List<UiEvent>) : Dispatch
    data class WaitFor(val events: List<UiEvent>) : Dispatch
}

fun interface RouterObserver<in E : UiEvent> {
    fun observe(trace: RouterTrace<E>)
}

// Every report is a RouterTrace<E>, has `event` and `number`, and prints as a sentence (toString).
sealed interface RouterTrace<out E : UiEvent> {
    val event: E
    val number: Long

    data class Dispatched<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val dispatch: Dispatch,
        val replacedOrAwaited: List<Long>,
    ) : RouterTrace<E>

    data class Started<out E : UiEvent>(override val event: E, override val number: Long) : RouterTrace<E>

    data class Completed<out E : UiEvent>(override val event: E, override val number: Long) : RouterTrace<E>

    data class Failed<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val error: Throwable,
    ) : RouterTrace<E>

    data class Cancelled<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val reason: Reason,
    ) : RouterTrace<E> {
        sealed interface Reason {
            data class ReplacedBy(val number: Long) : Reason
            data object ScopeCancelled : Reason
            data object ByHandler : Reason
        }
    }
}
```

Package `io.github.mradzinski.eventus.compose` (artifact `eventus-compose`):

```kotlin
// Performs each effect while the screen is started. Call it once for a router.
@Composable
fun <F : UiEffect> EventRouter<*, *, F>.collectEffects(onEffect: suspend (effect: F) -> Unit)

// Sends event(true) when the screen starts and event(false) when it stops.
@Composable
fun <E : UiEvent> EventRouter<E, *, *>.notifyVisibility(event: (visible: Boolean) -> E)
```

Package `io.github.mradzinski.eventus.koin` (artifact `eventus-koin`):

```kotlin
annotation class EventusKoinModule   // on one `expect val` of Koin's type Module
```

Package `io.github.mradzinski.eventus.metro` (artifact `eventus-metro`, from `1.0.0-rc02`):

```kotlin
annotation class ContributesEventHandlers(val scope: KClass<*>)   // on a screen's handler interface
```

These blocks are declarations to read, not code to copy: bodies and initializers are left out.
The examples in the "Adding a screen" section compile once the app's own types that they name
exist. The shorter snippets elsewhere are fragments: they leave out imports and the app's own types.

`EventRouter(...)` cannot always work out its event type by itself. Inside a view model's delegation
clause it can. Anywhere else, write the type arguments:
`EventRouter<CounterEvent, CounterUiState, CounterEffect>(...)`.

## Adding a screen

Write these pieces, in this order. `CounterRepository` and `OfflineException` are the app's own,
and so is `CounterScreen` in the UI block; with those in place, both blocks compile as they are.

```kotlin
import androidx.lifecycle.ViewModel
import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.routerScope
import kotlinx.coroutines.CoroutineScope

// 1. The contract: state, events, effects.
data class CounterUiState(val count: Int = 0, val saving: Boolean = false) : UiState

sealed interface CounterEvent : UiEvent {

    data object OnIncrementClick : CounterEvent
    data object OnSaveClick : CounterEvent
}

sealed interface CounterEffect : UiEffect {

    data object Saved : CounterEffect
    data class ShowFailure(val reason: FailureReason) : CounterEffect
}

// What went wrong, as a type of the app's own. Never send an exception or its message to the UI.
enum class FailureReason { Offline, Unknown }

// 2. One handler interface and one store name for the screen. They keep signatures short, and the
//    handler interface is what dependency injection asks for.
interface CounterHandler<E : CounterEvent> : EventHandler<E, CounterUiState, CounterEffect>

typealias CounterStore = ScreenStore<CounterUiState, CounterEffect>

// 3. One handler class for each event.
class OnIncrementClickHandler : CounterHandler<CounterEvent.OnIncrementClick> {

    override val eventType = CounterEvent.OnIncrementClick::class

    override suspend fun handle(event: CounterEvent.OnIncrementClick, store: CounterStore) {
        store.update { it.copy(count = it.count + 1) }
    }
}

class OnSaveClickHandler(private val counters: CounterRepository) : CounterHandler<CounterEvent.OnSaveClick> {

    override val eventType = CounterEvent.OnSaveClick::class

    // A second tap on Save is ignored while a save is running.
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<CounterEvent.OnSaveClick>()

    override suspend fun handle(event: CounterEvent.OnSaveClick, store: CounterStore) {
        store.update { it.copy(saving = true) }

        try {
            counters.save(store.current.count)   // throws when it fails
            store.emit(CounterEffect.Saved)
        } finally {
            store.update { it.copy(saving = false) }
        }
    }
}

// 4. One error handler for the screen. It receives every exception a handler throws, and is the
//    one place that turns an exception into something the UI can show.
class CounterErrorHandler : ErrorHandler<CounterEvent, CounterUiState, CounterEffect> {

    override fun handle(event: CounterEvent, error: Throwable, store: CounterStore) {
        val reason = if (error is OfflineException) FailureReason.Offline else FailureReason.Unknown

        store.emit(CounterEffect.ShowFailure(reason))
    }
}

// 5. The view model: a constructor and a delegation clause, nothing else.
class CounterViewModel(
    handlers: Collection<CounterHandler<*>>,
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

The UI, with `eventus-compose`:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mradzinski.eventus.compose.collectEffects

@Composable
fun CounterRoute(viewModel: CounterViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    viewModel.collectEffects { effect ->
        when (effect) {
            is CounterEffect.Saved -> { /* show a confirmation */ }
            is CounterEffect.ShowFailure -> { /* show a text for effect.reason */ }
        }
    }

    CounterScreen(state = state, onEvent = viewModel::onEvent)
}
```

Without `eventus-compose`, collect the flow yourself, once:
`LaunchedEffect(viewModel) { viewModel.effects.collect { effect -> … } }`. That plain block also
collects while the app is in the background, so an effect can be performed with nobody looking.
`collectEffects` collects only while the screen is started, and effects wait otherwise.

Facts about failures:

- A handler's `finally` runs before the `ErrorHandler`, so the `ErrorHandler` sees the state that
  `finally` left. `store.update` and `store.emit` are safe to call in a `finally`, also when the run
  was cancelled.
- The `ErrorHandler` receives the event that failed. Use it when one event needs different handling.
  A failure that must stay on screen is written to the state, not emitted.
- The store keeps every emitted effect until a collector takes it. Nothing is dropped, and nothing
  is replayed to a second collector.

## Adding an event to an existing screen

1. Add the event class to the screen's sealed event type.
2. Add one handler class for it, implementing the screen's handler interface.
3. Register the handler where the screen's other handlers are registered. With the generated Koin
   module or the generated Metro bindings, this step does not exist.
4. Send the event from the UI with `onEvent`.
5. Add a test class for the handler.

Do not change the view model class. If the handlers are listed by hand, the list is the only other
place to touch.

## Deciding where something goes

| It is… | Put it in | Example |
|---|---|---|
| Something the screen must still show if it were drawn again from scratch | The state | A count, a loading indicator, a validation message |
| Something only the UI can do, once | An effect | A toast, navigating to another screen |
| Something that needs no UI | The handler itself, through its dependencies | Saving, reporting to analytics |

An effect that shows something for a while, such as a toast, can go unseen if the user leaves the
app at that moment. A message the user must see belongs in the state.

## Choosing a dispatch strategy

A strategy decides what the router does with an event while other events are still active. An event
is active from the moment the router accepts it until its handler returns, fails or is cancelled.
An event that waits for others is active while it waits. An event that was ignored is never active.
A screen that observes data has an opening event that is active for as long as the screen lives.

A ready-made strategy looks at the active events that **match**: the ones that are an instance of
the type in the brackets and, when there is a key, have the same key as the new event. With a
sealed parent in the brackets, events of different classes match each other.

Read the type in the brackets as the end of the sentence.

| The situation | Write |
|---|---|
| Quick work that can overlap (the default; write nothing) | `DispatchStrategy.Concurrent` |
| A button that must not act twice | `DispatchStrategy.DropIfAlreadyRunning<OnSaveClick>()` |
| Input where only the newest matters, such as a search box | `DispatchStrategy.Latest<OnQueryChange>()` |
| Changes that must all apply, in order | `DispatchStrategy.Sequential<OnPlusClick>()` |
| Several events that are one thing: query, filter and sort reload one list | `DispatchStrategy.Latest<OnCriteriaChange>()` |
| Actions that exclude each other: Save and Delete, under a sealed parent `OnChangeClick` | `DispatchStrategy.DropIfAlreadyRunning<OnChangeClick>()` |
| Several events that share one order: plus and minus, under a sealed parent `OnQuantityClick` | `DispatchStrategy.Sequential<OnQuantityClick>()` |
| One event for every row of a list, each row by itself | `DispatchStrategy.Sequential<OnTaskDoneChange> { it.taskId }` |
| Several events for every row, each row by itself (the sealed parent declares `lineId`) | `DispatchStrategy.Sequential<OnQuantityClick> { it.lineId }` |

- **For several events**, give them a sealed parent type and name that type. Declare the strategy
  once, as a top-level `val`, and use that value in every handler of the type.
- **For each item**, add a key in braces. `Latest` and `DropIfAlreadyRunning` take a key the same way.
  The key function receives the type in the brackets, so for several events put the key on their
  sealed parent: `sealed interface OnQuantityClick : CartEvent { val lineId: Long }`.
- **A type the handler's event does not belong to does not compile.**
- **What the ready-made strategies answer:** all three answer `Dispatch.Run` when no active event
  matches. Otherwise `Latest` answers `Dispatch.Replace(matching)`, `Sequential` answers
  `Dispatch.WaitFor(matching)` and `DropIfAlreadyRunning` answers `Dispatch.Ignore`.

```kotlin
sealed interface SearchEvent : UiEvent {

    sealed interface OnCriteriaChange : SearchEvent

    data class OnQueryChange(val query: String) : OnCriteriaChange
    data class OnFilterChange(val filter: String) : OnCriteriaChange
}

val latestCriteria = DispatchStrategy.Latest<SearchEvent.OnCriteriaChange>()
```

Events that share a strategy usually share inputs too. Each handler writes its own input to the
state and reads the other's from it, so the search always uses the newest of both:

```kotlin
// In OnFilterChangeHandler. OnQueryChangeHandler is the mirror image.
override val strategy = latestCriteria

override suspend fun handle(event: OnFilterChange, store: SearchStore) {
    store.update { it.copy(filter = event.filter) }

    val results = products.search(query = store.current.query, filter = event.filter)

    store.update { it.copy(results = results) }
}
```

### Writing your own strategy

Only when no ready-made form fits. It is a function of the new event and every active event of the
screen, oldest first:

```kotlin
// A refresh cancels "load more", and a second refresh is ignored.
override val strategy = DispatchStrategy<OnRefresh> { _, active ->
    if (active.any { it is OnRefresh }) {
        Dispatch.Ignore
    } else {
        Dispatch.Replace(active.filterIsInstance<OnLoadMore>())
    }
}
```

| Answer | The router |
|---|---|
| `Dispatch.Run` | Runs the event now. |
| `Dispatch.Ignore` | Does not run it. |
| `Dispatch.Replace(events)` | Cancels those events, then runs it. With an empty list it is the same as `Run`. |
| `Dispatch.WaitFor(events)` | Runs it when those events have ended. With an empty list it is the same as `Run`. |

The function must only compute its answer: it runs on the thread that sent the event and can run
more than once. Name the events it looks for: filter `active` first, and never test whether the
whole list is empty, because the screen's opening event is always in it. Naming an event names
every active event that is equal to it. If the function throws, the event does not run and the app
gets an uncaught error.

A hand-written strategy and a ready-made one can sit side by side: here `OnRefresh` has its own
function, and `OnLoadMore` uses `DispatchStrategy.DropIfAlreadyRunning<OnFeedLoad>()`, where
`OnFeedLoad` is the sealed parent of both.

### Debouncing

There is no debounce operator. Use `Latest` and a `delay` inside the handler, after the part that
must show at once:

```kotlin
override val strategy = DispatchStrategy.Latest<OnQueryChange>()

override suspend fun handle(event: OnQueryChange, store: SearchStore) {
    store.update { it.copy(query = event.query) }   // the text field shows it at once

    delay(300.milliseconds)                         // a newer event cancels this run here

    val results = products.search(event.query)

    store.update { it.copy(results = results) }
}
```

## Work that keeps running

To observe data for as long as the screen lives, give the screen an opening event and send it from
the view model's `init`. Its handler collects a flow and never returns; it ends when the view model
is cleared.

```kotlin
class OnScreenOpenedHandler(private val tasks: TaskRepository) : TaskListHandler<OnScreenOpened> {

    override val eventType = OnScreenOpened::class
    override val strategy = DispatchStrategy.Latest<OnScreenOpened>()   // sent again, it starts over

    override suspend fun handle(event: OnScreenOpened, store: TaskListStore) {
        tasks.observeTasks().collect { loaded ->
            store.update { it.copy(tasks = loaded) }
        }
    }
}

class TaskListViewModel(
    handlers: Collection<TaskListHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
    ) {

    init {
        onEvent(TaskListEvent.OnScreenOpened)   // safe here: the router already exists
    }
}
```

If such a handler fails, it has ended, and the `ErrorHandler` is called as for any other handler.
Send the event again to restart it, for example from a retry button.

To stop costly work while the UI is hidden, define an event of your own that carries the visibility,
handle it with `Latest`, and return at once when it says hidden. With `eventus-compose`,
`viewModel.notifyVisibility { visible -> OnVisibilityChange(visible) }` sends it.

## Changing data that the screen observes

When the state shows data that a handler observes from a repository, a handler that changes that
data calls the repository and writes nothing to the state. The observing handler brings the change
back.

- A value that comes from the event is safe to save as it is: a toggle's new position, a slider's
  new value. `setLiked(trackId, event.liked)` is fine.
- Do not compute the new value from `store.current` and then save it: a second event can read the
  state before the first change has come back. For a change that depends on the old value, give
  the repository an operation that says what to do, such as `increment(lineId)`.
- Do not use `updateOptimistically` on observed data. To show such a change early, make the
  repository emit it early and take it back if saving fails.

## Updating the state optimistically

Use this for state that only the screen holds, and that nothing else writes.

- The exception is thrown again after the state is put back, so the `ErrorHandler` still gets it.
- The state is also put back when the run is cancelled, for example by a `Latest` strategy. The
  router cancels the old run before it starts the new one, so on a scope that runs on one thread,
  as `routerScope()` does, the old value is put back first and the newer value is what stays.
- For a switch or a slider that must follow the finger, use `Latest`. With `Sequential` the second
  change does not show until the first has been saved.

Show the result at once and put it back if the work fails:

```kotlin
store.updateOptimistically(
    stateUpdate = { it.copy(favorite = event.favorite) },
    stateIfFailed = { it.copy(favorite = !event.favorite) },
) {
    profiles.setFavorite(event.favorite)   // throws when it fails: the state goes back, the error goes on
}
```

`stateIfFailed` receives the state as it is at that moment. Undo only your own change.

## Dependency injection

A view model takes its handlers as a `Collection`. Give each screen a handler interface, as in
"Adding a screen", so the view model can ask for the handlers of its own screen.

By hand:

```kotlin
fun counterViewModel(counters: CounterRepository, scope: CoroutineScope = routerScope()) = CounterViewModel(
    handlers = listOf(OnIncrementClickHandler(), OnSaveClickHandler(counters)),
    errorHandler = CounterErrorHandler(),
    scope = scope,   // a test passes backgroundScope
)
```

With Koin and `eventus-ksp`, declare one property in common code. The processor generates its
`actual` on each target, with a factory for every handler class and every `ErrorHandler` class of
the Gradle module:

```kotlin
import io.github.mradzinski.eventus.koin.EventusKoinModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

val counterModule = module {
    viewModel {
        CounterViewModel(
            handlers = getAll<CounterHandler<*>>(),
            errorHandler = get(),
        )
    }
}

// Give Koin both: modules(handlersModule, counterModule)
```

Rules for the generated module: one `@EventusKoinModule` property for each Gradle module; handler
classes are not `private`; a handler implements its screen's handler interface, not `EventHandler`
directly; the property is written without a body.

With Metro and `eventus-ksp` (from `1.0.0-rc02`, written for Metro 1.6.0), annotate the screen's
handler interface with the scope of the graph that creates the view model. The processor
generates, on each target, a binding container contributed to that scope, with one
`@Provides @IntoSet` function for every handler class of the screen. Handler classes take no
annotation:

```kotlin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import io.github.mradzinski.eventus.metro.ContributesEventHandlers

@ContributesEventHandlers(AppScope::class)
interface CounterHandler<E : CounterEvent> : EventHandler<E, CounterUiState, CounterEffect>

@Inject
class CounterErrorHandler : ErrorHandler<CounterEvent, CounterUiState, CounterEffect> { /* … */ }

// Metro uses the default value of `scope` when the graph provides no CoroutineScope.
@Inject
class CounterViewModel(
    handlers: Set<CounterHandler<*>>,
    errorHandler: CounterErrorHandler,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<CounterEvent, CounterUiState, CounterEffect> by EventRouter(
        scope = scope,
        initialState = CounterUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
    )

// In a multiplatform module: one graph for each platform, in that platform's source set
// (androidMain, jvmMain, iosMain, …). A graph in commonMain does not see the generated bindings.
// In a module with a single platform: this one graph.
@DependencyGraph(AppScope::class)
interface AndroidAppGraph {
    val counterViewModel: CounterViewModel
}
```

Rules for the generated bindings: the annotation goes on an interface that extends `EventHandler`;
neither the interface nor a handler class is `private`; the generated container is as visible as
the interface, so a graph in another Gradle module needs a public interface (handler classes can
stay `internal`); Metro supplies every constructor parameter of a handler by type, keeping
qualifiers such as `@Named`, and a handler's default values are not used; error handlers are not generated, so
give them `@Inject`. Handler classes take no Metro annotation.

## Logging and debugging

Pass an `observer` to see what the router does with every event. Each report prints as a sentence.
In a view model it is one more argument of the delegation clause, and it may stay there for good:
send the reports to the project's logger.

```kotlin
class CounterViewModel(
    handlers: Collection<CounterHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<CounterEvent, CounterUiState, CounterEffect> by EventRouter(
        scope = scope,
        initialState = CounterUiState(),
        handlers = handlers,
        observer = { report -> println(report) },
    )
```

```text
#1 OnSaveClick runs
#1 OnSaveClick started
#2 OnDeleteClick is ignored
#1 OnSaveClick completed
```

When a user says "I tapped it and nothing happened" or "my results never arrived", add the
observer and read the lines: `is ignored`, `replaces #1`, `waits for #1`, `was replaced by #2`,
`was cancelled with the scope`, `was cancelled by its own handler`, `failed: …`.

The observer is a `RouterObserver<E>`, a function interface with one function,
`observe(trace: RouterTrace<E>)`. A report is a `RouterTrace<E>` with `event` and `number`:

| Report | Extra fields |
|---|---|
| `RouterTrace.Dispatched` | `dispatch: Dispatch`, `replacedOrAwaited: List<Long>` (the numbers of those events) |
| `RouterTrace.Started` | None |
| `RouterTrace.Completed` | None |
| `RouterTrace.Failed` | `error: Throwable` |
| `RouterTrace.Cancelled` | `reason`: `Reason.ReplacedBy(number)`, `Reason.ScopeCancelled` or `Reason.ByHandler` |

| The line ends with | The report is |
|---|---|
| `runs`, `is ignored`, `replaces #1`, `waits for #1` | `Dispatched`, with `dispatch` set to the strategy's answer |
| `started` | `Started` |
| `completed` | `Completed` |
| `failed: …` | `Failed` |
| `was replaced by #2`, `was cancelled with the scope`, `was cancelled by its own handler` | `Cancelled` |

- Every event the router is sent gets a `Dispatched` report first, the first event of a screen
  included. An ignored event gets no other report. Numbers start at 1, so a screen's opening event
  is usually `#1`.
- A `Cancelled` report is delivered when the cancelled run has ended. In a test, that is after the
  next `testScheduler.runCurrent()`.
- The router never calls the observer from two threads at once, so an observer may add to a plain
  list.
- A `Dispatched` report is delivered inside `onEvent`, on the thread that sent the event. The others
  are delivered where they happen.
- Reports arrive in order, one at a time. The observer must be quick. A report has no time; a
  logger adds its own.

## Testing

A handler test needs no router and no view model:

```kotlin
@Test
fun `it saves the count and says so`() = runTest {
    val counters = FakeCounterRepository()
    val store = CounterStore(CounterUiState(count = 3))

    OnSaveClickHandler(counters).handle(CounterEvent.OnSaveClick, store)

    assertEquals(3, counters.saved)
    assertEquals(CounterEffect.Saved, store.effects.first())
}
```

A view model or router test:

```kotlin
@Test
fun `a tap on increment raises the count`() = runTest {
    val viewModel = CounterViewModel(
        handlers = listOf(OnIncrementClickHandler()),
        errorHandler = CounterErrorHandler(),
        scope = backgroundScope,
    )

    viewModel.onEvent(CounterEvent.OnIncrementClick)
    testScheduler.runCurrent()

    assertEquals(1, viewModel.state.value.count)
}
```

- **Pass `backgroundScope`, never the test's own scope.** The router keeps a job in its scope until
  the scope is cancelled, so the test's own scope fails with `UncompletedCoroutinesError`.
- **Use `testScheduler.runCurrent()`.** It needs no opt-in. `advanceUntilIdle()` does not run work
  in `backgroundScope`.
- **A handler that uses `delay`:** move the clock with
  `testScheduler.advanceTimeBy(300.milliseconds)`, then call `testScheduler.runCurrent()`. That
  needs no opt-in either. In a test that calls `handle` directly, `runTest` skips the delay, so
  that kind of test cannot check the timing; test timing through a router.
- **Where tests go:** in the test source set, such as `commonTest`. The build-time check does not
  look at test sources.
- **Test names:** a sentence in backticks compiles on every target if it uses only letters, digits,
  spaces, apostrophes and hyphens.
- **A handler that must throw:** `assertFailsWith<OfflineException> { handler.handle(event, store) }`
  works inside `runTest`.
- **Collecting reports in a test:** create the router with `observer = { reports += it }`, or give
  the view model an optional `observer` parameter that it passes on.
- **A view model that sends an event from `init`:** call `testScheduler.runCurrent()` once after
  creating it, before sending anything else.
- **A strategy can be tested by itself.** Call its `decide` function:
  `handler.strategy.decide(newEvent, listOf(activeEvent))` returns the `Dispatch`, and every
  `Dispatch` can be compared with `assertEquals`: for example
  `assertEquals(Dispatch.WaitFor(listOf(activeEvent)), dispatch)`.
- **`ScreenStore` works by itself in a test**, including `updateOptimistically`.
- **Imports for tests:** `kotlin.test.Test`, `kotlin.test.assertEquals`,
  `kotlinx.coroutines.test.runTest`, `kotlinx.coroutines.flow.first`.
- **A handler that never returns:** launch it in `backgroundScope`.

## Error messages and what to do

| You see | It means | Do this |
|---|---|---|
| `No handler for com.example.X. Add a class that implements EventHandler for it.` (build) | An event has no handler class. | Add the handler class. |
| `X has two handlers: A and B. An event has exactly one handler.` (build) | Two classes handle one event. | Remove one. |
| `… is never called: … is sealed or abstract` (build) | A handler names a parent type. | Name one concrete event class. |
| `… implements EventHandler directly` (build) | A Koin module is requested and a handler has no screen interface. | Implement the screen's handler interface. |
| `… is private, so the generated Koin module cannot create it` (build) | A handler class is private. | Make it `internal`. |
| `… is private, so the generated Metro bindings cannot create it` or `cannot name it` (build) | A handler class or an annotated handler interface is private. | Make it `internal`. |
| `… must be an interface that extends EventHandler` (build) | `@ContributesEventHandlers` is on something else. | Put it on the screen's handler interface. |
| `The scope that … gives to @ContributesEventHandlers is not a known class` (build) | The scope does not resolve. | Name an existing class, such as Metro's `AppScope`. |
| `[Metro/MissingBinding] No binding found for Set<XHandler<*>>` (build) | The graph does not see the generated bindings. | Declare the graph in a platform source set; across Gradle modules, make the handler interface public; check the KSP line of that target. |
| `IllegalStateException: No handler registered for X` (run) | The event's handler was not given to the router. | Register it; add `eventus-ksp` to catch it at build time. |
| `IllegalStateException: Multiple handlers registered for X` (when the router is created) | Two handlers for one event in the collection. | Remove one. |
| `Type of 'val strategy: DispatchStrategy<A>' is not a subtype of … DispatchStrategy<B>` (compile) | The strategy names a type the handler's event is not part of. | Name the event itself or a parent of it. |
| `The strategy of XHandler failed for X` (uncaught) | A strategy function threw. | Fix the function; it must only compute. |
| `The observer failed for: …` (uncaught) | An observer threw. | Fix the observer. |
| `UncompletedCoroutinesError` in a test | The router was given the test's own scope. | Pass `backgroundScope`. |
| `ksp(...)` fails in a multiplatform module | KSP needs one configuration for each target. | Use `add("ksp<Target>", …)`. |

## Lifecycle facts

- The router lives exactly as long as the scope you give it. In a view model that is until the view
  model is cleared.
- A rotation on Android recreates the screen and keeps the view model, so running events, the state
  and waiting effects are untouched.
- In the background, running events go on for as long as the system lets the app run. With
  `collectEffects`, effects wait until the screen is started again.
- After a process death nothing comes back. Eventus saves nothing. If the app restores state, pass
  it as `initialState`: restore state related to the UI, and do not restore events.

## What does not exist

Do not write these; they are not part of Eventus:

- A base `ViewModel` class, or any class to extend.
- A lambda or builder form of a handler. A handler is always a class.
- `DispatchStrategy.Latest`, `.Sequential` or `.DropIfAlreadyRunning` without brackets, and the old
  names `DropIfBusy` and `WhileStarted`.
- A debounce or throttle operator.
- An event bus, or events that travel between screens. To reach another screen, have the handler
  change something both screens observe, such as a repository.
- Saved state, or anything that restores a router.
- Start and stop events for a screen. See "Work that keeps running" for what to do instead.

## Skills to save

If your environment supports skills (reusable instruction files that you load when a task matches,
such as a `SKILL.md` in Claude Code), save the six below. The text inside each block, without the
fence lines around it, is the full content of one skill: the part between the `---` lines is its
name and when to use it, and the rest is what to do.
If your environment has no skills, treat them as checklists.

- **Save a copy of this file next to the skills**, and point the skills at the copy. The address in
  each skill is a fallback for when there is no copy. A skill is a checklist; the signatures are in
  this file.
- **Save all six.** They refer to each other by name.
- **Match the version.** The address points at the `main` branch. If the project uses an older
  version of Eventus and the repository has a tag for it, use the tag in place of `main`.

### Adding a screen

```markdown
---
name: eventus-add-screen
description: "Create a new screen in a Kotlin Multiplatform app that uses the Eventus library. Use when asked to add a screen, a feature with its own view model, or to move an existing view model to Eventus."
---

Read the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

1. Find an existing Eventus screen in the project and copy its layout: where the contract, the handlers and the view model live, and how handlers reach the view model. If this is the project's first Eventus screen, follow the section "Adding a screen" of the guide.
2. Write the contract: one immutable state class that implements UiState, one sealed interface of events that implements UiEvent, one sealed interface of effects that implements UiEffect.
3. Write one handler interface for the screen that extends EventHandler, and a typealias for its ScreenStore.
4. Write one handler class for each event. A handler takes only real dependencies in its constructor and uses the store parameter for the state and the effects. It never catches an exception to show a message.
5. Choose a strategy for each handler. The default suits quick work. Anything that calls a repository needs a decision: use the skill eventus-choose-strategy.
6. Write one ErrorHandler class for the screen that turns an exception into an effect or a state.
7. Write the view model: a constructor and a delegation clause to EventRouter(...), with no logic. If the screen observes data, add one opening event and send it from init.
8. Register the handlers the way the project does. With a generated Koin module or generated Metro bindings there is nothing to register.
9. In the UI, read state, call onEvent, and collect effects in exactly one place.
10. Write one test class for each handler, in the test source set. Build and run the tests for the targets you are allowed to build.
```

### Adding an event

```markdown
---
name: eventus-add-event
description: "Make a screen that uses the Eventus library react to something new, such as a new button, field, gesture or timer. Use when changing the behavior of an existing Eventus screen."
---

Read the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

1. Add one event class to the screen's sealed event type. Name it after what happened, such as OnSaveClick, not after what to do.
2. Add one handler class for it that implements the screen's handler interface. Do not add a branch to another handler and do not put logic in the view model.
3. Decide the strategy: use the skill eventus-choose-strategy.
4. Decide what the handler produces. What the screen must still show if drawn again is state. What the UI does once is an effect. Work that needs no UI is done through the handler's dependencies.
5. If the handler changes data that the screen observes from a repository, call the repository and write nothing to the state. A value that comes from the event may be saved as it is; never compute the value to save from the current state.
6. Register the handler where the screen's other handlers are registered, unless a Koin module or Metro bindings are generated.
7. Send the event from the UI with onEvent.
8. Add a test class for the handler and build. If the build says "No handler for …", a handler class is missing.
```

### Choosing a strategy

```markdown
---
name: eventus-choose-strategy
description: "Decide how an Eventus event handler behaves when events overlap. Use when a handler calls a repository or does other slow work, when taps can repeat, or when a bug involves duplicated work, stale results or changes applied in the wrong order."
---

Read the section "Choosing a dispatch strategy" of the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

A strategy has two parts. Decide both.

Part 1, what should happen when a competing event is still active. Take the first that fits:

- The work must not start again while it runs (save, pay, refresh, download): DropIfAlreadyRunning.
- The newer event makes the older one pointless (search text, slider, a toggle whose event carries the new value): Latest.
- Every event must be applied, in the order sent (a plus button, anything that depends on the result of the one before): Sequential.
- None of these: leave the default, Concurrent, and stop here.

Part 2, which events compete with it. This goes in the angle brackets:

- Only events of its own class: the event's own class, as in Latest<OnQueryChange>().
- Several different events that affect the same thing (query, filter and sort reload one list; save and delete exclude each other): give them one sealed parent type and name the parent, as in Latest<OnCriteriaChange>(). Declare the strategy once as a top-level val and use that val in every one of those handlers.
- Only events about the same item of a list: add a key in braces in place of the parentheses, as in Latest<OnLikeChange> { it.trackId }. For several events per item, the sealed parent declares the key.

Write a strategy function by hand only if no combination of the two parts says it, for example when one event cancels another but not the reverse. The function must only compute its answer, and must name the events it looks for.
```

### Finding out why an event did nothing

```markdown
---
name: eventus-debug-event
description: "Investigate a bug in a screen that uses the Eventus library where a tap does nothing, a result never arrives, work runs twice, or changes apply in the wrong order."
---

Read the section "Logging and debugging" of the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

1. If the screen's router has no observer, pass one to EventRouter(...) that prints each report.
2. Reproduce the bug and read the lines for the event, which share its number.
3. Read the outcome:
   - "is ignored": a DropIfAlreadyRunning strategy found an active event. Find which one and whether its handler ever returns.
   - "was replaced by #n": a Latest strategy cancelled it. Check whether the two events should share a strategy.
   - "waits for #n" with no "started" after it: the event it waits for never ends. A handler that observes data must not be in a Sequential group.
   - "failed: …": the handler threw. Check the screen's ErrorHandler.
   - "was cancelled with the scope": the view model was cleared while it ran.
   - No line at all: the UI did not call onEvent, or the event's strategy function threw.
   - "started" and "completed" but nothing on screen: the handler did not change the state, or the state was mutated in place.
4. If two events race and neither "is ignored" nor "replaces" appears, they have separate strategies: use the skill eventus-choose-strategy.
5. If the project logs its routers, leave the observer and send it to the project's logger. Otherwise remove the one you added.
```

### Testing

```markdown
---
name: eventus-test
description: "Write or fix tests for code that uses the Eventus library, such as an event handler, a dispatch strategy, an error handler or a view model."
---

Read the section "Testing" of the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

- A handler: create a ScreenStore with a starting state, call handle(event, store) inside runTest, then assert on store.current and on store.effects. No router and no view model.
- A handler that never returns: launch it in backgroundScope.
- A strategy: call handler.strategy.decide(newEvent, listOf(activeEvents)) and compare the answer with assertEquals. No coroutines needed.
- An error handler: call handle(event, exception, store) and assert on the store.
- A view model or a router: pass backgroundScope as its scope, never the test's own scope. Send events with onEvent and call testScheduler.runCurrent() after each. Do not use advanceUntilIdle().
- A view model that sends an event from init: call testScheduler.runCurrent() once after creating it.
- A handler with a delay, tested through a router: call testScheduler.advanceTimeBy(duration), then testScheduler.runCurrent().
- To check what the router did, pass an observer that adds each report to a list and compare the list.
- Use fakes for repositories, not mocks. Put tests in the test source set.
```

### Reviewing Eventus code

```markdown
---
name: eventus-review
description: "Review a change in a project that uses the Eventus library, or check your own Eventus code before finishing."
---

Read the section "The rules" of the Eventus agent guide (your saved copy, or https://raw.githubusercontent.com/mradzinski/Eventus/main/AGENT-USAGE.md) if you have not in this session.

Report each of these as a defect:

- A view model with logic, a when over events, or a function other than what EventRouter gives it. Sending the opening event from init and passing an observer are fine.
- An event of a sealed event type with no handler class, or with two.
- A handler whose event type is a sealed or abstract parent.
- A handler that keeps state of the screen in a property.
- A handler that catches an exception to show a message, or catches CancellationException without rethrowing it.
- State mutated in place, or a mutable collection inside the state.
- A handler that calls a repository with the default strategy, where a repeat or an overlap would be wrong.
- Two handlers that must agree, each with a strategy of its own, where one shared val is needed.
- A strategy function that does work, or that tests whether the active list is empty.
- A handler that computes a value from the current state and saves it, for data the screen observes.
- The ScreenStore passed to the UI, or effects collected in more than one place.
- A message the user must see sent as an effect that shows for a moment.
- A test that gives the router the test's own scope.
- A name that the guide does not list.
```

## Before you finish

- Every event of every sealed event type has exactly one handler class.
- No view model contains logic or a `when` over events.
- Every strategy other than `Concurrent` names a type in brackets.
- No handler catches an exception only to show a message.
- The project builds for every target, and each new handler has a test.

You do not need anything else to use Eventus. The same topics are written for people, at more
length, at `https://github.com/mradzinski/Eventus/tree/main/docs`.
