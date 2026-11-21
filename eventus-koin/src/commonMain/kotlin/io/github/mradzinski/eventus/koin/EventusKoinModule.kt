package io.github.mradzinski.eventus.koin

/**
 * Asks `eventus-ksp` to write the Koin module of every event handler in this Gradle module.
 *
 * Declare the module once, in common code, and leave its content to the processor:
 *
 * ```
 * // The Eventus processor generates the `actual` property on each target.
 * @EventusKoinModule
 * @Suppress("KotlinNoActualForExpect")
 * internal expect val handlersModule: Module
 * ```
 *
 * The `@Suppress` line is for Android Studio, which cannot see the generated `actual` property and may
 * warn that there is none. The build does not need it.
 *
 * On each target the processor generates the `actual` property. It registers every class that implements
 * `EventHandler` as a factory, bound to the handler interface of its screen, and every class that implements
 * `ErrorHandler` as a factory. A view model then asks Koin for its screen's handlers as before, with
 * `getAll<TaskListHandler<*>>()`, and the list of modules given to Koin gains `handlersModule`.
 *
 * A Gradle module declares at most one such property, and its type must be Koin's `Module`. The processor
 * must be added to every target of the Gradle module (`kspAndroid`, `kspJvm`, `kspIosArm64` and so on):
 * a target without it fails to compile, because the `expect` property has no `actual` there.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
public annotation class EventusKoinModule
