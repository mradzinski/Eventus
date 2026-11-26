package io.github.mradzinski.eventus.test

import io.github.mradzinski.eventus.test.OnQueryChangeHandler.Companion.SEARCH_DELAY_MILLIS
import io.github.mradzinski.eventus.test.OnSaveClickHandler.Companion.SAVE_MILLIS
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class DispatchingAndReadingTest {

    private val repository = FakeRepository()

    @Test
    fun `nothing dispatched leaves the initial state alone and every list empty`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(query = "start"),
    ) {
        states shouldContainExactly listOf(TestState(query = "start"))
        state shouldBe TestState(query = "start")
        effects.shouldBeEmpty()
        reports.shouldBeEmpty()
        failures.shouldBeEmpty()
    }

    @Test
    fun `one event gives the state now and every state on the way`() = runEventHandlerTest(
        handler = OnRefreshHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnRefresh)

        states.map { it.loading } shouldContainExactly listOf(false, true, false)
        state.loading shouldBe false
        repository.refreshes shouldBe 1
    }

    @Test
    fun `effects are read as a list and reading them again gives the same list`() = runEventHandlerTest(
        handler = OnItemClickHandler(),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnItemClick("1"), TestEvent.OnItemClick("2"))

        effects shouldContainExactly listOf(TestEffect.Opened("1"), TestEffect.Opened("2"))
        effects shouldContainExactly listOf(TestEffect.Opened("1"), TestEffect.Opened("2"))
    }

    @Test
    fun `a handler that never returns is followed while the test runs`() = runEventHandlerTest(
        handler = OnScreenOpenedHandler(repository),
        initialState = TestState(items = listOf("old")),
    ) {
        dispatch(TestEvent.OnScreenOpened)
        repository.items.value = listOf("a", "b")

        states.map { it.items } shouldContainExactly listOf(listOf("old"), listOf("a"), listOf("a", "b"))
    }

    @Test
    fun `a handler that waits is checked before and after the wait`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnQueryChange("c"), TestEvent.OnQueryChange("ca"))
        advanceBy((SEARCH_DELAY_MILLIS - 1).milliseconds)

        state.query shouldBe "ca"
        repository.searched.shouldBeEmpty()

        advanceBy(1.milliseconds)

        repository.searched shouldContainExactly listOf("ca")
    }

    @Test
    fun `a run that is replaced shows what its finally block did`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnQueryChange("c"), TestEvent.OnQueryChange("ca"))

        states.map { it.loading } shouldContainExactly listOf(false, true, false, true)
    }

    @Test
    fun `a run still waiting when the test ends is stopped quietly`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnQueryChange("c"))

        state.query shouldBe "c"
    }

    @Test
    fun `the reports say what the router did with each event`() = runEventHandlerTest(
        handler = OnQueryChangeHandler(repository),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnQueryChange("c"), TestEvent.OnQueryChange("ca"))

        reports.map { it.toString() } shouldContainExactly listOf(
            "#1 OnQueryChange(query=c) runs",
            "#1 OnQueryChange(query=c) started",
            "#2 OnQueryChange(query=ca) replaces #1",
            "#1 OnQueryChange(query=c) was replaced by #2",
            "#2 OnQueryChange(query=ca) started",
        )
    }

    @Test
    fun `several handlers that share a strategy are tested together`() = runEventHandlerTest(
        handlers = listOf(OnSaveClickHandler(), OnDeleteClickHandler()),
        initialState = TestState(items = listOf("a")),
    ) {
        dispatch(TestEvent.OnSaveClick, TestEvent.OnDeleteClick)

        state.items shouldContainExactly listOf("a")
        reports.map { it.toString() } shouldContain "#2 OnDeleteClick is ignored"

        advanceBy(SAVE_MILLIS.milliseconds)
        dispatch(TestEvent.OnDeleteClick)

        state.items.shouldBeEmpty()
        effects shouldContainExactly listOf(TestEffect.Saved)
    }

    @Test
    fun `every handler of a screen is driven as the UI drives it`() = runEventHandlerTest(
        handlers = listOf(
            OnRefreshHandler(repository),
            OnScreenOpenedHandler(repository),
            OnQueryChangeHandler(repository),
            OnItemClickHandler(),
            OnSaveClickHandler(),
        ),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnScreenOpened, TestEvent.OnItemClick("1"), TestEvent.OnRefresh)

        state.items shouldContainExactly listOf("a")
        effects shouldContainExactly listOf(TestEffect.Opened("1"))
        repository.refreshes shouldBe 1
    }

    @Test
    fun `an optimistic update that fails shows both states and the failure`() = runEventHandlerTest(
        handler = OptimisticSaveHandler(),
        initialState = TestState(),
    ) {
        dispatch(TestEvent.OnSaveClick)

        states.map { it.query } shouldContainExactly listOf("", "saved", "")
        failures.single().message shouldBe "the server said no"
    }
}
