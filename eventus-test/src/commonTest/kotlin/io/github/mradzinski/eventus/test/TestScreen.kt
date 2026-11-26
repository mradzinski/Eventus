package io.github.mradzinski.eventus.test

import io.github.mradzinski.eventus.DispatchStrategy
import io.github.mradzinski.eventus.ErrorHandler
import io.github.mradzinski.eventus.EventHandler
import io.github.mradzinski.eventus.ScreenStore
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.UiState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

// A small screen for the tests of this module: a list that can be refreshed, searched and edited.

internal data class TestState(
    val loading: Boolean = false,
    val items: List<String> = emptyList(),
    val query: String = "",
    val message: String? = null,
) : UiState

internal sealed interface TestEffect : UiEffect {

    data object Saved : TestEffect

    data class Opened(val id: String) : TestEffect
}

internal sealed interface TestEvent : UiEvent {

    data object OnRefresh : TestEvent

    data object OnScreenOpened : TestEvent

    data class OnQueryChange(val query: String) : TestEvent

    data class OnItemClick(val id: String) : TestEvent

    /** The actions that change the list on the server. Only one of them runs at a time. */
    sealed interface OnChangeClick : TestEvent

    data object OnSaveClick : OnChangeClick

    data object OnDeleteClick : OnChangeClick
}

internal typealias TestStore = ScreenStore<TestState, TestEffect>

internal interface TestHandler<E : TestEvent> : EventHandler<E, TestState, TestEffect>

internal class FakeRepository {

    val items = MutableStateFlow(listOf("a"))
    val searched = mutableListOf<String>()

    var failure: Exception? = null
    var refreshes = 0

    fun refresh() {
        refreshes++

        failure?.let { throw it }
    }

    /** Work on a thread of its own: a dispatcher written into the code, which no test clock controls. */
    suspend fun loadElsewhere(): List<String> = withContext(Dispatchers.Default) {
        List(SORTED_ELSEWHERE) { it }
            .shuffled()
            .sorted()
            .take(2)
            .map { "item $it" }
    }

    fun watchElsewhere(): Flow<List<String>> = flow { emit(loadElsewhere()) }.flowOn(Dispatchers.Default)

    private companion object {

        /** Enough numbers to sort that a test which does not wait reads the state before they are sorted. */
        const val SORTED_ELSEWHERE = 20_000
    }
}

internal class OnRefreshHandler(private val repository: FakeRepository) : TestHandler<TestEvent.OnRefresh> {

    override val eventType = TestEvent.OnRefresh::class
    override val strategy = DispatchStrategy.DropIfAlreadyRunning<TestEvent.OnRefresh>()

    override suspend fun handle(event: TestEvent.OnRefresh, store: TestStore) {
        store.update { it.copy(loading = true) }

        try {
            repository.refresh()
        } finally {
            store.update { it.copy(loading = false) }
        }
    }
}

/** Never returns: it keeps following the repository's items. */
internal class OnScreenOpenedHandler(private val repository: FakeRepository) : TestHandler<TestEvent.OnScreenOpened> {

    override val eventType = TestEvent.OnScreenOpened::class

    override suspend fun handle(event: TestEvent.OnScreenOpened, store: TestStore) {
        repository.items.collect { items -> store.update { it.copy(items = items) } }
    }
}

/** Shows the text at once, and searches once the user has stopped typing. */
internal class OnQueryChangeHandler(private val repository: FakeRepository) : TestHandler<TestEvent.OnQueryChange> {

    override val eventType = TestEvent.OnQueryChange::class
    override val strategy = DispatchStrategy.Latest<TestEvent.OnQueryChange>()

    override suspend fun handle(event: TestEvent.OnQueryChange, store: TestStore) {
        store.update { it.copy(query = event.query, loading = true) }

        try {
            delay(SEARCH_DELAY_MILLIS.milliseconds)
            repository.searched += event.query
        } finally {
            store.update { it.copy(loading = false) }
        }
    }

    companion object {

        const val SEARCH_DELAY_MILLIS = 500
    }
}

internal class OnItemClickHandler : TestHandler<TestEvent.OnItemClick> {

    override val eventType = TestEvent.OnItemClick::class

    override suspend fun handle(event: TestEvent.OnItemClick, store: TestStore) {
        store.emit(TestEffect.Opened(event.id))
    }
}

internal class FailingItemClickHandler : TestHandler<TestEvent.OnItemClick> {

    override val eventType = TestEvent.OnItemClick::class

    override suspend fun handle(event: TestEvent.OnItemClick, store: TestStore): Unit = error("no ${event.id}")
}

internal val oneChangeAtATime = DispatchStrategy.DropIfAlreadyRunning<TestEvent.OnChangeClick>()

internal class OnSaveClickHandler : TestHandler<TestEvent.OnSaveClick> {

    override val eventType = TestEvent.OnSaveClick::class
    override val strategy = oneChangeAtATime

    override suspend fun handle(event: TestEvent.OnSaveClick, store: TestStore) {
        delay(SAVE_MILLIS.milliseconds)
        store.emit(TestEffect.Saved)
    }

    companion object {

        const val SAVE_MILLIS = 100
    }
}

internal class OnDeleteClickHandler : TestHandler<TestEvent.OnDeleteClick> {

    override val eventType = TestEvent.OnDeleteClick::class
    override val strategy = oneChangeAtATime

    override suspend fun handle(event: TestEvent.OnDeleteClick, store: TestStore) {
        store.update { it.copy(items = emptyList()) }
    }
}

/** Shows "saved" at once and puts the text back when the save fails, which it always does. */
internal class OptimisticSaveHandler : TestHandler<TestEvent.OnSaveClick> {

    override val eventType = TestEvent.OnSaveClick::class

    override suspend fun handle(event: TestEvent.OnSaveClick, store: TestStore) {
        store.updateOptimistically(
            stateUpdate = { it.copy(query = "saved") },
            stateIfFailed = { it.copy(query = "") },
        ) {
            error("the server said no")
        }
    }
}

internal class LoadElsewhereHandler(private val repository: FakeRepository) : TestHandler<TestEvent.OnRefresh> {

    override val eventType = TestEvent.OnRefresh::class

    override suspend fun handle(event: TestEvent.OnRefresh, store: TestStore) {
        val items = repository.loadElsewhere()

        store.update { it.copy(items = items) }
        store.emit(TestEffect.Saved)
    }
}

internal class FailElsewhereHandler : TestHandler<TestEvent.OnSaveClick> {

    override val eventType = TestEvent.OnSaveClick::class

    override suspend fun handle(event: TestEvent.OnSaveClick, store: TestStore): Unit =
        withContext(Dispatchers.Default) { error("the disk is full") }
}

/** Never returns: it follows items that are loaded on another thread. */
internal class WatchElsewhereHandler(private val repository: FakeRepository) : TestHandler<TestEvent.OnScreenOpened> {

    override val eventType = TestEvent.OnScreenOpened::class

    override suspend fun handle(event: TestEvent.OnScreenOpened, store: TestStore) {
        repository
            .watchElsewhere()
            .collect { items -> store.update { it.copy(items = items) } }

        awaitCancellation()
    }
}

/** Never returns, and records that it was stopped. */
internal class NeverReturnsHandler : TestHandler<TestEvent.OnScreenOpened> {

    var started = false
    var stopped = false

    override val eventType = TestEvent.OnScreenOpened::class

    override suspend fun handle(event: TestEvent.OnScreenOpened, store: TestStore) {
        started = true

        try {
            awaitCancellation()
        } finally {
            stopped = true
        }
    }
}

internal class MessageErrorHandler : ErrorHandler<TestEvent, TestState, TestEffect> {

    override suspend fun handle(
        event: TestEvent,
        error: Throwable,
        store: TestStore,
    ) {
        store.update { it.copy(message = error.message) }
        store.emit(TestEffect.Opened("error"))
    }
}
