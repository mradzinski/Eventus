# Dependency injection

Eventus needs no dependency-injection library, and works with any. Two facts make the wiring simple:

- **Event handlers hold nothing about the screen.** State and effects arrive through `handle`, so
  your container can create a handler fresh for each view model (`factory`) with no scope of its
  own.
- **The view model takes a `Collection` of handlers.** Adding an event never changes the view model.

## Contents

- [Giving each screen an event handler interface](#giving-each-screen-an-event-handler-interface)
- [Using Koin](#using-koin)
- [Using Metro](#using-metro)
- [Using Hilt](#using-hilt)
  - [Providing the event handlers](#providing-the-event-handlers)
  - [Writing the view model](#writing-the-view-model)
  - [Using Hilt in a multiplatform module](#using-hilt-in-a-multiplatform-module)
- [Using another container that resolves at compile time](#using-another-container-that-resolves-at-compile-time)
- [Wiring by hand](#wiring-by-hand)

## Giving each screen an event handler interface

One line per screen pays for itself:

```kotlin
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

typealias TaskListStore = ScreenStore<TaskListUiState, TaskListEffect>

class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> {

    override val eventType = OnRefresh::class
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<OnRefresh>()

    override suspend fun handle(event: OnRefresh, store: TaskListStore) { /* … */ }
}
```

Handlers get shorter, and your container gets a type that means "the handlers of this screen".

## Using Koin

```kotlin
val taskListModule = module {
    factoryOf(::OnScreenOpenedHandler) bind TaskListHandler::class
    factoryOf(::OnRefreshHandler) bind TaskListHandler::class
    factoryOf(::OnTaskClickHandler) bind TaskListHandler::class
    // …one line per handler

    viewModel { TaskListViewModel(handlers = getAll<TaskListHandler<*>>()) }
}
```

Koin looks definitions up by class and ignores type arguments. The per-screen interface is what
keeps the event handlers of one screen apart from those of another:

> [!TIP]
> **Do:** ask Koin for the handler interface of the screen.
>
> ```kotlin
> getAll<TaskListHandler<*>>()
> ```

> [!CAUTION]
> **Don't:** ask Koin for `EventHandler` with the screen's types. That call returns the event
> handlers of every screen in the app.
>
> ```kotlin
> getAll<EventHandler<*, TaskListUiState, TaskListEffect>>()
> ```

A view model must be cleared when its screen is gone, or its router never stops:

> [!TIP]
> **Do:** register the view model with `viewModel { }`.
>
> ```kotlin
> viewModel { TaskListViewModel(handlers = getAll<TaskListHandler<*>>()) }
> ```

> [!CAUTION]
> **Don't:** register it with `single { }`. A singleton view model is never cleared.
>
> ```kotlin
> single { TaskListViewModel(handlers = getAll<TaskListHandler<*>>()) }
> ```

You do not have to write the handler lines by hand: `eventus-ksp` generates them. See [checking and
wiring event handlers at build time](checking-and-wiring-event-handlers-at-build-time.md).

## Using Metro

Provide each event handler into a set of the screen's handler interface, and inject that set into
the view model:

```kotlin
@ContributesTo(AppScope::class)
@BindingContainer
object TaskListHandlerBindings {

    @Provides
    @IntoSet
    fun onRefreshHandler(tasks: TaskRepository): TaskListHandler<*> = OnRefreshHandler(tasks)

    // …one function per handler
}

@Inject
class TaskListViewModel(handlers: Set<TaskListHandler<*>>) : /* … */
```

You do not have to write the container by hand: `eventus-ksp` generates it from one annotation on
the handler interface. See [checking and wiring event handlers at build
time](checking-and-wiring-event-handlers-at-build-time.md#generating-the-metro-bindings).

## Using Hilt

Eventus is a Kotlin Multiplatform library, and Hilt runs on Android only. So Eventus supports Hilt
as far as Hilt goes today: you wire the event handlers yourself, in Android code, and `eventus-ksp`
generates nothing for Hilt. The processor still checks that every event has an event handler.

What follows was tested with Hilt 2.60.1.

### Providing the event handlers

Write one Hilt module for each screen. It provides each event handler into a set of the screen's
handler interface:

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
internal object TaskListHandlerBindings {

    @Provides
    @IntoSet
    fun onRefreshHandler(tasks: TaskRepository): TaskListHandler<*> = OnRefreshHandler(tasks)

    @Provides
    @IntoSet
    fun onTaskClickHandler(): TaskListHandler<*> = OnTaskClickHandler()

    // …one function per event handler
}
```

The event handler classes take no Hilt annotation, and both the module and the classes can stay
`internal`. A screen's error handler takes Hilt's own `@Inject`:

```kotlin
class TaskListErrorHandler @Inject constructor() :
    ErrorHandler<TaskListEvent, TaskListUiState, TaskListEffect> { /* … */ }
```

### Writing the view model

Two things differ from a view model written for Koin or Metro:

```kotlin
@HiltViewModel
class TaskListViewModel internal constructor(
    handlers: Set<TaskListHandler<*>>,
    errorHandler: TaskListErrorHandler,
    scope: CoroutineScope,
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
    ) {

    @Inject
    constructor(
        handlers: Set<@JvmSuppressWildcards TaskListHandler<*>>,
        errorHandler: TaskListErrorHandler,
    ) : this(handlers, errorHandler, routerScope())
}
```

- **Hilt uses the second constructor, which passes `routerScope()`.** Hilt asks its graph for every
  parameter of the constructor it calls and does not use a default value, so
  `scope: CoroutineScope = routerScope()` fails the build with a missing binding. A test calls the
  first constructor and passes its own scope.
- **The set carries `@JvmSuppressWildcards` where Hilt injects it.** This is Dagger's rule for a set
  injected into Kotlin code:

> [!TIP]
> **Do:** annotate the element type of the set.
>
> ```kotlin
> handlers: Set<@JvmSuppressWildcards TaskListHandler<*>>
> ```

> [!CAUTION]
> **Don't:** leave the annotation out. The build fails with a missing binding for
> `Set<? extends TaskListHandler<?>>`.
>
> ```kotlin
> handlers: Set<TaskListHandler<*>>
> ```

### Using Hilt in a multiplatform module

Event handlers written in common code work with Hilt on Android. Put the Hilt module in
`androidMain`, and set the module's build script up for Hilt's processor:

```kotlin
kotlin {
    android {
        withJava()   // Hilt generates Java code, which this line compiles
    }

    sourceSets {
        androidMain.dependencies {
            implementation("com.google.dagger:hilt-android:2.60.1")
        }
    }
}

dependencies {
    add("kspAndroid", "com.google.dagger:hilt-compiler:2.60.1")
}
```

Without `withJava()` the app that uses the module fails to build, with a missing binding for the
set. The other platforms need a container of their own, such as [Koin](#using-koin) or
[Metro](#using-metro).

## Using another container that resolves at compile time

Containers such as Dagger keep type arguments, so there you can multibind each handler into
`Set<EventHandler<*, TaskListUiState, TaskListEffect>>` and inject that set into the view model.
For Hilt, which is built on Dagger, see [using Hilt](#using-hilt).

## Wiring by hand

```kotlin
TaskListViewModel(
    handlers = listOf(
        OnScreenOpenedHandler(tasks),
        OnRefreshHandler(tasks),
        OnTaskClickHandler(),
    ),
)
```

---

[Back to the README](../README.md#documentation)
