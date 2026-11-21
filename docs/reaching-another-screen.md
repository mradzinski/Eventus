# Reaching another screen

Sooner or later something outside a screen has to make that screen do something: a settings screen
whose change the home screen must show, or a button in a shared app bar that acts on the list below
it. A router belongs to one screen, and Eventus has no channel that carries an event from one screen
to another. There are two ways to do it, and both leave each screen's router private.

**Go down by default.** It is the one that keeps working as an app grows. Go up only for a control
that belongs to one screen and is merely drawn somewhere shared.

## Down, through what the screens share (the default)

Give the part that acts its own event and handler, and let the handler change something the other
screen already observes:

```kotlin
// The settings screen's own handler. It never mentions the home screen.
class OnUnitsChangeHandler(
    private val settings: SettingsRepository,
) : EventHandler<OnUnitsChange, SettingsUiState, SettingsEffect> {

    override val eventType = OnUnitsChange::class

    override suspend fun handle(event: OnUnitsChange, store: ScreenStore<SettingsUiState, SettingsEffect>) {
        settings.setUnits(event.units)
    }
}
```

The home screen shows the new units because it observes the same repository, not because anyone told
it to. Two screens never talk to each other; they both talk to what lies below them.

Why this is the default:

- **It works whether the other screen exists or not**, and for any number of screens that care.
- **Each part is tested alone**: this handler with a fake repository, the other screen with the same
  fake.
- **Its cost does not grow.** A tenth screen that needs the value observes it, and nothing else
  changes.

Not everything shared is data. "The list is in selection mode" is not something a repository should
know. Put that kind of state in a small holder owned by the app, which the screens observe the same
way. The rule is "owned below the screens", not "owned by a repository".

## Up, through the UI (the exception)

Sending an event needs only the screen's `onEvent`, which is a plain function. So a screen can hand a
control that calls it to whoever draws the shared part:

```kotlin
// A bar shared by every screen. It knows nothing about any of them.
@Composable
fun AppTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) { /* … */ }

// The list puts the bar in its own scaffold and gives it its own button.
Scaffold(
    topBar = {
        AppTopBar(title = "Tasks") {
            IconButton(onClick = { onEvent(TaskListEvent.OnRefresh) }) { /* icon */ }
        }
    },
) { /* … */ }
```

This is right when all of these hold:

- the action belongs to one screen: its state, its progress and its failure message are that screen's;
- that screen is on display whenever the control is;
- no other screen has to reflect the result.

The sample uses both routes. Saving a task in the editor has to show in the list underneath, which is
not on display at that moment, so it goes down: the editor writes to the repository and the list
observes it. Refresh goes up: it is the list's own action, its button is shown only while the list
is, and nothing else depends on it, so the list passes its own button into the shared bar.

It stops being right as soon as one of them fails, and the signs are easy to spot: the control is
needed while its screen is not showing, two screens want to fill the same place, or the same control
is being passed down through layer after layer. Move the action down then.

## Why there is no bus

A channel where any code posts an event and the right router picks it up would be easy to add, and
it would cost what the rest of the design protects:

- **The sender would not know who receives the event, or whether anyone does.** With `onEvent`, an
  event nobody handles is an error at once.
- **The receiver may not exist.** A screen that is not on the back stack has no router. The event
  would be lost, or kept and replayed later at a moment nobody chose.
- **Any screen could drive any other.** The state of a screen would no longer be explained by its
  own events and what it observes.

If neither route fits, the cause is usually that something is owned by a screen and should be owned
by the layer below it.

---

[Back to the README](../README.md#documentation)
