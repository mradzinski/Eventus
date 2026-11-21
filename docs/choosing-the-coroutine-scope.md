# Choosing the coroutine scope

The router runs every event handler in a child of the scope you pass, and stops when that scope is
cancelled. Whoever creates the scope must cancel it.

- **In a view model**, pass the same scope to both: `ViewModel(scope)` cancels it when the view model
  is cleared. On Android the view model, and so the router and its state, survives configuration
  changes.
- **`routerScope()`** gives a scope that runs handlers on the main thread, at once when the event is
  sent from the main thread, which is what keeps a text field in step with its state. On a platform
  without a main dispatcher, handlers run on the thread that sends the event. Desktop apps need
  `kotlinx-coroutines-swing` for the main dispatcher.
- **Outside a view model** (a SwiftUI screen holding a shared presenter, a desktop window), you
  own the scope: cancel it when the screen goes away, or its `Sequential` workers and every handler that
  never returns live on.
- **Do the slow work elsewhere.** Repositories should be safe to call from the main thread and move
  their own work to another dispatcher. Handlers then stay simple.

`onEvent` is safe to call from any thread.

---

[Back to the README](../README.md#documentation)
