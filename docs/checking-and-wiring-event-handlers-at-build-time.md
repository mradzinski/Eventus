# Checking and wiring event handlers at build time

`eventus-ksp` is an optional, recommended [Kotlin Symbol Processing
(KSP)](https://kotlinlang.org/docs/ksp-overview.html) processor. It runs while your app is built,
and is never part of the app. It does this in the Gradle module you add it to:

- **It checks the event handlers.** An event without a handler class fails the build, not the app.
- **It writes the Koin module of the event handlers**, when you ask for it with one annotated
  property.
- **It writes the Metro bindings of the event handlers**, when you ask for them with one annotation
  on each screen.

## Contents

- [Setting up](#setting-up)
- [What the check finds](#what-the-check-finds)
- [Generating the Koin module](#generating-the-koin-module)
- [Generating the Metro bindings](#generating-the-metro-bindings)
  - [Declaring the graph](#declaring-the-graph)
  - [What the processor needs](#what-the-processor-needs)
- [Working with several modules](#working-with-several-modules)
  - [With Koin](#with-koin)
  - [With Metro](#with-metro)

## Setting up

Add the KSP plugin and the processor as shown in
[the build-time check](../README.md#recommended-adding-the-build-time-check) under Installing.

Building one target runs the processor for that target only, so a day of running the Android app runs
it once per build, not once per target.

## What the check finds

For every sealed event type declared in the module, each of these fails the build, with the file and
line:

- **An event with no handler class**, in the sealed type or in a sealed type nested in it.
- **An event with two handler classes.**
- **A handler the router can never call**: one whose event type is a sealed or abstract type, or a type
  parameter. The router sends an event to the handler of its exact class.
- **A view model that routes a wider type than it can handle.** A class that implements
  `EventRouter<TasksEvent, …>` accepts every `TasksEvent`. When some of those events have handlers
  written for another state or other effects, this view model can never handle them. See [several
  view models in one feature](key-concepts.md#several-view-models-in-one-feature).

```text
e: [ksp] TaskListEvent.kt:14: No handler for com.example.TaskListEvent.OnRefresh. Add a class that
implements EventHandler for it.
```

The check of a view model's type looks at classes that implement `EventRouter`, as a view model does
by delegation. It tells the handlers of one view model from those of another by their state and
effect types.

The processor looks at one Gradle module at a time, so **a screen's events and their handlers live in
the same Gradle module**. See [working with several modules](#working-with-several-modules).

## Generating the Koin module

Add the annotation to the Gradle module's common dependencies:

```kotlin
commonMain.dependencies {
    implementation("io.github.mradzinski:eventus-koin:1.0.0-rc02")
}
```

Then declare the Koin module once, in common code, and leave its content to the processor:

```kotlin
// The Eventus processor generates the `actual` property on each target.
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module
```

Android Studio cannot see the generated `actual` property and may warn that the `expect` property has
none. The `@Suppress` line silences that warning; the build does not need it.

On each target the processor writes the `actual` property. The property holds one factory for every
event handler class, bound to the [handler interface of its
screen](dependency-injection.md#giving-each-screen-an-event-handler-interface), and one for every
class that implements `ErrorHandler`:

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

The processor reports a build error when one of these four rules is broken:

- **The property is an `expect` property of Koin's type `Module`**, and a Gradle module has one. It
  holds the handlers of every screen in that Gradle module.
- **Every handler implements the handler interface of its screen**, not `EventHandler` directly. That
  interface is what the view model asks Koin for.
- **No handler is `private`.** The generated code is in another file. `internal` is fine.
- **Every target has its `ksp` line.** A target without one does not compile: the compiler reports
  that the `expect` property has no `actual` declaration there.

The generated module resolves each constructor parameter of a handler with Koin's `get()`. `expect`
exists only in a multiplatform module, so a module with a single platform gets the check but not the
generated module.

## Generating the Metro bindings

Metro support starts with version `1.0.0-rc02` of `eventus-ksp` and `eventus-metro`. It was written
for and tested with Metro 1.6.0, and uses only what Metro documents.

Add the annotation to the dependencies of the Gradle module, next to Metro's own Gradle plugin:

```kotlin
commonMain.dependencies {
    implementation("io.github.mradzinski:eventus-metro:1.0.0-rc02")
}
```

Then annotate the [handler interface of each
screen](dependency-injection.md#giving-each-screen-an-event-handler-interface) with the scope of the
Metro graph that creates the screen's view model. `AppScope` is Metro's own, and the usual one:

```kotlin
@ContributesEventHandlers(AppScope::class)
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>
```

That line is all a screen writes. The event handler classes take no annotation. On each target the
processor writes one Metro binding container for the screen, contributed to that scope, with one
provider for every handler class that implements the interface:

```kotlin
// generated
@ContributesTo(dev.zacsweers.metro.AppScope::class)
@BindingContainer
@Origin(com.example.tasklist.TaskListHandler::class)
public object EventusTaskListHandlerBindings {

    @Provides
    @IntoSet
    public fun onRefreshHandler(
        p0: com.example.data.TaskRepository,
    ): com.example.tasklist.TaskListHandler<*> = com.example.tasklist.OnRefreshHandler(p0)

    // …one provider for every handler of this screen
}
```

The view model asks Metro for the set, and an error handler takes Metro's own `@Inject`:

```kotlin
@Inject
class TaskListViewModel(
    handlers: Set<TaskListHandler<*>>,
    errorHandler: TaskListErrorHandler,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = handlers,
        errorHandler = errorHandler,
    )

@Inject
class TaskListErrorHandler : ErrorHandler<TaskListEvent, TaskListUiState, TaskListEffect> { /* … */ }
```

Metro uses the default value of `scope` when the graph provides no `CoroutineScope`.

A new event then takes a new event class and a new handler class, and nothing else.

### Declaring the graph

The generated container reaches every graph of its scope with no line in the graph:

```kotlin
@DependencyGraph(AppScope::class)
interface AppGraph {
    val taskListViewModel: TaskListViewModel
}
```

In a multiplatform module, declare the graph once for each platform, in that platform's source set,
such as `androidMain` or `iosMain`.
The processor writes the containers into each target, and Metro's documentation states that a graph
in common code cannot see what a platform source set contributes. Common code can still share an
interface that every platform graph extends:

```kotlin
// commonMain
interface AppGraph {
    val taskListViewModel: TaskListViewModel
}

// androidMain
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph

// iosMain
@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph
```

A module with a single platform has one graph, and the generated bindings work there too.

### What the processor needs

The processor reports a build error when one of these rules is broken:

- **The annotation is on an interface that extends `EventHandler`.**
- **The interface is not `private`**, and neither is any handler that implements it. The generated
  code is in another file. `internal` is fine.
- **The scope is a class the compiler can find.**
- **Every event handler of the screen implements the annotated interface.** A handler that implements
  `EventHandler` directly, for the same state and effects, is in no set that Metro provides.

Three more things to know:

- **The container is as visible as the interface.** An `internal` interface gets an `internal`
  container, which only a graph in the same Gradle module sees.
- **Metro supplies each constructor parameter of a handler by its type.** A qualifier on the
  parameter, such as `@Named("io")`, is kept. A default value is not used: Metro is asked for every
  parameter.
- **An interface with no handler classes gets no container.** Metro then reports that it has no
  binding for the set.

## Working with several modules

Keep a screen's events and its handlers in the same Gradle module. That is how Eventus is meant to be
used, and it is what the processor relies on: it reads the sources of the module it runs in, and
nothing else. If a handler is in a different module from its event, the processor cannot check it.

Each module that has handlers then repeats the same setup: its own `ksp` lines and, for a generated
Koin module or generated Metro bindings, its own annotations.

### With Koin

Each module declares its own annotated property, which can stay `internal`. Another module cannot name an internal property, so the feature
exposes one public Koin module that includes it:

```kotlin
// :feature:tasklist
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

val taskListFeatureModule: Module = module {
    includes(handlersModule)

    viewModel {
        TaskListViewModel(
            handlers = getAll<TaskListHandler<*>>(),
            errorHandler = get(),
        )
    }
}
```

The app lists one module per feature and never names a handler:

```kotlin
// :app
startKoin {
    modules(taskListFeatureModule, taskEditorFeatureModule)
}
```

Every feature can call its property `handlersModule` and have a handler called `OnScreenOpenedHandler`:
the properties are internal to their modules, and each screen asks for its own handler interface, so
one feature never receives another's handlers. A missing handler fails the build of its own feature
module only.

### With Metro

Each feature module annotates its own handler interfaces, and the module that declares the graph
depends on the features. Nothing else connects them: Metro finds the contributions of the modules
the graph's module depends on.

```kotlin
// :feature:tasklist
@ContributesEventHandlers(AppScope::class)
interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>

internal class OnRefreshHandler(private val tasks: TaskRepository) : TaskListHandler<OnRefresh> { /* … */ }

// :app, once for each platform
@DependencyGraph(AppScope::class)
interface AndroidAppGraph {
    val taskListViewModel: TaskListViewModel
}
```

A graph in another Gradle module sees only a public container, so in a feature module the handler
interface is public, and so are the types its handlers take in their constructors. The handler
classes can stay `internal`. A container that is not public is invisible to a graph in another
module, and Metro then reports that it has no binding for the set.

Two features can each have a handler called `OnScreenOpenedHandler`: every screen has its own
container and its own set.

---

[Back to the README](../README.md#documentation)
