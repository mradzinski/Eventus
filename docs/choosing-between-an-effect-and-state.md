# Choosing between an effect and state

Ask one question: **if the screen were drawn again from scratch, should this still show?**

- Yes: it is state. A loading indicator, a validation message under a field, a "could not load, retry"
  screen.
- No: it is an effect. A snackbar, a navigation, a haptic tap.

An effect modeled as state shows again after a rotation. A state modeled as an effect disappears
after one.

A warning under a field must still be there when the screen is drawn again, so it is state:

> [!TIP]
> **Do:** put the warning in the state.
>
> ```kotlin
> store.update { it.copy(titleMissing = true) }
> ```

> [!CAUTION]
> **Don't:** emit the warning as an effect. It shows once and is gone after a rotation, while the
> field is still empty.
>
> ```kotlin
> store.emit(TaskEditorEffect.ShowTitleMissing)
> ```

The router delivers effects in order, once, to one collector. The router keeps an effect emitted
while nothing collects (the app is in the background, say) and delivers that effect to the next
collector. The same goes for an effect that arrives at the very moment its collector is cancelled.
An effect is lost only if the collector is cancelled while performing that effect. Collect effects
in one place per screen. See [emitting an effect](emitting-an-effect.md).

---

[Back to the README](../README.md#documentation)
