package io.github.mradzinski.eventus.ksp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class KoinModuleTest {

    @Test
    fun `no module is written when none is requested`() {
        val compiled = compile(listScreen, allListHandlers)

        compiled.koinModule.shouldBeNull()
    }

    @Test
    fun `the module is the actual of the requested property in its package`() {
        val compiled = compile(listScreen, allListHandlers, koinModuleRequest)
        val module = compiled.koinModule.shouldNotBeNull()

        compiled.problems.shouldBeEmpty()
        module shouldContain "package app\n"
        module shouldContain "@io.github.mradzinski.eventus.koin.EventusKoinModule\n" +
            "internal actual val handlersModule: Module = module {"
    }

    @Test
    fun `every handler is a factory bound to the handler interface of its screen in name order`() {
        val compiled = compile(listScreen, allListHandlers, koinModuleRequest)

        compiled.definitions() shouldContainExactly listOf(
            "factory { app.OnSearchHandler(get()) } bind app.ListHandler::class",
            "factory { app.OnStartHandler() } bind app.ListHandler::class",
            "factory { app.RowClickHandler() } bind app.ListHandler::class",
        )
    }

    @Test
    fun `a handler gets one argument for each parameter of its constructor`() {
        val handlers = allListHandlers.replace(
            "(val limit: Int)",
            "(val limit: Int, val name: String, val on: Boolean)",
        )

        val compiled = compile(listScreen, handlers, koinModuleRequest)

        compiled.definitions() shouldContain
            "factory { app.OnSearchHandler(get(), get(), get()) } bind app.ListHandler::class"
    }

    @Test
    fun `a handler with only a secondary constructor gets an argument for each of its parameters`() {
        val handlers = allListHandlers.replace(
            "class OnSearchHandler(val limit: Int) :",
            "class OnSearchHandler :",
        ).replace(
            "override val eventType = ListEvent.OnSearch::class",
            "constructor(limit: Int, name: String)\n        override val eventType = ListEvent.OnSearch::class",
        )

        val compiled = compile(listScreen, handlers, koinModuleRequest)

        compiled.problems.shouldBeEmpty()
        compiled.definitions() shouldContain "factory { app.OnSearchHandler(get(), get()) } bind app.ListHandler::class"
    }

    @Test
    fun `handlers of the same name on two screens are both registered`() {
        val editorScreen = """
            package app.editor

            import app.ListEffect
            import app.ListState
            import app.ListStore
            import io.github.mradzinski.eventus.EventHandler
            import io.github.mradzinski.eventus.UiEvent

            sealed interface EditorEvent : UiEvent {
                data object OnStart : EditorEvent
            }

            interface EditorHandler<E : EditorEvent> : EventHandler<E, ListState, ListEffect>

            class OnStartHandler : EditorHandler<EditorEvent.OnStart> {
                override val eventType = EditorEvent.OnStart::class
                override suspend fun handle(event: EditorEvent.OnStart, store: ListStore) = Unit
            }
        """.trimIndent()

        val compiled = compile(editorScreen, listScreen, allListHandlers, koinModuleRequest)

        compiled.problems.shouldBeEmpty()
        compiled.definitions() shouldContain "factory { app.OnStartHandler() } bind app.ListHandler::class"
        compiled.definitions() shouldContain
            "factory { app.editor.OnStartHandler() } bind app.editor.EditorHandler::class"
    }

    @Test
    fun `an object handler is registered as it is`() {
        val handlers = allListHandlers.replace("class RowClickHandler", "object RowClickHandler")
        val compiled = compile(listScreen, handlers, koinModuleRequest)

        compiled.definitions() shouldContain "factory { app.RowClickHandler } bind app.ListHandler::class"
    }

    @Test
    fun `a handler behind an abstract class is bound to every type between it and EventHandler`() {
        val handlers = """
            abstract class BaseHandler<E : ListEvent> : ListHandler<E>

            class OnStartHandler : BaseHandler<ListEvent.OnStart>() ${handlerBody("ListEvent.OnStart")}

            class OnSearchHandler : ListHandler<ListEvent.OnSearch> ${handlerBody("ListEvent.OnSearch")}

            class RowClickHandler : ListHandler<ListEvent.Row.Click> ${handlerBody("ListEvent.Row.Click")}
        """.trimIndent()

        val compiled = compile(listScreen, handlers, koinModuleRequest)

        compiled.definitions() shouldContain
            "factory { app.OnStartHandler() } bind app.BaseHandler::class bind app.ListHandler::class"
    }

    @Test
    fun `an error handler is registered after the handlers`() {
        val errorHandler = """
            import io.github.mradzinski.eventus.ErrorHandler

            class ListErrorHandler(val tag: String) : ErrorHandler<ListEvent, ListState, ListEffect> {
                override fun handle(event: ListEvent, error: Throwable, store: ListStore) = Unit
            }
        """.trimIndent()

        val compiled = compile(listScreen, allListHandlers, errorHandler, koinModuleRequest)

        compiled.problems.shouldBeEmpty()
        compiled.definitions().last() shouldBe "factory { app.ListErrorHandler(get()) }"
    }

    @Test
    fun `the generated module compiles against Koin in every form it takes`() {
        val handlers = """
            import io.github.mradzinski.eventus.ErrorHandler

            abstract class BaseHandler<E : ListEvent> : ListHandler<E>

            class OnStartHandler(val limit: Int, val name: String) : BaseHandler<ListEvent.OnStart>() ${handlerBody(
            "ListEvent.OnStart",
        )}

            internal class OnSearchHandler : ListHandler<ListEvent.OnSearch> ${handlerBody("ListEvent.OnSearch")}

            object RowClickHandler : ListHandler<ListEvent.Row.Click> ${handlerBody("ListEvent.Row.Click")}

            class ListErrorHandler(val tag: String) : ErrorHandler<ListEvent, ListState, ListEffect> {
                override fun handle(event: ListEvent, error: Throwable, store: ListStore) = Unit
            }
        """.trimIndent()

        val compiled = compileWithGeneratedKoinModule(listScreen, handlers)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a public property gets a public module`() {
        val request = koinModuleRequest.replace("internal expect", "expect")
        val compiled = compile(listScreen, allListHandlers, request)

        compiled.koinModule.shouldNotBeNull() shouldContain "public actual val handlersModule: Module"
    }

    @Test
    fun `a property that is not expect fails the build and writes nothing`() {
        val request = """
            @io.github.mradzinski.eventus.koin.EventusKoinModule
            internal val handlersModule: org.koin.core.module.Module = org.koin.dsl.module { }
        """.trimIndent()

        val compiled = compile(listScreen, allListHandlers, request)

        compiled.koinModule.shouldBeNull()
        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldStartWith "handlersModule must be an 'expect' property"
    }

    @Test
    fun `a property of another type fails the build and writes nothing`() {
        val request = koinModuleRequest.replace("org.koin.core.module.Module", "String")
        val compiled = compile(listScreen, allListHandlers, request)

        compiled.koinModule.shouldBeNull()
        compiled.problems shouldContainExactly listOf(
            "handlersModule must have the type org.koin.core.module.Module.",
        )
    }

    @Test
    fun `a property with type arguments that is not a module fails the build`() {
        val request = koinModuleRequest.replace("org.koin.core.module.Module", "List<*>")
        val compiled = compile(listScreen, allListHandlers, request)

        compiled.koinModule.shouldBeNull()
        compiled.problems shouldContainExactly listOf(
            "handlersModule must have the type org.koin.core.module.Module.",
        )
    }

    @Test
    fun `a property whose type does not resolve fails the build`() {
        val request = koinModuleRequest.replace("org.koin.core.module.Module", "Missing")
        val compiled = compile(listScreen, allListHandlers, request)

        compiled.koinModule.shouldBeNull()
        compiled.problems shouldContain "handlersModule must have the type org.koin.core.module.Module."
    }

    @Test
    fun `a second requested property fails the build`() {
        val second = koinModuleRequest.replace("handlersModule", "moreHandlersModule")
        val compiled = compile(listScreen, allListHandlers, koinModuleRequest, second)

        compiled.problems shouldHaveSize 1
        compiled.problems.single() shouldContain "is a second @EventusKoinModule property"
    }

    @Test
    fun `a private handler fails the build when a module is requested`() {
        val handlers = allListHandlers.replace("class OnStartHandler", "private class OnStartHandler")

        val requested = compile(listScreen, handlers, koinModuleRequest)
        val notRequested = compile(listScreen, handlers)

        requested.problems shouldHaveSize 1
        requested.problems.single() shouldStartWith "app.OnStartHandler is private"
        notRequested.problems.shouldBeEmpty()
    }

    @Test
    fun `a handler with no screen interface fails the build when a module is requested`() {
        val handlers = allListHandlers.replace(
            "class OnStartHandler : ListHandler<ListEvent.OnStart>",
            "class OnStartHandler : $EVENT_HANDLER<ListEvent.OnStart, ListState, ListEffect>",
        )

        val requested = compile(listScreen, handlers, koinModuleRequest)
        val notRequested = compile(listScreen, handlers)

        requested.problems shouldHaveSize 1
        requested.problems.single() shouldStartWith "app.OnStartHandler implements EventHandler directly"
        notRequested.problems.shouldBeEmpty()
    }

    @Test
    fun `a handler the router can never call is left out of the module`() {
        val handler = listHandler(name = "RowHandler", event = "ListEvent.Row")
        val compiled = compile(listScreen, allListHandlers, handler, koinModuleRequest)

        compiled.definitions() shouldHaveSize 3
    }

    /** The lines between the braces of the generated module. */
    private fun Compiled.definitions(): List<String> = koinModule
        .shouldNotBeNull()
        .substringAfter("module {\n")
        .substringBeforeLast("}")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
