package io.github.mradzinski.eventus.sample.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.github.mradzinski.eventus.sample.app.AppEvent
import io.github.mradzinski.eventus.sample.app.AppHandler
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * An event without a handler is only found when it is sent. These tests find it earlier: they take the
 * handlers out of a Metro graph and compare them with the events of each screen, so they catch a
 * handler that was never written as well as one that was written but never registered. They run on the
 * JVM only, because listing the subclasses of a sealed type needs reflection.
 */
class HandlerCoverageTest {

    /** A graph that asks for the app's own event handlers, filled like the app's graph. */
    @DependencyGraph(AppScope::class)
    internal interface CoverageGraph {

        val handlers: Set<AppHandler<*>>
    }

    private val graph = createGraph<CoverageGraph>()

    @Test
    fun `every app event has a registered handler`() {
        val handled = graph.handlers.map { it.eventType }

        handled.sortedBy { it.simpleName } shouldBe eventsThatNeedAHandler(AppEvent::class)
    }

    /** The events of a screen, sorted by name. Every one of them needs a handler. */
    private fun eventsThatNeedAHandler(events: KClass<*>): List<KClass<*>> =
        events.sealedSubclasses.sortedBy { it.simpleName }
}
