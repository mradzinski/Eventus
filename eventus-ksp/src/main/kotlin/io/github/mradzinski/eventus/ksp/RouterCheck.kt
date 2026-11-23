package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * A router accepts every event of its type, and throws for one it was given no handler for. The handlers
 * it can be given are those written for its state and effects, so an event that only another screen
 * handles can never be handled by this router: its type is wider than what it handles.
 *
 * An event with no handler at all is left to the missing-handler check, which already reports it.
 */
internal fun Router.widerTypeProblem(handlers: List<Handler>, handled: Set<String>): Problem? {
    val event = event ?: return null

    val own = handlers
        .filter { it.state.isSameAs(state) && it.effect.isSameAs(effect) }
        .mapNotNull { it.routableEvent?.displayName }

    val foreign = event
        .concreteEvents()
        .map { it.displayName }
        .filter { it in handled && it !in own }
        .sorted()

    if (foreign.isEmpty()) return null

    return Problem(
        message = "${declaration.displayName} routes every ${event.displayName}, but it can never handle " +
            "${foreign.joinToString()}: the handlers of those events are written for another state or other " +
            "effects. Give the events that ${declaration.simpleName.asString()} handles a sealed type of their " +
            "own inside ${event.simpleName.asString()}, and route that type.",
        symbol = declaration,
    )
}

/** Unknown types are taken as the same: the check reports only what it is sure of. */
private fun KSType?.isSameAs(other: KSType?): Boolean = this == null || other == null || toSource() == other.toSource()

/** The event classes a router of this type accepts. */
private fun KSClassDeclaration.concreteEvents(): List<KSClassDeclaration> = when {
    isSealed -> leaves()
    isAbstract -> emptyList()
    else -> listOf(this)
}
