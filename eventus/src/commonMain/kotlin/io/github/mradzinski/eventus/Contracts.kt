package io.github.mradzinski.eventus

/**
 * The state of one screen: everything the UI needs to draw it, as one immutable value.
 *
 * Declare it per screen, usually as a sealed type with a shape for each thing the screen can show
 * (loading, content, failure), so that combinations that make no sense cannot be built.
 */
public interface UiState

/**
 * Something that happened and must be handled: a tap, a text change, something to report to analytics.
 *
 * Declare a screen's events as one sealed type that extends this interface. Each concrete event is
 * handled by exactly one [EventHandler]. Not every handler changes the state: Eventus routes events and
 * knows nothing about a user interface.
 */
public interface UiEvent

/**
 * Something the UI must do once and then forget: show a message, navigate, vibrate.
 *
 * An effect is not state. If the screen were drawn again from scratch and should still show it, it
 * belongs in the [UiState] instead.
 */
public interface UiEffect
