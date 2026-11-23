package io.github.mradzinski.eventus.integration.metro

import io.github.mradzinski.eventus.EventRouter
import io.github.mradzinski.eventus.integration.metro.counter.CounterEffect
import io.github.mradzinski.eventus.integration.metro.counter.CounterEvent
import io.github.mradzinski.eventus.integration.metro.counter.CounterState
import io.github.mradzinski.eventus.integration.metro.notes.NotesEffect
import io.github.mradzinski.eventus.integration.metro.notes.NotesEvent
import io.github.mradzinski.eventus.integration.metro.notes.NotesState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GeneratedBindingsTest {

    @Test
    fun `each screen receives the handlers of its own events and no others`() {
        val graph = createAppGraph()

        val counterEvents = setOf(
            CounterEvent.OnOpened::class,
            CounterEvent.OnPlusClick::class,
            CounterEvent.OnBreakClick::class,
        )

        val notesEvents = setOf(NotesEvent.OnOpened::class, NotesEvent.OnNoteAdd::class)

        assertEquals(counterEvents, graph.counter.handlers.map { it.eventType }.toSet())
        assertEquals(notesEvents, graph.notes.handlers.map { it.eventType }.toSet())
    }

    @Test
    fun `a router built from the injected handlers handles events with their dependencies`() = runTest {
        val screen = createAppGraph().counter

        val router = EventRouter<CounterEvent, CounterState, CounterEffect>(
            scope = backgroundScope,
            initialState = CounterState(),
            handlers = screen.handlers,
            errorHandler = screen.errorHandler,
        )

        router.onEvent(CounterEvent.OnOpened)
        router.onEvent(CounterEvent.OnPlusClick)
        router.onEvent(CounterEvent.OnBreakClick)
        testScheduler.runCurrent()

        assertEquals(CounterState(title = "Hello, 2 notes", count = 1, failure = "broken"), router.state.value)
    }

    @Test
    fun `a view model with a default scope is created by the graph`() {
        val viewModel = createAppGraph().counterViewModel

        assertEquals(CounterState(), viewModel.state.value)
    }

    @Test
    fun `a handler that shares its name with one of another screen gets its own dependency`() = runTest {
        val router = EventRouter<NotesEvent, NotesState, NotesEffect>(
            scope = backgroundScope,
            initialState = NotesState(),
            handlers = createAppGraph().notes.handlers,
        )

        router.onEvent(NotesEvent.OnOpened)
        testScheduler.runCurrent()
        router.onEvent(NotesEvent.OnNoteAdd(note = "eggs"))
        testScheduler.runCurrent()

        assertEquals(listOf("milk", "bread", "eggs"), router.state.value.notes)
    }
}
