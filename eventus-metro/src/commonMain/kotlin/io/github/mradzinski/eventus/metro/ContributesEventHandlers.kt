package io.github.mradzinski.eventus.metro

import kotlin.reflect.KClass

/**
 * Asks `eventus-ksp` to contribute every event handler of a screen to a Metro dependency graph.
 *
 * Put it on the handler interface of the screen, and name the scope of the graph that creates the
 * screen's view model:
 *
 * ```
 * @ContributesEventHandlers(AppScope::class)
 * internal interface TaskListHandler<E : TaskListEvent> : EventHandler<E, TaskListUiState, TaskListEffect>
 * ```
 *
 * On each target the processor generates a Metro binding container, contributed to [scope], that provides
 * every class implementing the interface into a set. The handler classes need no annotation. The view
 * model asks for that set:
 *
 * ```
 * @Inject
 * class TaskListViewModel(handlers: Set<TaskListHandler<*>>) : …
 * ```
 *
 * The generated container is as visible as the interface. A graph in another Gradle module sees only a
 * public one, so there the interface is public, and so are the types its handlers take in their
 * constructors. The handler classes can stay `internal`.
 *
 * In a multiplatform Gradle module the processor writes the container into each target, where a graph
 * declared in common code cannot see it: declare the graph once per platform, as Metro's documentation
 * describes.
 *
 * @property scope The scope of the Metro graph that receives the handlers, such as Metro's `AppScope`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class ContributesEventHandlers(val scope: KClass<*>)
