# Handlers checked and wired by the build

`eventus-ksp` is an optional, recommended [KSP](https://kotlinlang.org/docs/ksp-overview.html)
processor. It runs while your app is built, and is never part of the app. It does two things in the Gradle module you add
it to:

- **It checks the handlers.** An event without a handler class fails the build, not the app.
- **It writes the Koin module of the handlers**, when you ask for it with one annotated property.

## Set up

Add the KSP plugin and the processor as shown in
[the build-time check](../README.md#recommended-the-build-time-check) under Install.

Building one target runs the processor for that target only, so a day of running the Android app runs
it once per build, not once per target.

## What the check finds

For every sealed event type declared in the module, each of these fails the build, with the file and
line:

- **An event with no handler class**, in the sealed type or in a sealed type nested in it.
- **An event with two handler classes.**
- **A handler the router can never call**: one whose event type is a sealed or abstract type, or a type
  parameter. The router sends an event to the handler of its exact class.

```text
e: [ksp] TaskListEvent.kt:14: No handler for com.example.TaskListEvent.OnRefresh. Add a class that
implements EventHandler for it.
```

The processor looks at one Gradle module at a time, so **a screen's events and their handlers live in
the same Gradle module**. See [a project with several modules](#a-project-with-several-modules).

## The Koin module

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
class, bound to the [handler interface of its screen](dependency-injection.md#give-each-screen-a-handler-interface), and one
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

## A project with several modules

Keep a screen's events and its handlers in the same Gradle module. That is how Eventus is meant to be
used, and it is what the processor relies on: it reads the sources of the module it runs in, and
nothing else. If a handler is in a different module from its event, the processor cannot check it.

Each module that has handlers then repeats the same setup: its own `ksp` lines, and its own annotated
property, which can stay `internal`. Another module cannot name an internal property, so the feature
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

---

[Back to the README](../README.md#documentation)
