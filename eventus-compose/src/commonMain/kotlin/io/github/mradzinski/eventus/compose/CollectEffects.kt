package io.github.mradzinski.eventus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.UiEffect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Performs the effects of this router for as long as the screen is started.
 *
 * ```
 * viewModel.collectEffects { effect ->
 *     when (effect) {
 *         is NoteEffect.ShowSaved -> snackbarHostState.showSnackbar("Saved")
 *         is NoteEffect.Close -> onClose()
 *     }
 * }
 * ```
 *
 * An effect is something only the UI can do, so it is performed only while there is a UI to do it:
 *
 * - **Effects are collected while the lifecycle is at least started.** An effect emitted while the screen
 *   is stopped, with the app in the background for example, waits in the router and is performed when the
 *   screen starts again.
 * - **Each effect runs in a coroutine of its own.** One that suspends, such as showing a snackbar, does
 *   not hold up the next one.
 * - **[onEffect] may change between recompositions.** The latest one is always the one called.
 *
 * An effect that is being performed when the screen stops is cancelled and is not performed again, and
 * one that had already left the router at that moment is still performed, so that none is lost. Call this
 * once per router: a router gives each effect to one collector, so two collectors would each see only
 * some of them.
 *
 * @param onEffect Performs one effect. It runs on the main thread.
 */
@Composable
public fun <F : UiEffect> EventRouter<*, *, F>.collectEffects(onEffect: suspend (effect: F) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)

    LaunchedEffect(this, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                // Undispatched: the effect is no longer in the router once it is here, so performing it
                // must begin before anything can cancel this collector, or the effect would be lost.
                launch(start = CoroutineStart.UNDISPATCHED) { currentOnEffect(effect) }
            }
        }
    }
}
