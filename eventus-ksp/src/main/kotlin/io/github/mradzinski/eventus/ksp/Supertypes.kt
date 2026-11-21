package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter

/** A class or interface with the type arguments it was written with. An argument that is not known is null. */
internal class ClassType(val declaration: KSClassDeclaration, val arguments: List<KSType?>)

/** How a class reaches one of its supertypes: the supertype, and the types in between, nearest first. */
internal class SupertypePath(val supertype: ClassType, val through: List<KSClassDeclaration>)

/**
 * Finds the supertype named [qualifiedName], carrying type arguments along the way.
 *
 * KSP's own `getAllSuperTypes` does not: for `OnRefreshHandler : TaskListHandler<OnRefresh>` it gives
 * `EventHandler<E, …>` with the parameter `E`, not with `OnRefresh`.
 */
internal fun KSClassDeclaration.pathTo(
    qualifiedName: String,
    bound: Map<String, KSType> = emptyMap(),
): SupertypePath? = superTypes
    .mapNotNull { it.resolve().toClassType(bound) }
    .firstNotNullOfOrNull { it.pathOnTo(qualifiedName) }

internal fun KSClassDeclaration.isSubtypeOf(qualifiedName: String): Boolean = pathTo(qualifiedName) != null

/** The class this type names, looking through type aliases, with [bound] put in place of type parameters. */
internal fun KSType.toClassType(bound: Map<String, KSType> = emptyMap()): ClassType? {
    if (isError) return null

    // A star projection has no type of its own.
    val arguments = arguments.map { argument ->
        argument.type?.let { it.resolve().substitute(bound) }
    }

    val declaration = declaration

    if (declaration is KSTypeAlias) {
        return declaration.type
            .resolve()
            .toClassType(declaration.typeParameters.bind(arguments))
    }

    return (declaration as? KSClassDeclaration)?.let { ClassType(it, arguments) }
}

/** The way from this supertype to the one named [qualifiedName]: none if it is that one, or through it. */
private fun ClassType.pathOnTo(qualifiedName: String): SupertypePath? {
    if (declaration.displayName == qualifiedName) return SupertypePath(this, through = emptyList())

    val path = declaration.pathTo(qualifiedName, bindings())

    return path?.let { SupertypePath(it.supertype, through = listOf(declaration) + it.through) }
}

private fun KSType.substitute(bound: Map<String, KSType>): KSType? {
    val parameter = declaration as? KSTypeParameter ?: return this

    return bound[parameter.name.asString()]
}

private fun ClassType.bindings(): Map<String, KSType> = declaration.typeParameters.bind(arguments)

private fun List<KSTypeParameter>.bind(arguments: List<KSType?>): Map<String, KSType> = zip(arguments)
    .mapNotNull { (parameter, argument) -> argument?.let { parameter.name.asString() to it } }
    .toMap()
