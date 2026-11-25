package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/** One screen whose handlers are contributed to the Metro graph of [scope], a fully qualified name. */
internal class MetroBindings(
    val screen: KSClassDeclaration,
    val scope: String,
    val handlers: List<Handler>,
)

/** The Metro bindings the module asks for and asks for correctly, and what is wrong with the others. */
internal class MetroBindingsRequest(val bindings: List<MetroBindings>, val problems: List<Problem>)

internal fun Resolver.metroBindingsRequest(scan: Scan): MetroBindingsRequest {
    val screens = getSymbolsWithAnnotation(METRO_ANNOTATION)
        .filterIsInstance<KSClassDeclaration>()
        .sortedBy { it.displayName }
        .toList()

    val problems = screens.flatMap { it.metroProblems(scan) }
    val broken = problems.map { it.symbol }

    // A screen with nothing to contribute gets no container: Metro then reports the missing set itself.
    val bindings = screens
        .filterNot { screen -> screen in broken || scan.handlersOf(screen).any { it.declaration in broken } }
        .mapNotNull { it.toMetroBindings(scan) }

    return MetroBindingsRequest(bindings = bindings, problems = problems)
}

private fun KSClassDeclaration.toMetroBindings(scan: Scan): MetroBindings? {
    val scope = scope() ?: return null
    val handlers = scan.handlersOf(this)

    if (handlers.isEmpty()) return null

    return MetroBindings(
        screen = this,
        scope = scope.declaration.displayName,
        handlers = handlers,
    )
}

/** The handlers the router can call that implement this interface, in name order so every target agrees. */
private fun Scan.handlersOf(screen: KSClassDeclaration): List<Handler> = handlers
    .filter { handler -> handler.isRoutable() && handler.screenInterfaces.any { it.displayName == screen.displayName } }
    .sortedBy { it.declaration.displayName }

private fun KSClassDeclaration.metroProblems(scan: Scan): List<Problem> {
    val private = scan
        .handlersOf(this)
        .map { it.declaration }
        .filter { Modifier.PRIVATE in it.modifiers }
        .map { it.privateProblem(generated = "the generated Metro bindings") }

    return listOfNotNull(notHandlerInterfaceProblem(), privateScreenProblem(), unknownScopeProblem()) +
        private +
        leftOutProblems(scan)
}

/**
 * The handlers written for the state and effects of this screen that implement `EventHandler` directly.
 * Such a handler is in no set that Metro provides, so its screen would only find out when the event is
 * sent. A handler under another interface is left alone: that is another screen's.
 */
private fun KSClassDeclaration.leftOutProblems(scan: Scan): List<Problem> {
    val written = pathTo(EVENT_HANDLER)?.supertype?.arguments ?: return emptyList()

    val own = stateAndEffect(
        state = written.getOrNull(STATE_ARGUMENT),
        effect = written.getOrNull(EFFECT_ARGUMENT),
    )

    return scan.handlers
        .filter { it.screenInterfaces.isEmpty() }
        .filter { stateAndEffect(state = it.state, effect = it.effect) == own }
        .sortedBy { it.declaration.displayName }
        .map { it.leftOutProblem(screen = this) }
}

/** The two types as text, to compare what a handler is written for with what a screen is. */
private fun stateAndEffect(state: KSType?, effect: KSType?): List<String> = listOfNotNull(state, effect)
    .map { it.toSource() }

private fun Handler.leftOutProblem(screen: KSClassDeclaration): Problem = Problem(
    message = "${declaration.displayName} implements EventHandler directly, so it is not in the Metro bindings " +
        "of ${screen.displayName}, whose state and effects it is written for. Implement " +
        "${screen.simpleName.asString()} instead.",
    symbol = declaration,
)

private fun KSClassDeclaration.notHandlerInterfaceProblem(): Problem? {
    if (classKind == ClassKind.INTERFACE && isSubtypeOf(EVENT_HANDLER)) return null

    return Problem(
        message = "$displayName must be an interface that extends EventHandler: @ContributesEventHandlers goes " +
            "on the handler interface of a screen.",
        symbol = this,
    )
}

private fun KSClassDeclaration.privateScreenProblem(): Problem? {
    if (Modifier.PRIVATE !in modifiers) return null

    return Problem(
        message = "$displayName is private, so the generated Metro bindings cannot name it. Make it internal.",
        symbol = this,
    )
}

private fun KSClassDeclaration.unknownScopeProblem(): Problem? {
    if (scope() != null) return null

    return Problem(
        message = "The scope that $displayName gives to @ContributesEventHandlers is not a known class.",
        symbol = this,
    )
}

/** The class given as the scope, or null when it does not resolve. */
private fun KSClassDeclaration.scope(): KSType? {
    val annotation = annotations.first { it.annotationType.resolve().declaration.displayName == METRO_ANNOTATION }

    val scope = annotation.arguments.firstOrNull()?.value as? KSType

    return scope?.takeUnless { it.isError }
}
