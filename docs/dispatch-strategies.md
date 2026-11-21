# Dispatch strategies

A handler chooses its strategy by overriding `strategy`. Each strategy applies per event type: two
different events never wait for, cancel or drop each other.

| Strategy | When the event arrives again during a run | Use it for |
|---|---|---|
| `Concurrent` (default) | Both runs go on at once. | Anything quick, and anything that may overlap. |
| `Latest` | The active run is cancelled and a new one starts. | Search boxes, sliders: input that makes the previous work pointless. See [debouncing](debouncing.md). |
| `Sequential` | The event waits its turn. Events run in the order they were sent. | Toggles and counters, where order matters. |
| `DropIfBusy` | The event is ignored. | Submit, refresh, confirm: actions that must not run twice. |

## What the router keeps, and for how long

The router lives exactly as long as the coroutine scope you give it, and it has nothing to close.
While it lives, this is everything it holds on to:

| Strategy | What is kept | Until |
|---|---|---|
| `Concurrent` | Nothing. A run is forgotten as soon as it starts. | — |
| `Latest`, `DropIfBusy` | The one active run of each event type. | That run ends. |
| `Sequential` | One queue and one idle worker coroutine per event type, created when that event is first sent. | The scope ends. They are **not** released when the queue empties. |

So a screen that uses `Sequential` for three event types holds three suspended coroutines until its
view model is cleared. That costs little, but it is the one thing the router keeps past the work it
was asked to do. A `Sequential` run that fails does not stop its queue: the next event still runs.

---

[Back to the README](../README.md#documentation)
