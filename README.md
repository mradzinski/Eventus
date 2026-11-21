[![Maven Central](https://img.shields.io/maven-central/v/io.github.mradzinski/eventus?style=flat-square)](https://central.sonatype.com/artifact/io.github.mradzinski/eventus)
[![Build](https://img.shields.io/github/actions/workflow/status/mradzinski/eventus/build.yml?branch=main&style=flat-square&label=build)](https://github.com/mradzinski/eventus/actions/workflows/build.yml)
[![Coverage](https://img.shields.io/codecov/c/github/mradzinski/eventus?style=flat-square&label=coverage)](https://codecov.io/gh/mradzinski/eventus)

# Eventus

An event router for MVVM screens on Kotlin Multiplatform. The UI sends events, each event goes to
its own small handler class, and the screen's state and one-time effects come back as flows. It
runs on **Android, iOS, Desktop and Web** from common code.

- One event, one handler class, one test class: a screen with fifty events is fifty small files, not one
  view model with fifty functions
- Four ways to run a handler when its event arrives again: at once, latest only, in order, or ignore
  while busy
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
- [Documentation](#documentation)
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

That is all the library needs. One more step is optional, and recommended.

### Recommended: the build-time check

`eventus-ksp` is a [KSP](https://kotlinlang.org/docs/ksp-overview.html) processor. With it, an event
that has no handler fails the build; without it, that event throws when it is sent. It runs while your
app is built and is never part of the app.

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

If you use Koin, the processor can also write the Koin module of your handlers. That takes one more
artifact, `eventus-koin`, and one annotated property. See
[handlers checked and wired by the build](docs/handlers-checked-and-wired-by-the-build.md) for both.

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

Every event needs exactly one handler. With [`eventus-ksp`](#recommended-the-build-time-check) the
build tells you when one is missing: add an event to `CounterEvent` and forget its handler, and the
module no longer compiles.

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

## Documentation

Each topic has a page of its own in [`docs/`](docs). They are listed with what most apps need first.

| Guide | What it covers |
|---|---|
| [Key concepts](docs/key-concepts.md) | The types of the library and what each one is, and the three rules that follow from how they fit together. |
| [Dispatch strategies](docs/dispatch-strategies.md) | What happens when an event arrives again while its handler is still running, and what the router keeps for each strategy. |
| [Dependency injection](docs/dependency-injection.md) | Wiring handlers with Koin, with a compile-time container, or by hand. |
| [Handlers checked and wired by the build](docs/handlers-checked-and-wired-by-the-build.md) | The `eventus-ksp` processor: the missing-handler check, the generated Koin module, and projects with several modules. |
| [Failures](docs/failures.md) | Where a handler's exception goes, and how to turn it into something the UI can show. |
| [Work that keeps running](docs/work-that-keeps-running.md) | Handlers that never return, such as one that collects a flow, and how long they last. |
| [Testing](docs/testing.md) | Testing a handler, a view model, and that every event has a handler. |
| [Effect or state?](docs/effect-or-state.md) | How to decide whether something is an effect or part of the state. |
| [Navigation](docs/navigation.md) | Navigating as an effect that carries only an id. |
| [Debouncing](docs/debouncing.md) | Waiting until the user has stopped typing before doing the expensive part. |
| [Optimistic updates](docs/optimistic-updates.md) | Showing a result at once and putting it back if the work fails. |
| [Reaching another screen](docs/reaching-another-screen.md) | How one screen affects another without an event bus. |
| [The coroutine scope](docs/the-coroutine-scope.md) | Which scope the router runs in, who cancels it, and threads. |
| [Logging events](docs/logging-events.md) | Logging every event of a screen with a router that wraps another. |
| [Where Eventus sits in your app](docs/where-eventus-sits-in-your-app.md) | Which modules of an app depend on Eventus and which never do. |
| [What Eventus does not do](docs/what-eventus-does-not-do.md) | The limits of the library, stated plainly. |
| [Project layout](docs/project-layout.md) | The modules of this repository, and how to run the sample, the checks and the formatter. |

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

Eventus is released under the [Apache License 2.0](LICENSE). If you ship it in an app, please keep the
[NOTICE](NOTICE) file with it, as the license asks.
