package io.github.mradzinski.eventus.sample.taskeditor

import io.github.mradzinski.eventus.sample.domain.Connectivity
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import io.github.mradzinski.eventus.sample.testing.FakeConnectivity
import io.github.mradzinski.eventus.sample.testing.FakeTaskRepository
import io.github.mradzinski.eventus.sample.testing.silentLogger
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.test.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * An event without a handler is only found when it is sent. These tests find it earlier: they take the
 * handlers out of the real Koin module of this feature and compare them with the events of the screen, so they catch a
 * handler that was never written as well as one that was written but never registered. They run on the
 * JVM only, because listing the subclasses of a sealed type needs reflection.
 */
class HandlerCoverageTest {

    /** What the app would provide: this feature depends on the domain, so the test gives it fakes. */
    private val fakes = module {
        single<TaskRepository> { FakeTaskRepository() }
        single<Connectivity> { FakeConnectivity() }
        single { silentLogger() }
    }

    private val koin = koinApplication { modules(taskEditorModule, fakes) }.koin

    @Test
    fun `every task editor event has a registered handler`() {
        val handled = koin
            .getAll<TaskEditorHandler<*>>()
            .map { it.eventType }

        handled.sortedBy { it.simpleName } shouldBe eventsThatNeedAHandler(TaskEditorEvent::class)
    }

    /** The events of a screen, sorted by name. Every one of them needs a handler. */
    private fun eventsThatNeedAHandler(events: KClass<*>): List<KClass<*>> =
        events.sealedSubclasses.sortedBy { it.simpleName }
}
