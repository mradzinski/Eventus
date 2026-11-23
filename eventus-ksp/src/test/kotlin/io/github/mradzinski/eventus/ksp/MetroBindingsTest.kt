package io.github.mradzinski.eventus.ksp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test

class MetroBindingsTest {

    @Test
    fun `no container is written when no interface asks for one`() {
        val compiled = compile(listScreen, allListHandlers)

        compiled.metroBindings.shouldBeEmpty()
    }

    @Test
    fun `the container is an object contributed to the scope and compiles against Metro`() {
        val compiled = compile(metroListScreen, allListHandlers)
        val container = compiled.metroBindings.single()

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
        container shouldContain "package app\n"
        container shouldContain "@ContributesTo(dev.zacsweers.metro.AppScope::class)\n" +
            "@BindingContainer\n" +
            "@Origin(app.ListHandler::class)\n" +
            "public object EventusListHandlerBindings {"
    }

    @Test
    fun `every handler is provided into the set of its screen in name order`() {
        val compiled = compile(metroListScreen, allListHandlers)

        compiled.providers() shouldContainExactly listOf(
            "@Provides @IntoSet public fun onSearchHandler( p0: kotlin.Int, ): app.ListHandler<*> = " +
                "app.OnSearchHandler(p0)",
            "@Provides @IntoSet public fun onStartHandler(): app.ListHandler<*> = app.OnStartHandler()",
            "@Provides @IntoSet public fun rowClickHandler(): app.ListHandler<*> = app.RowClickHandler()",
        )
    }

    @Test
    fun `a provider takes each parameter of the constructor with its type`() {
        val handlers = allListHandlers.replace(
            "(val limit: Int)",
            "(val limit: Int, val name: String?, val names: List<String>, val all: Map<String, *>?)",
        )

        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.providers().first() shouldContain "p0: kotlin.Int, p1: kotlin.String?, " +
            "p2: kotlin.collections.List<kotlin.String>, p3: kotlin.collections.Map<kotlin.String, *>?, ): " +
            "app.ListHandler<*> = app.OnSearchHandler(p0, p1, p2, p3)"
    }

    @Test
    fun `a type argument keeps the variance it was written with`() {
        val handlers = allListHandlers.replace(
            "(val limit: Int)",
            "(val sink: Comparable<in String>, val source: Array<out Number>)",
        )

        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.providers().first() shouldContain
            "p0: kotlin.Comparable<in kotlin.String>, p1: kotlin.Array<out kotlin.Number>, )"
    }

    @Test
    fun `a suspending function parameter is written as a function type`() {
        val handlers = allListHandlers.replace(
            "(val limit: Int)",
            "(val load: suspend (Int, String) -> Boolean, val reload: (suspend () -> Unit)?, val now: () -> Long)",
        )

        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.providers().first() shouldContain
            "p0: suspend (kotlin.Int, kotlin.String) -> kotlin.Boolean, p1: (suspend () -> kotlin.Unit)?, " +
            "p2: kotlin.Function0<kotlin.Long>, )"
    }

    @Test
    fun `a qualifier on a parameter is kept and another annotation is not`() {
        val qualifiers = """
            import dev.zacsweers.metro.Qualifier
            import kotlin.reflect.KClass

            @Qualifier
            annotation class From(val source: KClass<*>, val tags: Array<String>, val retries: Int = 1)

            annotation class Unrelated
        """.trimIndent()

        val handlers = allListHandlers.replace(
            "(val limit: Int)",
            "(@dev.zacsweers.metro.Named(\"page \\\"size\\\" in \\$\") val limit: Int, " +
                "@From(String::class, tags = [\"a\", \"b\"]) @Unrelated val name: String)",
        )

        val compiled = compile(metroListScreen, qualifiers, handlers)
        val provider = compiled.providers().first()

        compiled.succeeded shouldBe true
        provider shouldContain "@dev.zacsweers.metro.Named(name = \"page \\\"size\\\" in \\$\") p0: kotlin.Int,"
        provider shouldContain
            "@app.From(source = kotlin.String::class, tags = [\"a\", \"b\"], retries = 1) p1: kotlin.String,"
        provider shouldNotContain "Unrelated"
    }

    @Test
    fun `a qualifier that takes an enum entry is written with the entry's full name`() {
        val qualifier = """
            import dev.zacsweers.metro.Qualifier

            enum class Pool { Io, Main }

            @Qualifier
            annotation class On(val pool: Pool)
        """.trimIndent()

        val handlers = allListHandlers.replace("(val limit: Int)", "(@On(Pool.Io) val limit: Int)")
        val compiled = compile(metroListScreen, qualifier, handlers)

        compiled.succeeded shouldBe true
        compiled.providers().first() shouldContain "@app.On(pool = app.Pool.Io) p0: kotlin.Int,"
    }

    @Test
    fun `a handler with only a secondary constructor gets a parameter for each of its parameters`() {
        val handlers = allListHandlers.replace(
            "class OnSearchHandler(val limit: Int) :",
            "class OnSearchHandler :",
        ).replace(
            "override val eventType = ListEvent.OnSearch::class",
            "constructor(limit: Int, name: String)\n        override val eventType = ListEvent.OnSearch::class",
        )

        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.providers().first() shouldContain "p0: kotlin.Int, p1: kotlin.String, ): app.ListHandler<*> = " +
            "app.OnSearchHandler(p0, p1)"
    }

    @Test
    fun `an object handler is provided as it is`() {
        val handlers = allListHandlers.replace("class OnStartHandler", "object OnStartHandler")
        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.providers()[1] shouldBe
            "@Provides @IntoSet public fun onStartHandler(): app.ListHandler<*> = app.OnStartHandler"
    }

    @Test
    fun `an internal interface gets an internal container that can provide internal handlers`() {
        val screen = metroListScreen.replace("interface ListHandler", "internal interface ListHandler")

        val handlers = allListHandlers.replace("class ", "internal class ")
        val compiled = compile(screen, handlers)
        val container = compiled.metroBindings.single()

        compiled.succeeded shouldBe true
        container shouldContain "internal object EventusListHandlerBindings {"
        container shouldContain "internal fun onStartHandler()"
        container shouldNotContain "public"
    }

    @Test
    fun `a public container can provide internal handlers`() {
        val handlers = allListHandlers.replace("class ", "internal class ")
        val compiled = compile(metroListScreen, handlers)

        compiled.succeeded shouldBe true
        compiled.metroBindings.single() shouldContain "public object EventusListHandlerBindings {"
    }

    @Test
    fun `each screen that asks gets a container with its own handlers`() {
        val editor = """
            package editor

            import io.github.mradzinski.eventus.EventHandler
            import io.github.mradzinski.eventus.UiEffect
            import io.github.mradzinski.eventus.UiEvent
            import io.github.mradzinski.eventus.UiState

            object EditorScope

            data object EditorState : UiState

            sealed interface EditorEffect : UiEffect

            sealed interface EditorEvent : UiEvent {
                data object OnStart : EditorEvent
            }

            $METRO_REQUEST(EditorScope::class)
            interface EditorHandler : EventHandler<EditorEvent.OnStart, EditorState, EditorEffect>

            class OnStartHandler : EditorHandler {
                override val eventType = EditorEvent.OnStart::class
                override suspend fun handle(
                    event: EditorEvent.OnStart,
                    store: io.github.mradzinski.eventus.ScreenStore<EditorState, EditorEffect>,
                ) = Unit
            }
        """.trimIndent()

        val compiled = compile(metroListScreen, allListHandlers, editor)
        val (editorBindings, listBindings) = compiled.metroBindings

        compiled.succeeded shouldBe true
        compiled.metroBindings shouldHaveSize 2
        editorBindings shouldContain "@ContributesTo(editor.EditorScope::class)"
        editorBindings shouldContain "fun onStartHandler(): editor.EditorHandler = editor.OnStartHandler()"
        editorBindings shouldNotContain "app."
        listBindings shouldContain "fun onStartHandler(): app.ListHandler<*> = app.OnStartHandler()"
        listBindings shouldNotContain "editor."
    }

    @Test
    fun `two handlers of one name on a screen get providers named after their full names`() {
        val other = """
            package app.rows

            import app.ListEvent
            import app.ListHandler
            import app.ListStore

            class OnStartHandler : ListHandler<ListEvent.Row.Click> {
                override val eventType = ListEvent.Row.Click::class
                override suspend fun handle(event: ListEvent.Row.Click, store: ListStore) = Unit
            }
        """.trimIndent()

        val handlers = allListHandlers.substringBefore("class RowClickHandler")
        val compiled = compile(metroListScreen, handlers, other)

        compiled.succeeded shouldBe true
        compiled.providers().map { it.substringBefore("(").substringAfter("fun ") } shouldContainExactly listOf(
            "onSearchHandler",
            "app_OnStartHandler",
            "app_rows_OnStartHandler",
        )
    }

    @Test
    fun `a nested interface gets a container named after the names around it`() {
        val screen = """
            object Screens {
                $METRO_REQUEST(dev.zacsweers.metro.AppScope::class)
                interface Rows : ListHandler<ListEvent.Row.Click>
            }

            class RowClickHandler : Screens.Rows ${handlerBody(event = "ListEvent.Row.Click")}
        """.trimIndent()

        val handlers = allListHandlers.substringBefore("class RowClickHandler")
        val compiled = compile(listScreen, handlers, screen)
        val container = compiled.metroBindings.single()

        compiled.succeeded shouldBe true
        container shouldContain "public object EventusScreensRowsBindings {"
        container shouldContain "fun rowClickHandler(): app.Screens.Rows = app.RowClickHandler()"
    }

    @Test
    fun `a handler behind an abstract class joins the set of the interface above it`() {
        val handlers = """
            abstract class BaseHandler<E : ListEvent> : ListHandler<E>

            class OnStartHandler : BaseHandler<ListEvent.OnStart>() ${handlerBody(event = "ListEvent.OnStart")}
        """.trimIndent()

        val others = allListHandlers.substringAfter("class OnStartHandler").substringAfter("}\n")
        val compiled = compile(metroListScreen, handlers, others)

        compiled.succeeded shouldBe true
        compiled.providers() shouldHaveSize 3
    }

    @Test
    fun `a handler the router can never call is left out of the container`() {
        val handler = listHandler(name = "RowHandler", event = "ListEvent.Row")
        val compiled = compile(metroListScreen, allListHandlers, handler)

        compiled.providers() shouldHaveSize 3
    }

    @Test
    fun `an interface with no handlers gets no container`() {
        val compiled = compile(metroListScreen)

        compiled.metroBindings.shouldBeEmpty()
    }

    @Test
    fun `a Koin module and Metro bindings can be asked for together`() {
        val compiled = compile(metroListScreen, allListHandlers, koinModuleRequest)

        compiled.problems.shouldBeEmpty()
        compiled.koinModule.shouldNotBeNull()
        compiled.metroBindings shouldHaveSize 1
    }

    @Test
    fun `the annotation on a class fails the build and writes nothing`() {
        val base = "$METRO_REQUEST(dev.zacsweers.metro.AppScope::class)\n" +
            "abstract class BaseHandler<E : ListEvent> : ListHandler<E>"

        val compiled = compile(listScreen, allListHandlers, base)

        compiled.metroBindings.shouldBeEmpty()
        compiled.problems.single() shouldStartWith "app.BaseHandler must be an interface that extends EventHandler"
    }

    @Test
    fun `the annotation on an interface that is not a handler interface fails the build`() {
        val other = "$METRO_REQUEST(dev.zacsweers.metro.AppScope::class)\ninterface Other"
        val compiled = compile(listScreen, allListHandlers, other)

        compiled.metroBindings.shouldBeEmpty()
        compiled.problems.single() shouldStartWith "app.Other must be an interface that extends EventHandler"
    }

    @Test
    fun `a private interface fails the build and writes nothing`() {
        val screen = metroListScreen.replace("interface ListHandler", "private interface ListHandler")

        val compiled = compile(screen + "\n\n" + allListHandlers.replace("class ", "private class "))

        compiled.metroBindings.shouldBeEmpty()
        compiled.problems shouldContain
            "app.ListHandler is private, so the generated Metro bindings cannot name it. Make it internal."
    }

    @Test
    fun `a private handler fails the build and writes nothing`() {
        val handlers = allListHandlers.replace("class OnStartHandler", "private class OnStartHandler")

        val compiled = compile(metroListScreen, handlers)

        compiled.metroBindings.shouldBeEmpty()
        compiled.problems.single() shouldBe
            "app.OnStartHandler is private, so the generated Metro bindings cannot create it. Make it internal."
    }

    @Test
    fun `a scope that is not a known class fails the build and writes nothing`() {
        val screen = metroListScreen.replace("dev.zacsweers.metro.AppScope::class", "Missing::class")

        val compiled = compile(screen, allListHandlers)

        compiled.metroBindings.shouldBeEmpty()
        compiled.problems shouldContain
            "The scope that app.ListHandler gives to @ContributesEventHandlers is not a known class."
    }

    /** The provider functions of the one generated container, each on one line. */
    private fun Compiled.providers(): List<String> = metroBindings
        .single()
        .substringAfter("Bindings {\n")
        .substringBeforeLast("}")
        .split("\n\n")
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotEmpty() }
}
