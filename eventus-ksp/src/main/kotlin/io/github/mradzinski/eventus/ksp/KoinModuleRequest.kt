package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

/** The property the Koin module is generated for, if the module asks for one and asks correctly. */
internal class KoinModuleRequest(val property: KSPropertyDeclaration?, val problems: List<Problem>)

internal fun Resolver.koinModuleRequest(): KoinModuleRequest {
    // The generated `actual` property carries the annotation too, and is not a request.
    val annotated = getSymbolsWithAnnotation(KOIN_MODULE_ANNOTATION)
        .filterIsInstance<KSPropertyDeclaration>()
        .filterNot { Modifier.ACTUAL in it.modifiers }
        .toList()

    val property = annotated.firstOrNull() ?: return KoinModuleRequest(property = null, problems = emptyList())

    val extra = annotated
        .drop(1)
        .map { it.extraRequestProblem(first = property) }

    val problems = listOfNotNull(property.notExpectProblem(), property.wrongTypeProblem()) + extra

    return KoinModuleRequest(
        property = property.takeIf { problems.isEmpty() },
        problems = problems,
    )
}

private fun KSPropertyDeclaration.notExpectProblem(): Problem? {
    if (Modifier.EXPECT in modifiers) return null

    return Problem(
        message = "${simpleName.asString()} must be an 'expect' property in common code: the processor " +
            "generates its 'actual' on each target.",
        symbol = this,
    )
}

private fun KSPropertyDeclaration.wrongTypeProblem(): Problem? {
    val type = type
        .resolve()
        .toClassType()
        ?.declaration
        ?.displayName

    if (type == KOIN_MODULE) return null

    return Problem(
        message = "${simpleName.asString()} must have the type $KOIN_MODULE.",
        symbol = this,
    )
}

private fun KSPropertyDeclaration.extraRequestProblem(first: KSPropertyDeclaration): Problem = Problem(
    message = "${simpleName.asString()} is a second @EventusKoinModule property; ${first.simpleName.asString()} " +
        "already holds every handler of this Gradle module. Keep one.",
    symbol = this,
)
