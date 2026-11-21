package io.github.mradzinski.eventus.sample.di

import io.github.mradzinski.eventus.sample.presentation.app.AppEvent
import io.github.mradzinski.eventus.sample.presentation.app.AppHandler
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorEvent
import io.github.mradzinski.eventus.sample.presentation.taskeditor.TaskEditorHandler
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListEvent
import io.github.mradzinski.eventus.sample.presentation.tasklist.TaskListHandler
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass
import kotlin.test.Test
import org.koin.dsl.koinApplication

/**
 * An event without a handler is only found when it is sent. These tests find it earlier: they take the
 * handlers out of the real Koin modules and compare them with the events of each screen, so they catch a
 * handler that was never written as well as one that was written but never registered. They run on the
 * JVM only, because listing the subclasses of a sealed type needs reflection.
 */
class HandlerCoverageTest {

    private val koin = koinApplication { modules(sampleModules) }.koin

    @Test
    fun `every app event has a registered handler`() {
        val handled = koin
            .getAll<AppHandler<*>>()
            .map { it.eventType }

        handled.sortedBy { it.simpleName } shouldBe eventsThatNeedAHandler(AppEvent::class)
    }

    @Test
    fun `every task list event has a registered handler`() {
        val handled = koin
            .getAll<TaskListHandler<*>>()
            .map { it.eventType }

        handled.sortedBy { it.simpleName } shouldBe eventsThatNeedAHandler(TaskListEvent::class)
    }

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
