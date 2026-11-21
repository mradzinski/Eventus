package io.github.mradzinski.eventus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data object TestState : UiState

data class VisibilityChanged(val visible: Boolean, val tag: String = "") : UiEvent

data class Shown(val text: String) : UiEffect

/** A router that records the events it is sent, and whose effects a test emits straight into its store. */
class TestRouter : EventRouter<VisibilityChanged, TestState, Shown> {

    val store = ScreenStore<TestState, Shown>(TestState)
    val events = mutableListOf<VisibilityChanged>()

    override val state: StateFlow<TestState>
        get() = store.state

    override val effects: Flow<Shown>
        get() = store.effects

    override fun onEvent(event: VisibilityChanged) {
        events += event
    }
}

/** A lifecycle a test moves by hand, in place of the one a real screen gets from its platform. */
class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {

    // "Unsafe" only lifts the check that the state is changed on the main thread.
    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle
        get() = registry

    var state: Lifecycle.State
        get() = registry.currentState
        set(value) {
            registry.currentState = value
        }

    init {
        state = initialState
    }
}

/** Shows [content] as a screen whose lifecycle is [owner]. */
@Composable
fun Screen(owner: LifecycleOwner, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}
