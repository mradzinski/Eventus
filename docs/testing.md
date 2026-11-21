# Testing

## Testing an event handler

An event handler test needs no router, no view model and no dependency injection. Create a store,
call `handle`, and look at the store.

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
never returns by itself, launch it in `backgroundScope` and let `runTest` cancel it.

## Testing a view model

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

## Checking that every event has a handler

An event that has no registered handler throws `IllegalStateException` at runtime if sent, which may
be three taps into a screen. With [`eventus-ksp`](checking-and-wiring-event-handlers-at-build-time.md) the build
finds a handler that was never written, and its generated Koin module registers every handler there is,
so this test is not needed.

Without `eventus-ksp`, find the missing handler with one test per screen. The test compares the
events of the sealed type with the handlers your container really provides, so it catches a handler
that was never written as well as one that was never registered:

```kotlin
// JVM only: listing the subclasses of a sealed type needs kotlin-reflect.
@Test
fun `every task list event has a registered handler`() {
    val koin = koinApplication { modules(appModules) }.koin
    val handled = koin.getAll<TaskListHandler<*>>().map { it.eventType }.toSet()

    val events = TaskListEvent::class.sealedSubclasses.toSet()

    assertEquals(events, handled)
}
```

Two handlers for the same event need no test: the router refuses to be created.

---

[Back to the README](../README.md#documentation)
