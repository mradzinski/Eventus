package io.github.mradzinski.eventus.ksp

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RouterCheckTest {

    /** A feature with a list, an editor and a view model above both, each with its own state. */
    private fun feature(featureEvents: String, routed: String): String {
        val back = if ("Feature" in featureEvents) "TasksEvent.Feature.OnBackClick" else "TasksEvent.OnBackClick"

        return """
            package tasks

            import io.github.mradzinski.eventus.EventHandler
            import io.github.mradzinski.eventus.EventRouter
            import io.github.mradzinski.eventus.ScreenStore
            import io.github.mradzinski.eventus.UiEffect
            import io.github.mradzinski.eventus.UiEvent
            import io.github.mradzinski.eventus.UiState

            sealed interface TasksEvent : UiEvent {
                sealed interface List : TasksEvent { data object OnRefresh : List }
                sealed interface Editor : TasksEvent { data object OnSaveClick : Editor }
                $featureEvents
            }

            data object ListState : UiState
            data object EditorState : UiState
            data object TasksState : UiState
            sealed interface TasksEffect : UiEffect

            class OnRefreshHandler : EventHandler<TasksEvent.List.OnRefresh, ListState, TasksEffect> {
                override val eventType = TasksEvent.List.OnRefresh::class
                override suspend fun handle(
                    event: TasksEvent.List.OnRefresh,
                    store: ScreenStore<ListState, TasksEffect>,
                ) = Unit
            }

            class OnSaveClickHandler : EventHandler<TasksEvent.Editor.OnSaveClick, EditorState, TasksEffect> {
                override val eventType = TasksEvent.Editor.OnSaveClick::class
                override suspend fun handle(
                    event: TasksEvent.Editor.OnSaveClick,
                    store: ScreenStore<EditorState, TasksEffect>,
                ) = Unit
            }

            class OnBackClickHandler : EventHandler<$back, TasksState, TasksEffect> {
                override val eventType = $back::class
                override suspend fun handle(event: $back, store: ScreenStore<TasksState, TasksEffect>) = Unit
            }

            class ListViewModel(router: EventRouter<TasksEvent.List, ListState, TasksEffect>) :
                EventRouter<TasksEvent.List, ListState, TasksEffect> by router

            class EditorViewModel(router: EventRouter<TasksEvent.Editor, EditorState, TasksEffect>) :
                EventRouter<TasksEvent.Editor, EditorState, TasksEffect> by router

            class FeatureViewModel(router: EventRouter<$routed, TasksState, TasksEffect>) :
                EventRouter<$routed, TasksState, TasksEffect> by router
        """.trimIndent()
    }

    @Test
    fun `a view model that routes a type whose events other screens handle fails the build`() {
        val source = feature(featureEvents = "data object OnBackClick : TasksEvent", routed = "TasksEvent")

        val compiled = compile(source)

        compiled.succeeded shouldBe false
        compiled.problems shouldContainExactly listOf(
            "tasks.FeatureViewModel routes every tasks.TasksEvent, but it can never handle " +
                "tasks.TasksEvent.Editor.OnSaveClick, tasks.TasksEvent.List.OnRefresh: the handlers of those " +
                "events are written for another state or other effects. Give the events that FeatureViewModel " +
                "handles a sealed type of their own inside TasksEvent, and route that type.",
        )
    }

    @Test
    fun `a view model that routes a sealed type of its own events compiles`() {
        val source = feature(
            featureEvents = "sealed interface Feature : TasksEvent { data object OnBackClick : Feature }",
            routed = "TasksEvent.Feature",
        )

        val compiled = compile(source)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a view model that routes one event class compiles`() {
        val source = feature(featureEvents = "data object OnBackClick : TasksEvent", routed = "TasksEvent.OnBackClick")

        val compiled = compile(source)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a view model that routes the whole type of a screen with every handler compiles`() {
        val viewModel = """
            class ListViewModel(router: io.github.mradzinski.eventus.EventRouter<ListEvent, ListState, ListEffect>) :
                io.github.mradzinski.eventus.EventRouter<ListEvent, ListState, ListEffect> by router
        """.trimIndent()

        val compiled = compile(listScreen, allListHandlers, viewModel)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `an event with no handler at all is reported once, as missing`() {
        val source = feature(featureEvents = "data object OnBackClick : TasksEvent", routed = "TasksEvent.OnBackClick")
            .replace("data object OnRefresh : List", "data object OnRefresh : List\ndata object OnMore : List")

        val compiled = compile(source)

        compiled.problems shouldContainExactly listOf(
            "No handler for tasks.TasksEvent.List.OnMore. Add a class that implements EventHandler for it.",
        )
    }

    @Test
    fun `a router whose types are parameters of its class is not checked`() {
        val base = """
            abstract class BaseViewModel<E : UiEvent, S : UiState>(router: EventRouter<E, S, TasksEffect>) :
                EventRouter<E, S, TasksEffect> by router

            class GenericViewModel<E : UiEvent>(router: EventRouter<E, TasksState, TasksEffect>) :
                BaseViewModel<E, TasksState>(router)

            class AnyStateViewModel<S : UiState>(router: EventRouter<TasksEvent.Feature, S, TasksEffect>) :
                BaseViewModel<TasksEvent.Feature, S>(router)
        """.trimIndent()

        val source = feature(
            featureEvents = "sealed interface Feature : TasksEvent { data object OnBackClick : Feature }",
            routed = "TasksEvent.Feature",
        )

        val compiled = compile(source + "\n\n" + base)

        compiled.problems.shouldBeEmpty()
        compiled.succeeded shouldBe true
    }

    @Test
    fun `a router of an abstract type that is not sealed is not checked`() {
        val open = """
            interface OpenEvent : UiEvent

            class OpenViewModel(router: EventRouter<OpenEvent, TasksState, TasksEffect>) :
                EventRouter<OpenEvent, TasksState, TasksEffect> by router
        """.trimIndent()

        val source = feature(
            featureEvents = "sealed interface Feature : TasksEvent { data object OnBackClick : Feature }",
            routed = "TasksEvent.Feature",
        )

        compile(source + "\n\n" + open).problems.shouldBeEmpty()
    }
}
