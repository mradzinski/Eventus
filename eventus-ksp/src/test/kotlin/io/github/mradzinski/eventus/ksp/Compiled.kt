package io.github.mradzinski.eventus.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** What came out of compiling some sources with the processor applied. */
internal class Compiled(
    /**
     * Whether the sources compiled. Always false when a Koin module was requested: the test compiles one
     * module, and Kotlin wants an `expect` and its `actual` in two.
     */
    val succeeded: Boolean,
    /** What the processor reported as errors, one message each, without the file and line. */
    val problems: List<String>,
    /** The text of the generated Koin module, or null when none was written. */
    val koinModule: String?,
    /** The text of each generated Metro binding container, in file name order. */
    val metroBindings: List<String> = emptyList(),
)

private const val PROCESSOR_ERROR = "e: [ksp] "
private const val METRO_CONTAINER = "@BindingContainer"

/** The annotation that asks for Metro bindings, as an app writes it on a handler interface. */
internal const val METRO_REQUEST = "@io.github.mradzinski.eventus.metro.ContributesEventHandlers"
private const val NOT_MULTIPLATFORM = "can be used only in multiplatform projects"

/** One screen, declared the way the README does: a sealed event type and a handler interface of its own. */
internal val listScreen: String = """
    package app

    import io.github.mradzinski.eventus.EventHandler
    import io.github.mradzinski.eventus.ScreenStore
    import io.github.mradzinski.eventus.UiEffect
    import io.github.mradzinski.eventus.UiEvent
    import io.github.mradzinski.eventus.UiState

    data class ListState(val query: String = "") : UiState

    sealed interface ListEffect : UiEffect

    sealed interface ListEvent : UiEvent {
        data object OnStart : ListEvent
        data class OnSearch(val query: String) : ListEvent

        sealed interface Row : ListEvent {
            data object Click : Row
        }
    }

    interface ListHandler<E : ListEvent> : EventHandler<E, ListState, ListEffect>

    typealias ListStore = ScreenStore<ListState, ListEffect>
""".trimIndent()

/** The body every handler in these tests has; what a handler does is not what the processor looks at. */
internal fun handlerBody(event: String): String = """
    {
        override val eventType = $event::class
        override suspend fun handle(event: $event, store: ListStore) = Unit
    }
""".trimIndent()

/** A handler of the list screen for [event], written as `ListEvent.OnStart` and so on. */
internal fun listHandler(
    name: String,
    event: String,
    constructor: String = "",
): String = "class $name$constructor : ListHandler<$event> ${handlerBody(event)}"

/** The three handlers that leave no event of [listScreen] unhandled. */
internal val allListHandlers: String = listOf(
    listHandler(name = "OnStartHandler", event = "ListEvent.OnStart"),
    listHandler(name = "OnSearchHandler", event = "ListEvent.OnSearch", constructor = "(val limit: Int)"),
    listHandler(name = "RowClickHandler", event = "ListEvent.Row.Click"),
).joinToString(separator = "\n\n")

/** The request for a generated Koin module, as an app writes it in common code. */
internal val koinModuleRequest: String = """
    @io.github.mradzinski.eventus.koin.EventusKoinModule
    internal expect val handlersModule: org.koin.core.module.Module
""".trimIndent()

/** [listScreen] with its handler interface asking for Metro bindings in Metro's own `AppScope`. */
internal val metroListScreen: String = listScreen.replace(
    "interface ListHandler",
    "$METRO_REQUEST(dev.zacsweers.metro.AppScope::class)\ninterface ListHandler",
)

/**
 * Compiles [sources] together with the Koin module the processor generates for them, to show that the
 * generated code is valid against the real Koin. The module is compiled as a plain property, without
 * `actual`, because one JVM module cannot hold an `expect` and its `actual`.
 */
internal fun compileWithGeneratedKoinModule(vararg sources: String): Compiled {
    val generated = compile(*sources, koinModuleRequest).koinModule ?: error("No Koin module was generated.")

    val plainModule = generated
        .replace("@$KOIN_MODULE_ANNOTATION\n", "")
        .replace(" actual val ", " val ")

    return compile(plainModule, *sources)
}

/**
 * Compiles [sources] as the files of one package, `app`, with the processor applied. A source
 * with no `package` line of its own gets that one.
 */
@OptIn(ExperimentalCompilerApi::class)
internal fun compile(vararg sources: String): Compiled {
    val files = sources.mapIndexed { index, source ->
        val hasPackage = source.lineSequence().any { it.startsWith("package ") }
        val text = if (hasPackage) source else "package app\n\n$source"

        SourceFile.kotlin(name = "Source$index.kt", contents = text)
    }

    val compilation = KotlinCompilation().apply {
        this.sources = files
        inheritClassPath = true
        // The Koin module is requested with an `expect` property, as in a multiplatform module.
        multiplatform = true
        configureKsp { symbolProcessorProviders += EventusProcessorProvider() }
    }

    val result = compilation.compile()

    val generated = compilation.kspSourcesDir
        .walkTopDown()
        .filter { it.isFile && it.name.startsWith("Eventus") }
        .sortedBy { it.name }
        .map { it.readText() }
        .toList()

    return Compiled(
        succeeded = result.exitCode == KotlinCompilation.ExitCode.OK,
        problems = result.messages
            .lines()
            .filter { it.startsWith(PROCESSOR_ERROR) }
            // Not from the processor: the test compiles a plain JVM module, where `expect` is not allowed.
            .filterNot { NOT_MULTIPLATFORM in it }
            .map { it.substringAfter(": ", missingDelimiterValue = it).substringAfter(": ") },
        koinModule = generated.firstOrNull { METRO_CONTAINER !in it },
        metroBindings = generated.filter { METRO_CONTAINER in it },
    )
}
