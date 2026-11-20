package io.github.mradzinski.eventus.sample.presentation.app

import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStoppedEvent
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState

/**
 * The part of the app that stays on screen whatever destination is shown: the bar with the "Work
 * offline" switch. It is a screen like any other as far as Eventus goes, with its own router.
 */
internal sealed interface AppEvent : UiEvent {

    data object OnAppStarted : AppEvent

    data object OnAppStopped : AppEvent, ScreenStoppedEvent

    data class OnOfflineChange(val offline: Boolean) : AppEvent
}

internal data class AppUiState(val offline: Boolean = false) : UiState

/** The bar has nothing to do once and forget, so it has no effects. A router still needs the type. */
internal sealed interface AppEffect : UiEffect

internal interface AppHandler<E : AppEvent> : EventHandler<E, AppUiState, AppEffect>

internal typealias AppStore = ScreenStore<AppUiState, AppEffect>
