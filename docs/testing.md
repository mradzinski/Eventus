# Testing

`eventus-test` is the artifact for your tests. It has one function, `runEventHandlerTest`. It runs your event
handlers the way the app runs them, through a router and with their dispatch strategy, inside a
coroutine test. Your test dispatches events and reads what happened.

It does not choose your other tools. Use the assertions you already use, and any flow-testing library,
next to it.

## Contents

- [Installing](#installing)
- [Testing an event handler](#testing-an-event-handler)
  - [What the block gives you](#what-the-block-gives-you)
- [Testing several event handlers together](#testing-several-event-handlers-together)
- [Testing a failure](#testing-a-failure)
  - [Testing what the error handler does](#testing-what-the-error-handler-does)
- [Testing an event handler that waits](#testing-an-event-handler-that-waits)
- [Testing an event handler that never returns](#testing-an-event-handler-that-never-returns)
- [Testing work that runs on another thread](#testing-work-that-runs-on-another-thread)
- [Setting a time limit](#setting-a-time-limit)
- [Using other testing tools](#using-other-testing-tools)
- [Testing a dispatch strategy](#testing-a-dispatch-strategy)
- [Testing a view model](#testing-a-view-model)

## Installing

`eventus-test` is part of [installing Eventus](../README.md#installing). It goes with your test
dependencies:

```kotlin
commonTest.dependencies {
    implementation("io.github.mradzinski:eventus-test:1.0.0-rc03")
}
```

It brings `kotlinx-coroutines-test` with it and has every target of the library.

## Testing an event handler

Pass the event handler and the state the screen starts with. In the block, dispatch events and read what
happened:

```kotlin
class OnRefreshHandlerTest {

    private val tasks = FakeTaskRepository()

    @Test
    fun `a refresh shows and hides the spinner`() = runEventHandlerTest(
        handler = OnRefreshHandler(tasks),
        initialState = TaskListUiState(),
    ) {
        dispatch(OnRefresh)

        states.map { it.refreshing } shouldBe listOf(false, true, false)
        state.refreshing shouldBe false
        effects shouldBe emptyList()
    }
}
```

There is no store, router or scope to create, and nothing to clean up. Return the result from the test
function, as you do with `runTest`.

The event handler runs through a real router, so its dispatch strategy applies. This test could not
be written by calling `handle` directly:

```kotlin
@Test
fun `a second refresh is ignored while the first runs`() = runEventHandlerTest(
    handler = OnRefreshHandler(tasks),
    initialState = TaskListUiState(),
) {
    dispatch(OnRefresh, OnRefresh)

    tasks.refreshes shouldBe 1
    reports.last().toString() shouldBe "#2 OnRefresh is ignored"
}
```

### What the block gives you

| You write | You get |
|---|---|
| `dispatch(a, b)` | Each event dispatched as the UI would send it, one after the other. |
| `advanceBy(duration)` | The test's clock moved forward. |
| `awaitIdle()` | A wait until every event that was dispatched has been handled to its end. |
| `state` | The state now. |
| `states` | Every state so far, the initial one first. |
| `effects` | Every effect so far, in order. |
| `reports` | What the router did with each event; see [logging events](logging-events.md). |
| `failures` | What the event handlers threw. |
| `router`, `testScope` | The router and the coroutine test's scope, for [other tools](#using-other-testing-tools). |

You never have to tell the test to "let things run". Every read first runs whatever can run without
moving the clock. So a test can change a fake repository and read `state` on the next line.

## Testing several event handlers together

Some event handlers only make sense together, such as those that
[share a dispatch strategy](choosing-a-dispatch-strategy.md#sharing-a-strategy-between-several-events).
Pass them as a list:

```kotlin
@Test
fun `delete is ignored while a save runs`() = runEventHandlerTest(
    handlers = listOf(OnSaveClickHandler(tasks), OnDeleteClickHandler(tasks)),
    initialState = EditorUiState(),
) {
    dispatch(OnSaveClick, OnDeleteClick)

    reports.last().toString() shouldBe "#2 OnDeleteClick is ignored"
}
```

`dispatch` accepts the events of every event handler in the list. An event whose handler is not in the
list does not compile.

Pass every event handler of a screen to drive the screen as the UI does.

## Testing a failure

An event handler that throws does not make `dispatch` throw. The failure is kept in `failures`:

```kotlin
@Test
fun `a refresh that fails hides the spinner`() = runEventHandlerTest(
    handler = OnRefreshHandler(tasks),
    initialState = TaskListUiState(),
) {
    tasks.failure = OfflineException()

    dispatch(OnRefresh)

    failures.single().shouldBeInstanceOf<OfflineException>()
    state.refreshing shouldBe false
}
```

**A failure the test never reads fails the test**, with that failure. So an event handler cannot fail
unnoticed in a test that expected it to work. When an assertion fails first, the failure of the event
handler is attached to it, because it is usually the reason.

### Testing what the error handler does

To test what the user sees after a failure, pass the screen's [error handler](handling-failures.md)
too:

```kotlin
@Test
fun `a refresh that fails tells the user why`() = runEventHandlerTest(
    handler = OnRefreshHandler(tasks),
    initialState = TaskListUiState(),
    errorHandler = TaskListErrorHandler(),
) {
    tasks.failure = OfflineException()

    dispatch(OnRefresh)

    effects shouldBe listOf(TaskListEffect.ShowFailure(FailureReason.Offline))
}
```

With an error handler, a failure goes to it, as in the app. The failure is still in `failures`, but
the test no longer fails for not reading it: what the error handler did is what the test checks.

## Testing an event handler that waits

Time in the test is the test's own clock. `advanceBy` moves it:

```kotlin
@Test
fun `it searches once the user has stopped typing`() = runEventHandlerTest(
    handler = OnQueryChangeHandler(products),
    initialState = SearchUiState(),
) {
    dispatch(OnQueryChange("c"), OnQueryChange("ca"))
    advanceBy(299.milliseconds)

    state.query shouldBe "ca"
    products.searched shouldBe emptyList()

    advanceBy(1.milliseconds)

    products.searched shouldBe listOf("ca")
}
```

See [debouncing](debouncing.md) for the event handler this test is for.

## Testing an event handler that never returns

Some event handlers are meant to run for as long as the screen lives, such as one that follows a list
in a repository; see [keeping work running](keeping-work-running.md). Test one like any other: dispatch
the event, change what it follows, and read the state.

```kotlin
@Test
fun `it follows the tasks for as long as the screen lives`() = runEventHandlerTest(
    handler = OnScreenOpenedHandler(tasks),
    initialState = TaskListUiState(),
) {
    dispatch(OnScreenOpened)

    tasks.all.value = listOf(task(id = 1L), task(id = 2L))

    state.tasks shouldBe listOf(task(id = 1L), task(id = 2L))
}
```

You stop nothing. The event handler is cancelled when the block ends, and its `finally` blocks run.
There is nothing to cancel or close in the test.

When its data arrives from another thread, the test has to wait for it:

> [!TIP]
> **Do:** wait for the state you expect, with a plain coroutines call on the router's state.
>
> ```kotlin
> dispatch(OnScreenOpened)
> router.state.first { it.tasks.isNotEmpty() }
>
> state.tasks shouldBe listOf(task(id = 1L))
> ```

> [!CAUTION]
> **Don't:** call `awaitIdle()`. It waits for every event to be handled to its end, and this one has
> no end.
>
> ```kotlin
> dispatch(OnScreenOpened)
> awaitIdle()
>
> state.tasks shouldBe listOf(task(id = 1L))
> ```

The second test fails at its time limit, with a message that names the event:

```text
The test did not finish within 10s. Still being handled: #1 OnScreenOpened. An event handler that
never returns has no end to wait for: wait for the state you expect with router.state.first { … },
not with awaitIdle().
```

## Testing work that runs on another thread

The test's clock controls everything that runs on the test's own dispatcher. It does not control code
that moves to another thread by itself, such as a repository that calls `withContext(Dispatchers.IO)`.
A read right after `dispatch` then comes too early.

`awaitIdle()` waits until every event that was dispatched has been handled to its end, wherever its work
ran:

```kotlin
@Test
fun `opening loads the tasks from disk`() = runEventHandlerTest(
    handler = OnTasksLoadHandler(DiskTaskRepository()),
    initialState = TaskListUiState(),
) {
    dispatch(OnTasksLoad)
    awaitIdle()

    state.tasks shouldBe listOf(task(id = 1L))
}
```

A failure on that other thread is waited for too, and shows in `failures`.

A test stays faster and simpler when the repository takes its dispatcher as a parameter and the test
passes its own, as in [choosing the coroutine scope](choosing-the-coroutine-scope.md#slow-work-belongs-in-the-repository).
Use `awaitIdle()` for code you cannot change that way.

## Setting a time limit

A test may take 10 seconds. That is real time, not the test's clock: a test that moves its clock
forward by an hour still finishes at once. Change the limit with `timeout`:

```kotlin
@Test
fun `opening loads a large file`() = runEventHandlerTest(
    handler = OnTasksLoadHandler(DiskTaskRepository()),
    initialState = TaskListUiState(),
    timeout = 30.seconds,
) {
    dispatch(OnTasksLoad)
    awaitIdle()

    state.tasks.size shouldBe 10_000
}
```

A test that runs out of time fails with a message that names the events still being handled.

## Using other testing tools

`router` is the router the event handlers run in, and `testScope` is the scope of the coroutine test.
With those two, any tool works inside the block. This test reads the effects with
[Turbine](https://github.com/cashapp/turbine):

```kotlin
@Test
fun `a tap on a task opens the editor`() = runEventHandlerTest(
    handler = OnTaskClickHandler(),
    initialState = TaskListUiState(),
) {
    router.effects.test {
        dispatch(OnTaskClick(taskId = 1L))

        awaitItem() shouldBe TaskListEffect.OpenEditor(taskId = 1L)
    }
}
```

Reading `effects` takes nothing away from another tool: Turbine still receives every effect, and so
does a collector you write yourself.

## Testing a dispatch strategy

A strategy is a function, so a test can also call it directly, without a router. Give it the new event
and the events that are active, and look at the answer. This test is for an event handler whose
strategy is `DispatchStrategy.Latest<OnTaskDoneChange> { it.taskId }`:

```kotlin
@Test
fun `a new change replaces the one still being saved for that task and no other`() {
    val active = listOf(
        OnTaskDoneChange(taskId = 1L, done = true),
        OnTaskDoneChange(taskId = 2L, done = true),
    )

    val dispatch = handler.strategy.decide(OnTaskDoneChange(taskId = 2L, done = false), active)

    dispatch shouldBe Dispatch.Replace(listOf(OnTaskDoneChange(taskId = 2L, done = true)))
}
```

The ready-made strategies need no test of their own. Test a strategy when it has a key or when you
wrote the function yourself. See [choosing a dispatch strategy](choosing-a-dispatch-strategy.md).

## Testing a view model

A view model built on Eventus is an ordinary view model, and you test it the standard way. Eventus
adds nothing here and needs nothing: `state`, `effects` and `onEvent` are plain members.

The standard way is the one in Android's guide to
[testing coroutines](https://developer.android.com/kotlin/coroutines/test): replace the main
dispatcher with a test dispatcher, create the view model, call it, and check its state. A view model
whose scope is [`routerScope()`](choosing-the-coroutine-scope.md#the-routerscope-function) runs its
event handlers on the main dispatcher, so they run on the test dispatcher.

```kotlin
class TaskListViewModelTest {

    private val tasks = FakeTaskRepository(initial = listOf(task(id = 1L)))

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `a tap on a task opens the editor`() = runTest {
        val viewModel = TaskListViewModel(
            handlers = listOf(OnScreenOpenedHandler(tasks), OnTaskClickHandler()),
            errorHandler = TaskListErrorHandler(),
        )

        viewModel.onEvent(OnTaskClick(taskId = 1L))

        viewModel.effects.first() shouldBe TaskListEffect.OpenEditor(taskId = 1L)
    }
}
```

`setMain`, `resetMain` and `UnconfinedTestDispatcher` belong to `kotlinx-coroutines-test` and ask for
an opt-in to `ExperimentalCoroutinesApi`. On Android with JUnit 4, the same guide puts the two calls
in a reusable rule, `MainDispatcherRule`.

A view model that takes its scope as a parameter can be given the test's `backgroundScope` instead,
with no change to the main dispatcher: `TaskListViewModel(handlers, scope = backgroundScope)`. Use
`backgroundScope` and not the test's own scope: the router keeps its scope busy until it is cancelled,
and `runTest` waits for its own scope to finish.

Keep these tests for what the view model itself does: its own logic and how it is wired. Test what
the event handlers do with `runEventHandlerTest`, which is shorter and also checks their strategies.

---

[Back to the README](../README.md#documentation)
