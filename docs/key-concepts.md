# Key concepts

| Type | What it is |
|---|---|
| `UiState` | The state of one screen: everything the UI needs to draw it, as one immutable value. |
| `UiEvent` | Something that happened and must be handled: a tap, a text change, something to report to analytics. |
| `UiEffect` | Something the UI must do once and then forget: show a message, navigate. |
| `EventHandler<E, S, F>` | The logic for one event type `E` of a screen whose state is `S` and whose effects are `F`. |
| `ErrorHandler<E, S, F>` | What the screen does when one of its event handlers fails. One per screen. |
| `ScreenStore<S, F>` | The state and effects of one screen. The router creates it and hands it to every handler, which read it (`current`), change it (`update`, `updateOptimistically`) and send effects through it (`emit`). |
| `EventRouter<E, S, F>` | What the UI sees: `state`, `effects` and `onEvent`. Created by the `EventRouter(...)` function. |
| `DispatchStrategy` | How a handler runs when its event arrives again while it is still running. |
| `routerScope()` | Creates the coroutine scope a view model and its router share. |

Three rules follow from how these fit together:

- **Declare a screen's events as one sealed type.** The router matches each event by its exact
  class, so a handler registered for a parent type does not receive the events of its subtypes.
- **Keep the state immutable.** `store.update { it.copy(...) }` builds a new value. A value that was
  changed in place (an item added to a list the state already holds) is equal to the old one, so it
  is never emitted and the UI never sees the change.
- **The UI never gets the `ScreenStore`.** It reads `state` and `effects` and calls `onEvent`. Only
  handlers, and the error handler, write to the store.

If your app already has types called `UiState`, `UiEvent` or `UiEffect`, import these with an alias
(`import io.github.mradzinski.eventus.UiState as EventusUiState`) or replace yours with them.

---

[Back to the README](../README.md#documentation)
