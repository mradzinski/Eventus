# Effect or state?

Ask one question: **if the screen were drawn again from scratch, should this still show?**

- Yes: it is state. A loading indicator, a validation message under a field, a "could not load, retry"
  screen.
- No: it is an effect. A snackbar, a navigation, a haptic tap.

An effect modeled as state shows again after a rotation. A state modeled as an effect disappears
after one.

Effects are delivered in order, once, to one collector. Effects emitted while nothing collects (the
app is in the background, say) are kept and delivered to the next collector. So is an effect that
arrives at the very moment its collector is cancelled. An effect is lost only if the collector is
cancelled while it is handling that effect. Collect them in one place per screen.

---

[Back to the README](../README.md#documentation)
