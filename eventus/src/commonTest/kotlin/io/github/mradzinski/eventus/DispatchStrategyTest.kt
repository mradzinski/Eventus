package io.github.mradzinski.eventus

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class DispatchStrategyTest {

    private val active = listOf(Plus("a"), Minus("a"), Plus("b"), Run("x"))

    @Test
    fun `concurrent runs the event whatever is active`() {
        DispatchStrategy.Concurrent.decide(Plus("c"), active) shouldBe Dispatch.Run
    }

    @Test
    fun `latest of the event's own type replaces the active events of that type`() {
        replacedBy(DispatchStrategy.Latest<Plus>()) shouldContainExactly listOf(Plus("a"), Plus("b"))
    }

    @Test
    fun `latest of a shared type replaces every active event of that type`() {
        replacedBy(DispatchStrategy.Latest<Step>()) shouldContainExactly listOf(Plus("a"), Minus("a"), Plus("b"))
    }

    @Test
    fun `latest with a key replaces the active events of that type with the same key`() {
        replacedBy(DispatchStrategy.Latest<Step> { it.item }) shouldContainExactly listOf(Plus("a"), Minus("a"))
    }

    @Test
    fun `sequential of the event's own type waits for the active events of that type`() {
        awaitedBy(DispatchStrategy.Sequential<Plus>()) shouldContainExactly listOf(Plus("a"), Plus("b"))
    }

    @Test
    fun `sequential of a shared type waits for every active event of that type`() {
        awaitedBy(DispatchStrategy.Sequential<Step>()) shouldContainExactly listOf(Plus("a"), Minus("a"), Plus("b"))
    }

    @Test
    fun `sequential with a key waits for the active events of that type with the same key`() {
        awaitedBy(DispatchStrategy.Sequential<Step> { it.item }) shouldContainExactly listOf(Plus("a"), Minus("a"))
    }

    @Test
    fun `drop if already running of the event's own type ignores it while one of that type is active`() {
        DispatchStrategy.DropIfAlreadyRunning<Plus>().decide(Plus("c"), active) shouldBe Dispatch.Ignore
        DispatchStrategy.DropIfAlreadyRunning<Tick>().decide(Tick, active) shouldBe Dispatch.Run
    }

    @Test
    fun `drop if already running of a shared type ignores an event while any of that type is active`() {
        val strategy = DispatchStrategy.DropIfAlreadyRunning<Step>()

        strategy.decide(Minus("c"), active) shouldBe Dispatch.Ignore
        strategy.decide(Minus("c"), listOf(Run("x"), Tick)) shouldBe Dispatch.Run
    }

    @Test
    fun `drop if already running with a key ignores an event while one with the same key is active`() {
        val strategy = DispatchStrategy.DropIfAlreadyRunning<Step> { it.item }

        strategy.decide(Minus("b"), active) shouldBe Dispatch.Ignore
        strategy.decide(Minus("c"), active) shouldBe Dispatch.Run
    }

    @Test
    fun `with nothing that matches every ready-made strategy answers run`() {
        val unrelated = listOf(Run("x"), Tick)

        listOf(emptyList(), unrelated).forEach { active ->
            DispatchStrategy.Latest<Plus>().decide(Plus("a"), active) shouldBe Dispatch.Run
            DispatchStrategy.Sequential<Plus>().decide(Plus("a"), active) shouldBe Dispatch.Run
            DispatchStrategy.DropIfAlreadyRunning<Plus>().decide(Plus("a"), active) shouldBe Dispatch.Run
        }
    }

    @Test
    fun `two answers that name the same events are equal`() {
        Dispatch.Replace(listOf(Plus("a"))) shouldBe Dispatch.Replace(listOf(Plus("a")))
        Dispatch.WaitFor(listOf(Plus("a"))) shouldBe Dispatch.WaitFor(listOf(Plus("a")))
        Dispatch.WaitFor(listOf(Plus("a"))).toString() shouldBe "WaitFor(events=[Plus(item=a)])"
    }

    private fun replacedBy(strategy: DispatchStrategy<Plus>): List<UiEvent> = strategy
        .decide(Plus("a"), active)
        .shouldBeInstanceOf<Dispatch.Replace>()
        .events

    private fun awaitedBy(strategy: DispatchStrategy<Plus>): List<UiEvent> = strategy
        .decide(Plus("a"), active)
        .shouldBeInstanceOf<Dispatch.WaitFor>()
        .events
}
