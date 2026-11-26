# Using Eventus from SwiftUI

A view model written in common code works behind a SwiftUI screen as it does behind a Compose one:
the screen draws `state`, performs `effects` and calls `onEvent`. Eventus needs nothing extra for
this, and you do not have to export it into your iOS framework.

Kotlin reaches Swift through Objective-C, which loses the type of a flow. That is true of every
Kotlin Multiplatform project, with or without Eventus, and two libraries give the types back. Both
work with an Eventus view model, so use the one your project already has, or pick by this table:

| | SKIE | KMP-NativeCoroutines |
|---|---|---|
| Kotlin you write for each view model | Nothing | Two annotated properties |
| A `switch` over effects | Exhaustive | Needs a `default` branch |
| On the Swift side | Nothing to add | A Swift package |

This page was tested on the iOS simulator with Kotlin 2.4.21, SKIE 0.10.16, KMP-NativeCoroutines
1.0.7 and Lifecycle 2.11.1. Each library supports specific Kotlin versions: check its own
compatibility notes before you choose a version.

## Contents

- [What Swift sees of a view model](#what-swift-sees-of-a-view-model)
- [Creating the view model and ending it](#creating-the-view-model-and-ending-it)
- [Using SKIE](#using-skie)
  - [Setting up SKIE](#setting-up-skie)
  - [Drawing the state and performing the effects with SKIE](#drawing-the-state-and-performing-the-effects-with-skie)
- [Using KMP-NativeCoroutines](#using-kmp-nativecoroutines)
  - [Setting up KMP-NativeCoroutines](#setting-up-kmp-nativecoroutines)
  - [Exposing the state and the effects](#exposing-the-state-and-the-effects)
  - [Drawing the state and performing the effects with KMP-NativeCoroutines](#drawing-the-state-and-performing-the-effects-with-kmp-nativecoroutines)
- [What you can rely on](#what-you-can-rely-on)

## What Swift sees of a view model

Take this view model, in common code:

```kotlin
class TaskListViewModel(
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<TaskListEvent, TaskListUiState, TaskListEffect> by EventRouter(
        scope = scope,
        initialState = TaskListUiState(),
        handlers = listOf(OnRefreshHandler(), OnTaskClickHandler()),
    )
```

- **`onEvent` is typed.** Swift can pass it only a `TaskListEvent`. An event that is a
  `data object` is written with `.shared`, and one that is a `data class` with its initializer:

  ```swift
  viewModel.onEvent(event: TaskListEventOnRefresh.shared)
  viewModel.onEvent(event: TaskListEventOnTaskClick(id: 7))
  ```

- **`state` and `effects` are not typed** until you add one of the two libraries.
- **A default argument does not reach Swift**, so Swift cannot write `TaskListViewModel()`. Give
  Swift a factory, as the next section shows.

## Creating the view model and ending it

SwiftUI has no view model of its own, so something has to create the view model and clear it when
the screen is gone. Follow Google's guide, [Set up ViewModel for
KMP](https://developer.android.com/kotlin/multiplatform/viewmodel). It gives you two small files to
copy, `IosViewModelStoreOwner.swift` and `ViewModelResolver.ios.kt`, and has you export the
Lifecycle library into your framework.

What you add for each view model is a factory, in common code:

```kotlin
val taskListViewModelFactory = viewModelFactory {
    initializer { TaskListViewModel() }
}
```

The screen holds the owner, asks it for the view model, and hands the view model to the view that
draws it. Swift names the factory after its Kotlin file, here `TaskList.kt`:

```swift
struct TaskListScreen: View {
    @StateObject private var owner = IosViewModelStoreOwner()

    var body: some View {
        let viewModel: TaskListViewModel = owner.viewModel(factory: TaskListKt.taskListViewModelFactory)

        TaskListContent(viewModel: viewModel)
    }
}
```

When the screen goes away, the owner clears the view model. That cancels the router's scope, and
with it every event handler that is still running.

## Using SKIE

### Setting up SKIE

Add the SKIE plugin to the Gradle module that builds your iOS framework:

```kotlin
plugins {
    id("co.touchlab.skie") version "0.10.16"
}
```

SKIE needs no code in Kotlin and nothing on the Swift side.

### Drawing the state and performing the effects with SKIE

With SKIE, `state` is a typed flow that has the current value, and both flows work in a
`for await` loop. A `switch` over `onEnum(of:)` covers every effect, and stops compiling when you
add an effect and forget it here:

```swift
struct TaskListContent: View {
    let viewModel: TaskListViewModel

    @State private var state: TaskListUiState
    @State private var openedTask: Int64?

    init(viewModel: TaskListViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.state.value)
    }

    var body: some View {
        VStack {
            ForEach(state.tasks, id: \.self) { task in Text(task) }

            Button("Refresh") { viewModel.onEvent(event: TaskListEventOnRefresh.shared) }
        }
        .task {
            for await newState in viewModel.state {
                state = newState
            }
        }
        .task {
            for await effect in viewModel.effects {
                switch onEnum(of: effect) {
                case .openTask(let open): openedTask = open.id
                case .showFailure: break   // show why
                }
            }
        }
    }
}
```

## Using KMP-NativeCoroutines

### Setting up KMP-NativeCoroutines

Add the plugin and its opt-in to the Gradle module that builds your iOS framework:

```kotlin
plugins {
    id("com.rickclephas.kmp.nativecoroutines") version "1.0.7"
}

kotlin {
    sourceSets.all { languageSettings.optIn("kotlin.experimental.ExperimentalObjCName") }
}
```

Then add its Swift package to your Xcode project, in the same version, with the
`KMPNativeCoroutinesAsync` product.

### Exposing the state and the effects

KMP-NativeCoroutines works on the flows that your own code declares and annotates. A view model
gets `state` and `effects` from the router and declares neither, so give the plugin two properties
of your own for each view model, in common code:

```kotlin
@NativeCoroutinesState
val TaskListViewModel.uiState: StateFlow<TaskListUiState>
    get() = state

@NativeCoroutines
val TaskListViewModel.uiEffects: Flow<TaskListEffect>
    get() = effects
```

Swift then has three typed members on the view model: `uiState`, the state right now;
`uiStateFlow`, the state as it changes; and `uiEffects`.

### Drawing the state and performing the effects with KMP-NativeCoroutines

```swift
struct TaskListContent: View {
    let viewModel: TaskListViewModel

    @State private var state: TaskListUiState
    @State private var openedTask: Int64?

    init(viewModel: TaskListViewModel) {
        self.viewModel = viewModel
        _state = State(initialValue: viewModel.uiState)
    }

    var body: some View {
        VStack {
            ForEach(state.tasks, id: \.self) { task in Text(task) }

            Button("Refresh") { viewModel.onEvent(event: TaskListEventOnRefresh.shared) }
        }
        .task {
            do {
                for try await newState in asyncSequence(for: viewModel.uiStateFlow) {
                    state = newState
                }
            } catch {
                // the flow failed
            }
        }
        .task {
            do {
                for try await effect in asyncSequence(for: viewModel.uiEffects) {
                    switch effect {
                    case let open as TaskListEffectOpenTask: openedTask = open.id
                    default: break
                    }
                }
            } catch {
                // the flow failed
            }
        }
    }
}
```

Swift cannot check that this `switch` covers every effect, so it needs a `default` branch.

## What you can rely on

- **An effect waits for the screen.** SwiftUI cancels a `.task` when its view goes away and starts
  it again when the view comes back. An effect emitted in between stays in the store and reaches
  the next loop. See [emitting an effect](emitting-an-effect.md).
- **Perform the effects in one `.task` for each screen.** Each effect goes to one collector, so two
  loops would compete for them.
- **The view model outlives the view that draws it**, for as long as the screen holds the owner. A
  view that is drawn again gets the current state first.
- **Everything else is common code.** The event handlers, their dispatch strategies, the error
  handler and their tests do not know which UI is in front of them.

---

[Back to the README](../README.md#documentation)
