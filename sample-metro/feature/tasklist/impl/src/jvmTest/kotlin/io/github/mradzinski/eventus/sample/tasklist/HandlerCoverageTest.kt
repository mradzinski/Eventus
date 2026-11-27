package io.github.mradzinski.eventus.sample.tasklist

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import io.github.mradzinski.eventus.sample.domain.Connectivity
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.testing.FakeConnectivity
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.silentLogger
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * An event without a handler is only found when it is sent. These tests find it earlier: they take the
 * handlers out of a Metro graph of this feature and compare them with the events of each screen, so they catch a
 * handler that was never written as well as one that was written but never registered. They run on the
 * JVM only, because listing the subclasses of a sealed type needs reflection.
 */
class HandlerCoverageTest {

    /**
     * A graph that asks for this feature's event handlers. The feature depends on the domain, so the
     * graph gives it fakes where the app would give it the data layer.
     */
    @DependencyGraph(AppScope::class)
    interface CoverageGraph {

        val handlers: Set<TaskListHandler<*>>

        @Provides
        fun tasks(): TaskRepository = FakeTaskRepository()

        @Provides
        fun connectivity(): Connectivity = FakeConnectivity()

        @Provides
        fun logger(): Logger = silentLogger()
    }

    private val graph = createGraph<CoverageGraph>()

    @Test
    fun `every task list event has a registered handler`() {
        val handled = graph.handlers.map { it.eventType }

        handled.sortedBy { it.simpleName } shouldBe eventsThatNeedAHandler(TaskListEvent::class)
    }

    /** The events of a screen, sorted by name. Every one of them needs a handler. */
    private fun eventsThatNeedAHandler(events: KClass<*>): List<KClass<*>> =
        events.sealedSubclasses.sortedBy { it.simpleName }
}
