package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

internal class EventusProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {

    // `apiFeatures` is left alone on purpose. KSP 2.3.13 and newer log a note asking processors to override
    // it, but the type it returns does not exist in 2.3.12, where this processor would then fail to load.
    // KSP calls `process` again for the file this processor generates, which holds nothing to look at.
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        processed = true

        val scan = resolver.scan()
        val request = resolver.koinModuleRequest()
        val koinProblems = if (request.property == null) emptyList() else scan.koinProblems()
        val metro = resolver.metroBindingsRequest(scan)
        val problems = scan.routingProblems() + request.problems + koinProblems + metro.problems

        problems.forEach { logger.error(it.message, it.symbol) }

        if (request.property != null) {
            writeKoinModule(
                property = request.property,
                scan = scan,
                codeGenerator = codeGenerator,
            )
        }

        metro.bindings.forEach { writeMetroBindings(bindings = it, codeGenerator = codeGenerator) }

        return emptyList()
    }
}
