# Changelog

What changed in each version of Eventus, newest first. Versions follow
[semantic versioning](https://semver.org).

## 1.0.0-rc03 (not released yet)

### Added

- **An event handler can dispatch another event.** `store.dispatch(OnCartRefresh)` hands an event of
  the same screen to the router, from an event handler or from the screen's `ErrorHandler`. The event is
  handled like any other: by its own event handler, under its own dispatch strategy, with its own
  reports. `dispatch` returns the `Job` that handles the event, so `store.dispatch(…).join()` waits
  for it. See [dispatching an event from a handler](docs/dispatching-an-event-from-a-handler.md).

- **Effects in the router's reports.** A router with an `observer` now reports each effect at the
  moment it is emitted, with the event it came from: `#3 OnSaveClick emitted Close`. That includes
  an effect the screen's `ErrorHandler` emits for a failed event. The UI still gets every effect.
  See [logging events](docs/logging-events.md).

- **`eventus-test`, a new artifact for testing event handlers.** One function,
  `runEventHandlerTest`, runs one event handler or several through a router inside a coroutine test.
  In its block a test dispatches events with `dispatch`, moves the clock with `advanceBy`, waits for
  work on another thread with `awaitIdle`, and reads `state`, `states`, `effects`, `reports` and `failures`.
  A failure the test never reads fails the test, and a test that runs out of time names the events
  still being handled. It depends on `kotlinx-coroutines-test` only and works beside any other
  testing tool. See [testing](docs/testing.md).

- **A build error for an event handler that Metro would leave out.** With
  `@ContributesEventHandlers`, a handler that implements `EventHandler` directly, and not the
  annotated interface of its screen, now fails the build. Before, the build passed and the screen
  threw when that event was sent.

### Changed

- **`ErrorHandler.handle` is a suspending function.** An error handler can now wait, for example for
  an event it dispatched: `store.dispatch(OnSignOut).join()`. A class that implements `ErrorHandler` must add
  `suspend` to its `handle`; an error handler written as a lambda needs no change. The error handler
  runs in the coroutine of the event that failed, so that event stays active until it returns. An
  error handler that throws fails in the router's scope as an uncaught error.

- `RouterTrace` has a new report, `EffectEmitted`. A `when` that lists every report and has no
  `else` no longer compiles until it lists the new one. A one-line observer, such as
  `observer = { println(it) }`, needs no change.

## 1.0.0-rc02 (2026-11-23)

### Added

- **Metro support.** `eventus-ksp` generates the Metro bindings of a screen's event handlers from
  one annotation on the screen's handler interface, `@ContributesEventHandlers(AppScope::class)`.
  The view model injects `Set<TaskListHandler<*>>`, and the event handler classes take no
  annotation. It works in multiplatform modules and in modules with a single platform, and across
  Gradle modules. Written for and tested with Metro 1.6.0. See [checking and wiring event handlers
  at build time](docs/checking-and-wiring-event-handlers-at-build-time.md#generating-the-metro-bindings).
- `eventus-metro`: a new artifact that holds that annotation and nothing else.
- **A build-time check of a view model's event type.** `eventus-ksp` now fails the build when a class
  that implements `EventRouter` routes a wider type than it can handle: for example, a view model
  that routes a whole feature's event type while other view models handle parts of it. Before, such
  a view model threw when it was sent one of those events. See [several view models in one
  feature](docs/key-concepts.md#several-view-models-in-one-feature).

## 1.0.0-rc01 (2026-11-22)

The first release. It is a release candidate: the API is meant to stay as it is, and this version
is for trying it in real apps before 1.0.0.

### What the library does

- **Routes each UI event of a screen to its own event handler class.** A view model implements
  `EventRouter` by delegation and extends nothing from the library.
- **Keeps the screen's state and effects in one store**, which the router creates and passes to
  every event handler. State is one immutable value in a `StateFlow`. Each effect is delivered once.
- **Decides what happens when events overlap, with a dispatch strategy for each event handler.**
  Four are ready-made: `Concurrent`, `Latest`, `Sequential` and `DropIfAlreadyRunning`. Each of the
  last three names the events it looks at: the handler's own event, a type that several events
  share, or a type and a key for each item of a list. A strategy can also be a function of your own.
- **Sends every failure of an event handler to one `ErrorHandler`** for the screen.
- **Updates the state optimistically in one call**, and puts it back if the work fails.
- **Reports what it does with each event** to an optional `RouterObserver`: what ran, what was
  ignored, what was replaced and how each run ended. One line of code gives a log.
- **Lives as long as the scope it is given.** In a view model, a rotation does not interrupt a
  running event.

### The artifacts

- `eventus`: the library. It depends on `kotlinx.coroutines` and `atomicfu`.
- `eventus-ksp`: an optional symbol processor. It fails the build when an event has no handler class or
  more than one, and it can generate the Koin module of a module's handlers.
- `eventus-koin`: the annotation that asks `eventus-ksp` for that Koin module.
- `eventus-compose`: optional helpers for a Compose UI. `collectEffects` performs a router's effects
  while the screen is started, and `notifyVisibility` tells the router when the screen is visible.

### Requirements

- Kotlin 2.4.21 or newer.
- Targets: `android` (minSdk 24), `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64`, `js` and `wasmJs`.
  `eventus-compose` has all of them except `iosX64`.

### For AI agents

The [guide for AI agents](AGENT-USAGE.md) is one file with what an agent needs to use the library, and
skills it can save.
