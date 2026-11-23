# Changelog

What changed in each version of Eventus, newest first. Versions follow
[semantic versioning](https://semver.org).

## 1.0.0-rc02 (not released yet)

### Added

- **Metro support.** `eventus-ksp` generates the Metro bindings of a screen's event handlers from
  one annotation on the screen's handler interface, `@ContributesEventHandlers(AppScope::class)`.
  The view model injects `Set<TaskListHandler<*>>`, and the event handler classes take no
  annotation. It works in multiplatform modules and in modules with a single platform, and across
  Gradle modules. Written for and tested with Metro 1.6.0. See [checking and wiring event handlers
  at build time](docs/checking-and-wiring-event-handlers-at-build-time.md#generating-the-metro-bindings).
- `eventus-metro`: a new artifact that holds that annotation and nothing else.

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

`AGENT-USAGE.md` is one file with what an agent needs to use the library, and skills it can save.
