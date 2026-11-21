package io.github.mradzinski.eventus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.UiEvent

/**
 * Tells this router when the screen becomes visible and when it stops being visible, with an event of
 * your own.
 *
 * ```
 * viewModel.notifyVisibility { visible -> ChatEvent.OnVisibilityChange(visible) }
 * ```
 *
 * The router knows nothing about a user interface, so nothing in it stops because a screen is hidden.
 * This is for the few screens with work too costly to keep running then, such as an open socket: the
 * handler of the event starts that work when the screen is visible and returns at once when it is not.
 *
 * Use one event type that carries the visibility, and handle it with `DispatchStrategy.Latest`. A new
 * event of a type cancels the run before it only within that type, so two separate events, one for
 * visible and one for hidden, would not stop each other's work.
 *
 * [event] is called with `true` when the lifecycle starts and with `false` when it stops or when this
 * leaves the composition.
 *
 * @param event Builds the event to send for the new visibility.
 */
@Composable
public fun <E : UiEvent> EventRouter<E, *, *>.notifyVisibility(event: (visible: Boolean) -> E) {
    val currentEvent by rememberUpdatedState(event)

    LifecycleStartEffect(this) {
        onEvent(currentEvent(true))

        onStopOrDispose { onEvent(currentEvent(false)) }
    }
}
