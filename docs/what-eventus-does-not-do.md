# What Eventus does not do

- **It does not restore state after the process is stopped.** Pass a restored value as `initialState`
  if you save one.
- **The library alone does not check at compile time that every event has a handler.** The optional
  processor does; see [handlers checked and wired by the build](handlers-checked-and-wired-by-the-build.md).
- **It does not follow the UI's lifecycle.** Nothing starts or stops because a screen is shown or
  hidden; see [work that keeps running](work-that-keeps-running.md).
- **It does not navigate**, inject dependencies or collect flows for the UI. It gives you the pieces
  those are built from.
- **It is not an event bus.** A router belongs to one screen. Events do not travel between screens;
  see [reaching another screen](reaching-another-screen.md) for what to do instead.

---

[Back to the README](../README.md#documentation)
