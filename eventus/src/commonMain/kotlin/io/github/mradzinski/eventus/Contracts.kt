package io.github.mradzinski.eventus

/**
 * The state of one screen: everything the UI needs to draw it, as one immutable value.
 *
 * Declare it per screen, usually as a sealed type with a shape for each thing the screen can show
 * (loading, content, failure), so that combinations that make no sense cannot be built.
 */
public interface UiState

/**
 * Something the UI tells its screen: a tap, a text change, the screen becoming visible.
 *
 * Declare a screen's events as one sealed type that extends this interface. Each concrete event is
 * handled by exactly one [EventHandler].
 */
public interface UiEvent

/**
 * Something the UI must do once and then forget: show a message, navigate, vibrate.
 *
 * An effect is not state. If the screen were drawn again from scratch and should still show it, it
 * belongs in the [UiState] instead.
 */
public interface UiEffect

/**
 * An event that tells the router the screen is no longer visible.
 *
 * Sending an event that implements this interface cancels every handler of the screen that runs with
 * [DispatchStrategy.WhileStarted]. The event may have a handler of its own, which then runs as usual,
 * but it does not need one.
 */
public interface ScreenStoppedEvent : UiEvent
