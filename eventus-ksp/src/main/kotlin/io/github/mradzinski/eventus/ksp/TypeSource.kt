package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Variance

// Generated Metro code names the type of every constructor parameter, which the Koin module never does:
// there, `get()` leaves the type to the compiler.

/** This type as it is written in a source file, with full names, so it needs no import. */
internal fun KSType.toSource(): String {
    val nullable = if (isMarkedNullable) "?" else ""

    // The class behind `suspend () -> Unit` has no name a source file can use.
    if (isSuspendFunctionType) {
        val parameters = arguments
            .dropLast(1)
            .joinToString { it.toSource() }

        val function = "suspend ($parameters) -> ${arguments.last().toSource()}"

        return if (isMarkedNullable) "($function)?" else function
    }

    if (arguments.isEmpty()) return declaration.displayName + nullable

    return declaration.displayName + arguments.joinToString(prefix = "<", postfix = ">") { it.toSource() } + nullable
}

/**
 * The qualifiers of this parameter, each followed by a space, ready to go before the parameter's name.
 *
 * A qualifier is an annotation whose own class is annotated with `Qualifier`: Metro's, or one of the
 * `javax`, `jakarta` and kotlin-inject ones that Metro can be set up to read.
 */
internal fun KSValueParameter.qualifiers(): String = annotations
    .filter { it.isQualifier() }
    .joinToString(separator = "") { it.toSource() + " " }

private fun KSTypeArgument.toSource(): String {
    val type = type?.resolve()

    if (variance == Variance.STAR || type == null) return "*"

    return "${variance.label} ${type.toSource()}".trim()
}

private fun KSAnnotation.isQualifier(): Boolean = annotationType
    .resolve()
    .declaration
    .annotations
    .any { it.shortName.asString() == "Qualifier" }

private fun KSAnnotation.toSource(): String {
    val name = annotationType
        .resolve()
        .declaration
        .displayName

    val arguments = arguments.joinToString { "${it.name?.asString()} = ${it.value.toLiteral()}" }

    return "@$name($arguments)"
}

/** A value of an annotation as it is written in a source file. */
private fun Any?.toLiteral(): String = when (this) {
    is String -> "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    is KSType -> "${declaration.displayName}::class"

    // An enum entry.
    is KSClassDeclaration -> displayName

    is List<*> -> joinToString(prefix = "[", postfix = "]") { it.toLiteral() }

    else -> toString()
}
