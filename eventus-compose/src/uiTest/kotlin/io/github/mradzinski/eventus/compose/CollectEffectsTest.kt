package io.github.mradzinski.eventus.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred

class CollectEffectsTest : UiTest() {

    private val router = TestRouter()
    private val performed = mutableListOf<String>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect emitted while the screen is started is performed`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)

        setContent {
            Screen(owner) { router.collectEffects { performed += it.text } }
        }
        runOnIdle { router.store.emit(Shown("a")) }
        waitForIdle()

        performed shouldContainExactly listOf("a")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect emitted before anything collects is performed once the screen is shown`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)

        router.store.emit(Shown("early"))
        setContent {
            Screen(owner) { router.collectEffects { performed += it.text } }
        }
        waitForIdle()

        performed shouldContainExactly listOf("early")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect emitted while the screen is stopped waits until it starts`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED)

        setContent {
            Screen(owner) { router.collectEffects { performed += it.text } }
        }
        runOnIdle { router.store.emit(Shown("a")) }
        waitForIdle()

        performed.shouldBeEmpty()

        runOnIdle { owner.state = Lifecycle.State.STARTED }
        waitForIdle()

        performed shouldContainExactly listOf("a")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `effects stop being performed when the screen stops and resume when it starts again`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)

        setContent {
            Screen(owner) { router.collectEffects { performed += it.text } }
        }
        runOnIdle { owner.state = Lifecycle.State.CREATED }
        runOnIdle { router.store.emit(Shown("a")) }
        waitForIdle()

        performed.shouldBeEmpty()

        runOnIdle { owner.state = Lifecycle.State.STARTED }
        waitForIdle()

        performed shouldContainExactly listOf("a")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect that suspends does not hold up the next one`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        val gate = CompletableDeferred<Unit>()

        setContent {
            Screen(owner) {
                router.collectEffects { effect ->
                    if (effect.text == "slow") gate.await()

                    performed += effect.text
                }
            }
        }
        runOnIdle {
            router.store.emit(Shown("slow"))
            router.store.emit(Shown("quick"))
        }
        waitForIdle()

        performed shouldContainExactly listOf("quick")

        runOnIdle { gate.complete(Unit) }
        waitForIdle()

        performed shouldContainExactly listOf("quick", "slow")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the lambda of the latest recomposition is the one called`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        var prefix by mutableStateOf("first")

        setContent {
            // A new lambda for each prefix, as a screen gets when one of its callbacks changes.
            val current = prefix
            val onEffect: suspend (Shown) -> Unit = { performed += "$current ${it.text}" }

            Screen(owner) { router.collectEffects(onEffect) }
        }
        runOnIdle { router.store.emit(Shown("a")) }
        runOnIdle { prefix = "second" }
        runOnIdle { router.store.emit(Shown("b")) }
        waitForIdle()

        performed shouldContainExactly listOf("first a", "second b")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect emitted after the screen left the composition is kept for the next collector`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        var screen by mutableStateOf("first")

        setContent {
            val current = screen

            if (current != "none") {
                Screen(owner) { router.collectEffects { performed += "$current ${it.text}" } }
            }
        }
        runOnIdle { screen = "none" }
        runOnIdle { router.store.emit(Shown("a")) }
        waitForIdle()

        performed.shouldBeEmpty()

        runOnIdle { screen = "second" }
        waitForIdle()

        performed shouldContainExactly listOf("second a")
    }

    // Two effects wait in the router, and performing the first stops the screen. The second has already
    // left the router by then, so it must not be dropped: it is performed, once.
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an effect taken just before the screen stops is not lost`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)

        router.store.emit(Shown("stop the screen"))
        router.store.emit(Shown("next"))
        setContent {
            Screen(owner) {
                router.collectEffects { effect ->
                    if (effect.text == "stop the screen") owner.state = Lifecycle.State.CREATED

                    performed += effect.text
                }
            }
        }
        waitForIdle()
        runOnIdle { owner.state = Lifecycle.State.STARTED }
        waitForIdle()

        performed shouldContainExactly listOf("stop the screen", "next")
    }
}
