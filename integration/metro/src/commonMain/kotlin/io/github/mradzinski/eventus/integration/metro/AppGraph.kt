package io.github.mradzinski.eventus.integration.metro

import io.github.mradzinski.eventus.integration.metro.counter.CounterScreen
import io.github.mradzinski.eventus.integration.metro.counter.CounterViewModel
import io.github.mradzinski.eventus.integration.metro.notes.NotesScreen

/**
 * What the app takes from its Metro graph. The graph itself is declared once per platform, because the
 * processor writes the handlers' bindings into each target, where a graph in common code cannot see them.
 */
internal interface AppGraph {

    val counter: CounterScreen

    val notes: NotesScreen

    val counterViewModel: CounterViewModel
}

internal expect fun createAppGraph(): AppGraph
