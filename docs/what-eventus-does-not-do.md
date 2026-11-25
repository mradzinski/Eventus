# What Eventus does not do

- **It does not bring anything back after the app's process has died.** When the system stops the
  app to reclaim memory, or the app crashes, the router is gone with its state, its running events
  and its waiting effects. See the [lifecycle](key-concepts.md#lifecycle) for when that happens.
  What comes back is your app's decision. If you pass a restored value as `initialState`, save and
  restore the state related to your UI. Do not restore events. State related to effects can be
  restored if you want to: for example, an effect that notifies the user that the app has been
  restored. Decide also whether the screen keeps the restored state when it opens or loads fresh
  data over it.
- **The library alone does not check at compile time that every event has a handler.** The optional
  processor does; see [checking and wiring event handlers at build
  time](checking-and-wiring-event-handlers-at-build-time.md).
- **It does not follow the UI's lifecycle.** Nothing starts or stops because a screen is shown or
  hidden; see [keeping work running](keeping-work-running.md).
- **It does not navigate or inject dependencies.** It gives you the pieces those are built from.
- **The library alone has nothing for a UI toolkit.** It depends on neither Compose nor the
  lifecycle library. The optional [Compose helpers](using-the-compose-helpers.md) are a separate
  artifact.
- **It is not an event bus, by design.** A router belongs to one screen, and events do not travel
  between screens. See [why there is no bus](reaching-another-screen.md#why-there-is-no-bus) for the
  reasons, and [reaching another screen](reaching-another-screen.md) for what to do instead.

---

[Back to the README](../README.md#documentation)
