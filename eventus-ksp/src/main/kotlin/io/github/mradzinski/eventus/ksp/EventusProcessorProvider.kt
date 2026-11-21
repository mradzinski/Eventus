package io.github.mradzinski.eventus.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * The entry point KSP looks up to create the Eventus processor.
 *
 * The processor does two things in the Gradle module it is added to. It fails the build when an event of a
 * sealed event type has no handler class, or more than one. And when the module declares an `expect`
 * property annotated with `@EventusKoinModule`, it generates the `actual` property: a Koin module that
 * registers every event handler and error handler of the Gradle module.
 */
public class EventusProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = EventusProcessor(
        logger = environment.logger,
        codeGenerator = environment.codeGenerator,
    )
}
