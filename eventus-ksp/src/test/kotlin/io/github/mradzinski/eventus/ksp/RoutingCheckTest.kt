package io.github.mradzinski.eventus.ksp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class RoutingCheckTest {

    @Test
    fun `a screen whose every event has a handler compiles`() {
        val compiled = compile(listScreen, allListHandlers)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `an event without a handler fails the build and is named`() {
        val handlers = listOf(
            listHandler(name = "OnStartHandler", event = "ListEvent.OnStart"),
            listHandler(name = "RowClickHandler", event = "ListEvent.Row.Click"),
        )
        val compiled = compile(listScreen, *handlers.toTypedArray())

        compiled.succeeded shouldBe false
        compiled.problems shouldContainExactly listOf(
            "No handler for app.ListEvent.OnSearch. Add a class that implements EventHandler for it.",
        )
    }

    @Test
    fun `an event of a nested sealed type without a handler fails the build`() {
        val handlers = listOf(
            listHandler(name = "OnStartHandler", event = "ListEvent.OnStart"),
            listHandler(name = "OnSearchHandler", event = "ListEvent.OnSearch"),
        )
        val compiled = compile(listScreen, *handlers.toTypedArray())

        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldStartWith "No handler for app.ListEvent.Row.Click."
    }

    @Test
    fun `a screen with no handler at all fails for each of its events`() {
        val compiled = compile(listScreen)

        compiled.problems shouldHaveSize 3
    }

    @Test
    fun `a screen stopped event needs no handler but may have one`() {
        val onStopHandler = listHandler(name = "OnStopHandler", event = "ListEvent.OnStop")
        val compiled = compile(listScreen, allListHandlers, onStopHandler)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `two handlers of one event fail the build`() {
        val second = listHandler(name = "AnotherOnStartHandler", event = "ListEvent.OnStart")
        val compiled = compile(listScreen, allListHandlers, second)

        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldStartWith "app.ListEvent.OnStart has two handlers"
        compiled.problems.single() shouldContain "app.OnStartHandler"
        compiled.problems.single() shouldContain "app.AnotherOnStartHandler"
    }

    @Test
    fun `a handler of a sealed event type fails the build`() {
        val handler = listHandler(name = "RowHandler", event = "ListEvent.Row")
        val compiled = compile(listScreen, allListHandlers, handler)

        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldStartWith "app.RowHandler is never called: app.ListEvent.Row is sealed"
    }

    @Test
    fun `a handler whose event is a type parameter fails the build`() {
        val handler = """
            class AnyHandler<E : ListEvent>(override val eventType: kotlin.reflect.KClass<E>) : ListHandler<E> {
                override suspend fun handle(event: E, store: ListStore) = Unit
            }
        """.trimIndent()
        val compiled = compile(listScreen, allListHandlers, handler)

        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldStartWith "app.AnyHandler is never called: its event type is not one known"
    }

    @Test
    fun `an abstract handler is not a handler but the class that extends it is`() {
        val handlers = """
            abstract class BaseHandler<E : ListEvent> : ListHandler<E>

            class OnStartHandler : BaseHandler<ListEvent.OnStart>() ${handlerBody("ListEvent.OnStart")}

            class OnSearchHandler : BaseHandler<ListEvent.OnSearch>() ${handlerBody("ListEvent.OnSearch")}

            class RowClickHandler : BaseHandler<ListEvent.Row.Click>() ${handlerBody("ListEvent.Row.Click")}
        """.trimIndent()
        val compiled = compile(listScreen, handlers)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a handler written through type aliases counts`() {
        val handlers = """
            typealias Handles<E> = ListHandler<E>
            typealias Search = ListEvent.OnSearch

            class OnStartHandler : Handles<ListEvent.OnStart> ${handlerBody("ListEvent.OnStart")}

            class OnSearchHandler : ListHandler<Search> ${handlerBody("ListEvent.OnSearch")}

            class RowClickHandler : Handles<ListEvent.Row.Click> ${handlerBody("ListEvent.Row.Click")}
        """.trimIndent()
        val compiled = compile(listScreen, handlers)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a handler nested in another class counts`() {
        val handlers = """
            object ListHandlers {
                ${listHandler(name = "OnStartHandler", event = "ListEvent.OnStart")}

                ${listHandler(name = "OnSearchHandler", event = "ListEvent.OnSearch")}

                ${listHandler(name = "RowClickHandler", event = "ListEvent.Row.Click")}
            }
        """.trimIndent()
        val compiled = compile(listScreen, handlers)

        compiled.problems.shouldBeEmpty()
    }

    @Test
    fun `a sealed type that is not an event is left alone`() {
        val other = """
            sealed interface Shape {
                data object Circle : Shape
                data object Square : Shape
            }
        """.trimIndent()
        val compiled = compile(listScreen, allListHandlers, other)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `an event that is not part of a sealed type may have a handler and is not required to`() {
        val loose = """
            import io.github.mradzinski.eventus.EventHandler
            import io.github.mradzinski.eventus.UiEvent

            class Ping : UiEvent
            class Pong : UiEvent

            class PingHandler : EventHandler<Ping, ListState, ListEffect> {
                override val eventType = Ping::class
                override suspend fun handle(event: Ping, store: ListStore) = Unit
            }
        """.trimIndent()
        val compiled = compile(listScreen, allListHandlers, loose)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }
}
