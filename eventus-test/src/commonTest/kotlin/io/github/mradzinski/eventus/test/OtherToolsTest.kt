package io.github.mradzinski.eventus.test

import app.cash.turbine.test
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch

/** The helper does not choose the tools of a test: these use it beside others. */
class OtherToolsTest {

    private val repository = FakeRepository()

    @Test
    fun `Turbine reads the effects and the test reads the same ones`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(),
    ) {
        router.effects.test {
            dispatch(TestEvent.OnItemClick("1"), TestEvent.OnItemClick("2"))

            awaitItem() shouldBe TestEffect.Opened("1")
            awaitItem() shouldBe TestEffect.Opened("2")
        }

        effects shouldContainExactly listOf(TestEffect.Opened("1"), TestEffect.Opened("2"))
    }

    @Test
    fun `effects the test has read still reach Turbine`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnItemClick("1"))

        effects shouldContainExactly listOf(TestEffect.Opened("1"))

        router.effects.test {
            awaitItem() shouldBe TestEffect.Opened("1")
        }
    }

    @Test
    fun `Turbine reads the state beside the recorded states`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
    ) {
        router.state.test {
            awaitItem().loading shouldBe false

            dispatch(TestEvent.OnRefresh)

            awaitItem().loading shouldBe true
            awaitItem().loading shouldBe false
        }

        states.size shouldBe 3
    }

    @Test
    fun `the assertions of kotlin test work on what the test reads`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnRefresh)

        assertEquals(listOf(false, true, false), states.map { it.loading })
        assertEquals(false, state.loading)
    }

    @Test
    fun `a collector of the test's own runs in the test's scope`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(),
    ) {
        val seen = mutableListOf<TestEffect>()

        testScope.backgroundScope.launch {
            router.effects.collect { seen += it }
        }

        dispatch(TestEvent.OnItemClick("1"))

        seen shouldContainExactly listOf(TestEffect.Opened("1"))
    }
}
