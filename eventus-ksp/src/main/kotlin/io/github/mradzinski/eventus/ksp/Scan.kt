package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/** A class that implements `EventHandler` and can be created: not abstract, not an interface. */
internal class Handler(
    val declaration: KSClassDeclaration,
    /** The event class it handles, or null when its event type is not one known class. */
    val event: KSClassDeclaration?,
    /** The interfaces between it and `EventHandler`, nearest first: usually the handler interface of its screen. */
    val screenInterfaces: List<KSClassDeclaration>,
    /** The state and effect types it is written for, each null when it is not known. */
    val state: KSType? = null,
    val effect: KSType? = null,
)

/** A class that implements `EventRouter`, as a view model does by delegation. */
internal class Router(
    val declaration: KSClassDeclaration,
    /** The event type it routes, or null when that is not one known class. */
    val event: KSClassDeclaration?,
    /** The state and effect types of its handlers, each null when it is not known. */
    val state: KSType?,
    val effect: KSType?,
)

/** What the processor found in the sources of one Gradle module. */
internal class Scan(
    val handlers: List<Handler>,
    val errorHandlers: List<KSClassDeclaration>,
    val routers: List<Router>,
    /** The sealed event types declared in this module that are not inside another sealed type. */
    val eventRoots: List<KSClassDeclaration>,
)

// `EventHandler<E, S, F>` and `EventRouter<E, S, F>` take their types in the same order.
private const val STATE_ARGUMENT = 1
private const val EFFECT_ARGUMENT = 2

internal fun Resolver.scan(): Scan {
    val classes = getAllFiles()
        .flatMap { it.declarations }
        .flatMap { it.withNestedClasses() }
        .toList()

    val creatable = classes.filter { it.isCreatable() }

    return Scan(
        handlers = creatable.mapNotNull { it.toHandler() },
        errorHandlers = creatable.filter { it.isSubtypeOf(ERROR_HANDLER) },
        routers = creatable.mapNotNull { it.toRouter() },
        eventRoots = classes.filter { it.isEventRoot() },
    )
}

internal val KSClassDeclaration.isSealed: Boolean get() = Modifier.SEALED in modifiers

internal val KSClassDeclaration.isAbstract: Boolean
    get() = classKind == ClassKind.INTERFACE || Modifier.ABSTRACT in modifiers || isSealed

internal val KSDeclaration.displayName: String get() = qualifiedName?.asString() ?: simpleName.asString()

private fun KSDeclaration.withNestedClasses(): Sequence<KSClassDeclaration> {
    if (this !is KSClassDeclaration) return emptySequence()

    return sequenceOf(this) + declarations.flatMap { it.withNestedClasses() }
}

private fun KSClassDeclaration.isCreatable(): Boolean {
    val isClassOrObject = classKind == ClassKind.CLASS || classKind == ClassKind.OBJECT

    return isClassOrObject && !isAbstract
}

private fun KSClassDeclaration.toHandler(): Handler? {
    val path = pathTo(EVENT_HANDLER) ?: return null

    val event = path.supertype.arguments
        .firstOrNull()
        ?.toClassType()

    return Handler(
        declaration = this,
        event = event?.declaration,
        screenInterfaces = path.through,
        state = path.supertype.arguments.getOrNull(STATE_ARGUMENT),
        effect = path.supertype.arguments.getOrNull(EFFECT_ARGUMENT),
    )
}

private fun KSClassDeclaration.toRouter(): Router? {
    val arguments = pathTo(EVENT_ROUTER)?.supertype?.arguments ?: return null

    val event = arguments
        .firstOrNull()
        ?.toClassType()

    return Router(
        declaration = this,
        event = event?.declaration,
        state = arguments.getOrNull(STATE_ARGUMENT),
        effect = arguments.getOrNull(EFFECT_ARGUMENT),
    )
}

private fun KSClassDeclaration.isEventRoot(): Boolean {
    if (!isSealed || !isSubtypeOf(UI_EVENT)) return false

    val parents = superTypes.mapNotNull { it.resolve().toClassType() }
    val hasSealedParent = parents.any { it.declaration.isSealed }

    return !hasSealedParent
}
