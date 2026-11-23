package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Modifier

/** Something that fails the build, reported at [symbol]. */
internal class Problem(val message: String, val symbol: KSNode)

/**
 * What the router would only find out at run time: an event nobody handles, an event with two handlers, and
 * a handler the router can never call.
 */
internal fun Scan.routingProblems(): List<Problem> {
    val handlersByEvent = handlers
        .mapNotNull { handler -> handler.routableEvent?.let { event -> event.displayName to handler } }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    val unroutable = handlers.mapNotNull { it.unroutableProblem() }

    val duplicates = handlersByEvent.flatMap { (event, sameEvent) ->
        sameEvent
            .drop(1)
            .map { it.duplicateProblem(event = event, first = sameEvent.first()) }
    }

    val missing = eventRoots
        .flatMap { it.leaves() }
        .filterNot { it.displayName in handlersByEvent }
        .map { it.missingHandlerProblem() }

    val handled = handlersByEvent.keys

    return unroutable + duplicates + missing + routers.mapNotNull { it.widerTypeProblem(handlers, handled) }
}

/** What stops the generated Koin module from registering a handler in a way its view model can ask for. */
internal fun Scan.koinProblems(): List<Problem> {
    val registered = handlers.map { it.declaration } + errorHandlers

    val private = registered
        .filter { Modifier.PRIVATE in it.modifiers }
        .map { it.privateProblem(generated = "the generated Koin module") }

    val unbound = handlers
        .filter { it.screenInterfaces.isEmpty() }
        .map { it.unboundProblem() }

    return private + unbound
}

/** The router matches an event by its exact class, so only a handler of one concrete class is ever called. */
internal fun Handler.isRoutable(): Boolean = routableEvent != null

/** The event this handler is called for, or null when the router can never call it. */
internal val Handler.routableEvent: KSClassDeclaration? get() = event?.takeUnless { it.isAbstract }

internal fun KSClassDeclaration.privateProblem(generated: String): Problem = Problem(
    message = "$displayName is private, so $generated cannot create it. Make it internal.",
    symbol = this,
)

/** The concrete event classes of a sealed type, including those of the sealed types nested in it. */
internal fun KSClassDeclaration.leaves(): List<KSClassDeclaration> = getSealedSubclasses()
    .flatMap { subclass ->
        when {
            subclass.isSealed -> subclass.leaves()
            subclass.isAbstract -> emptyList()
            else -> listOf(subclass)
        }
    }
    .toList()

private fun Handler.unroutableProblem(): Problem? {
    if (isRoutable()) return null

    val reason = if (event == null) {
        "its event type is not one known class"
    } else {
        "${event.displayName} is sealed or abstract"
    }

    return Problem(
        message = "${declaration.displayName} is never called: $reason. The router sends an event to the " +
            "handler of its exact class, so a handler must name one concrete event class.",
        symbol = declaration,
    )
}

private fun Handler.duplicateProblem(event: String, first: Handler): Problem = Problem(
    message = "$event has two handlers: ${first.declaration.displayName} and " +
        "${declaration.displayName}. An event has exactly one handler.",
    symbol = declaration,
)

private fun KSClassDeclaration.missingHandlerProblem(): Problem = Problem(
    message = "No handler for $displayName. Add a class that implements EventHandler for it.",
    symbol = this,
)

private fun Handler.unboundProblem(): Problem = Problem(
    message = "${declaration.displayName} implements EventHandler directly, so a view model cannot ask Koin " +
        "for the handlers of its screen. Give the screen a handler interface that extends EventHandler, and " +
        "implement that.",
    symbol = declaration,
)
