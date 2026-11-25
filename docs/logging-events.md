# Logging events

The router can tell you what it does with every event: what the event's strategy answered, when its
event handler started, which effects it emitted, and how its run ended.

## Contents

- [Turning the reports on](#turning-the-reports-on)
- [Reading the reports](#reading-the-reports)
- [The reports](#the-reports)
- [Sending the reports to your logger](#sending-the-reports-to-your-logger)
- [What you can rely on](#what-you-can-rely-on)
- [The rules of an observer](#the-rules-of-an-observer)
- [Checking in a test what the router did](#checking-in-a-test-what-the-router-did)
- [What the reports do not cover](#what-the-reports-do-not-cover)

## Turning the reports on

Pass an `observer` when you create the router. Each report prints as a sentence, so one line is a
log:

```kotlin
class EditorViewModel(
    handlers: Collection<EditorHandler<*>>,
    scope: CoroutineScope = routerScope(),
) : ViewModel(scope),
    EventRouter<EditorEvent, EditorUiState, EditorEffect> by EventRouter(
        scope = scope,
        initialState = EditorUiState(),
        handlers = handlers,
        observer = { report -> println(report) },
    )
```

Without an `observer`, the router does none of this work.

## Reading the reports

Every event gets a number when the router decides what to do with it, counting from 1. All the
reports of one event carry its number, so you can tell two equal events apart and pair the start of
a run with its end.

**"I tapped Delete and nothing happened."** Save and Delete share
`DispatchStrategy.DropIfAlreadyRunning<OnChangeClick>()`, and a save was still running:

```text
#1 OnSaveClick runs
#1 OnSaveClick started
#2 OnDeleteClick is ignored
#1 OnSaveClick completed
```

**"My search results never arrived."** The query and the filter share
`DispatchStrategy.Latest<OnCriteriaChange>()`, and the filter changed before the search answered:

```text
#1 OnQueryChange(query=cat) runs
#1 OnQueryChange(query=cat) started
#2 OnFilterChange(filter=New) replaces #1
#1 OnQueryChange(query=cat) was replaced by #2
#2 OnFilterChange(filter=New) started
#2 OnFilterChange(filter=New) completed
```

**"The second tap took a long time."** The plus button uses
`DispatchStrategy.Sequential<OnPlusClick>()`, so the second tap waited for the first:

```text
#1 OnPlusClick runs
#1 OnPlusClick started
#2 OnPlusClick waits for #1
#1 OnPlusClick completed
#2 OnPlusClick started
#2 OnPlusClick completed
```

**"The editor closed, and I do not know what closed it."** An effect is reported with the event it
came from:

```text
#3 OnSaveClick runs
#3 OnSaveClick started
#3 OnSaveClick emitted Close
#3 OnSaveClick completed
```

## The reports

A report is a `RouterTrace`. Every report has the `event` and its `number`.

| Report | It prints as | When |
|---|---|---|
| `Dispatched` | `runs`, `is ignored`, `replaces #1` or `waits for #1` | The strategy answered. It is the first report of every event, and the only one of an event that is ignored. |
| `Started` | `started` | The event handler began. An event that waits starts when the events it waits for have ended. |
| `EffectEmitted` | `emitted ` and the effect | The event handler emitted an effect, or the screen's `ErrorHandler` did while it handled the failure of that event. |
| `Completed` | `completed` | The event handler returned. |
| `Failed` | `failed: ` and the error | The event handler threw. The screen's `ErrorHandler` receives the same failure. |
| `Cancelled` | See the next table | The run was cancelled. It may never have started. |

A `Cancelled` report says why:

| Reason | It prints as | When |
|---|---|---|
| `ReplacedBy(number)` | `was replaced by #2` | A strategy answered `Replace` for another event and named this run. |
| `ScopeCancelled` | `was cancelled with the scope` | The router's scope was cancelled, as when a view model is cleared. |
| `ByHandler` | `was cancelled by its own handler` | The event handler ended by a cancellation of its own, such as a timeout that the handler did not catch. |

## Sending the reports to your logger

The sentence is how a report prints. To use your own logger, pass the report's text to it:

```kotlin
observer = { report -> Log.d("Editor", report.toString()) }
```

A report carries no time, because a logger stamps its own lines.

To write your own text, or to send something other than text, read the fields:

```kotlin
observer = { report ->
    when (report) {
        is RouterTrace.Failed -> crashReporter.record(report.error, event = report.event)
        is RouterTrace.Dispatched -> if (report.dispatch is Dispatch.Ignore) metrics.count("ignored")
        is RouterTrace.Started -> Unit
        is RouterTrace.EffectEmitted -> Unit
        is RouterTrace.Completed -> Unit
        is RouterTrace.Cancelled -> Unit
    }
}
```

## What you can rely on

- **Reports arrive in order, one at a time**, also when events are sent from several threads at
  once. The router never calls your `observer` twice at the same moment.
- **Decisions arrive in the order the router made them:** `#1`, then `#2`, then `#3`.
- **The reports of one event arrive in order:** dispatched, then started, then its effects as it
  emits them, then how the run ended.
- **An effect is reported at the moment it is emitted.** So an effect that the `ErrorHandler` emits
  is reported before the `Failed` report of the same event.
- **The UI gets every effect that is reported.** A report takes nothing away from `effects`.
- **"`#2` replaces `#1`" arrives before "`#1` was replaced by `#2`".**
- **Your `observer` does not always run on the same thread.** It runs where the reported thing
  happened: on the thread that sent the event, inside a run, or on the thread that ended a run.

## The rules of an observer

- **Be quick, and do not wait for anything.** The router calls the `observer` while it handles the
  event. Hand slow work, such as writing to a file, to something that does the work elsewhere.
- **An `observer` that throws is a mistake in your code, and the router treats it as one.** The
  event still runs. The exception is not passed to the screen's `ErrorHandler`. It reaches the
  `CoroutineExceptionHandler` of the router's scope or, without one, the platform's handler for
  uncaught exceptions. Once the scope is cancelled, the exception is dropped.

## Checking in a test what the router did

A report is a value, so a test can compare it. This test checks that a tap on Delete during a save
is ignored:

```kotlin
@Test
fun `delete is ignored while a save is running`() = runTest {
    val reports = mutableListOf<RouterTrace<EditorEvent>>()

    val router = EventRouter<EditorEvent, EditorUiState, EditorEffect>(
        scope = backgroundScope,
        initialState = EditorUiState(),
        handlers = listOf(OnSaveClickHandler(tasks), OnDeleteClickHandler(tasks)),
        observer = { reports += it },
    )

    router.onEvent(OnSaveClick)
    router.onEvent(OnDeleteClick)
    testScheduler.runCurrent()

    val expected = RouterTrace.Dispatched(
        event = OnDeleteClick,
        number = 2,
        dispatch = Dispatch.Ignore,
        replacedOrAwaited = emptyList(),
    )

    assertEquals(expected, reports.first { it.event == OnDeleteClick })
}
```

## What the reports do not cover

- **State changes.** Collect the router's `state` to see them.

---

[Back to the README](../README.md#documentation)
