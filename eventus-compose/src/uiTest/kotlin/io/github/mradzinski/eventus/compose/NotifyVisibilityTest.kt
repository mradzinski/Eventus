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

class NotifyVisibilityTest : UiTest() {

    private val router = TestRouter()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `nothing is sent while the screen has not started`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED)

        setContent {
            Screen(owner) { router.notifyVisibility { VisibilityChanged(it) } }
        }
        waitForIdle()

        router.events.shouldBeEmpty()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `each start sends visible and each stop sends hidden`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.CREATED)

        setContent {
            Screen(owner) { router.notifyVisibility { VisibilityChanged(it) } }
        }
        runOnIdle { owner.state = Lifecycle.State.STARTED }
        runOnIdle { owner.state = Lifecycle.State.CREATED }
        runOnIdle { owner.state = Lifecycle.State.RESUMED }
        waitForIdle()

        router.events shouldContainExactly listOf(
            VisibilityChanged(visible = true),
            VisibilityChanged(visible = false),
            VisibilityChanged(visible = true),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `leaving the composition while visible sends hidden`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.RESUMED)
        var shown by mutableStateOf(true)

        setContent {
            if (shown) {
                Screen(owner) { router.notifyVisibility { VisibilityChanged(it) } }
            }
        }
        runOnIdle { shown = false }
        waitForIdle()

        router.events shouldContainExactly listOf(
            VisibilityChanged(visible = true),
            VisibilityChanged(visible = false),
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the lambda of the latest recomposition builds the event`() = runComposeUiTest {
        val owner = TestLifecycleOwner(Lifecycle.State.STARTED)
        var tag by mutableStateOf("first")

        setContent {
            val current = tag

            Screen(owner) { router.notifyVisibility { VisibilityChanged(visible = it, tag = current) } }
        }
        runOnIdle { tag = "second" }
        runOnIdle { owner.state = Lifecycle.State.CREATED }
        waitForIdle()

        router.events shouldContainExactly listOf(
            VisibilityChanged(visible = true, tag = "first"),
            VisibilityChanged(visible = false, tag = "second"),
        )
    }
}
