package io.github.mradzinski.eventus.integration.metro.notes

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import io.github.mradzinski.eventus.integration.metro.NoteRepository
import io.github.mradzinski.eventus.metro.ContributesEventHandlers

internal data class NotesState(val notes: List<String> = emptyList()) : UiState

internal sealed interface NotesEffect : UiEffect

internal sealed interface NotesEvent : UiEvent {

    data object OnOpened : NotesEvent

    data class OnNoteAdd(val note: String) : NotesEvent
}

internal typealias NotesStore = ScreenStore<NotesState, NotesEffect>

@ContributesEventHandlers(AppScope::class)
internal interface NotesHandler<E : NotesEvent> : EventHandler<E, NotesState, NotesEffect>

/** The counter screen has a handler of this name too. Each lands in the set of its own screen. */
internal class OnOpenedHandler(private val repository: NoteRepository) : NotesHandler<NotesEvent.OnOpened> {

    override val eventType = NotesEvent.OnOpened::class

    override suspend fun handle(event: NotesEvent.OnOpened, store: NotesStore) {
        store.update { it.copy(notes = repository.notes()) }
    }
}

internal class OnNoteAddHandler : NotesHandler<NotesEvent.OnNoteAdd> {

    override val eventType = NotesEvent.OnNoteAdd::class

    override suspend fun handle(event: NotesEvent.OnNoteAdd, store: NotesStore) {
        store.update { it.copy(notes = it.notes + event.note) }
    }
}

@Inject
internal class NotesScreen(val handlers: Set<NotesHandler<*>>)
