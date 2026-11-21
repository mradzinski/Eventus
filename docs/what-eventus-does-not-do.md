# What Eventus does not do

- **It does not restore state after the process is stopped.** Pass a restored value as
  `initialState` if you save one.
- **The library alone does not check at compile time that every event has a handler.** The optional
  processor does; see [checking and wiring event handlers at build
  time](checking-and-wiring-event-handlers-at-build-time.md).
- **It does not follow the UI's lifecycle.** Nothing starts or stops because a screen is shown or
  hidden; see [keeping work running](keeping-work-running.md).
- **It does not navigate or inject dependencies.** It gives you the pieces those are built from.
- **The library alone has nothing for a UI toolkit.** It depends on neither Compose nor the
  lifecycle library. The optional [Compose helpers](using-the-compose-helpers.md) are a separate
  artifact.
- **It is not an event bus.** A router belongs to one screen. Events do not travel between screens;
  see [reaching another screen](reaching-another-screen.md) for what to do instead.

---

[Back to the README](../README.md#documentation)
