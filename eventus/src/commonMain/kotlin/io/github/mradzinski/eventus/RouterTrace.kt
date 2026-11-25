package io.github.mradzinski.eventus

/**
 * One thing an [EventRouter] did with an event, as its [RouterObserver] is told.
 *
 * Each event gets a [number] when the router decides what to do with it, counting from 1 in the order
 * of those decisions. The reports of one event share its number, so two equal events can be told apart
 * and the start of a run can be paired with its end.
 *
 * Every report prints as a sentence that starts with the number and the event, such as
 * `#3 OnQueryChange(query=cat) replaces #2`.
 */
public sealed interface RouterTrace<out E : UiEvent> {

    /** The event the report is about. */
    public val event: E

    /** The number of [event] among the events of its router, counting from 1. */
    public val number: Long

    /**
     * The strategy of the event's handler answered [dispatch]. It is the first report of every event,
     * and the only one of an event that is ignored.
     *
     * @property replacedOrAwaited The numbers of the events that this one replaces or waits for. Empty for the
     * other answers, and when none of the events the strategy named was still active.
     */
    public data class Dispatched<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val dispatch: Dispatch,
        val replacedOrAwaited: List<Long>,
    ) : RouterTrace<E> {

        override fun toString(): String {
            val others = replacedOrAwaited.joinToString { "#$it" }

            return when {
                dispatch is Dispatch.Ignore -> "${subject()} is ignored"
                dispatch is Dispatch.Replace && others.isNotEmpty() -> "${subject()} replaces $others"
                dispatch is Dispatch.WaitFor && others.isNotEmpty() -> "${subject()} waits for $others"
                else -> "${subject()} runs"
            }
        }
    }

    /** The handler of the event started. An event that waits for others starts once they have ended. */
    public data class Started<out E : UiEvent>(override val event: E, override val number: Long) : RouterTrace<E> {

        override fun toString(): String = "${subject()} started"
    }

    /**
     * The handler of the event emitted [effect], or the screen's [ErrorHandler] did while it handled
     * the failure of that event. The report comes at the moment of the emission, so an effect of the
     * error handler is reported before the failure itself.
     */
    public data class EffectEmitted<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val effect: UiEffect,
    ) : RouterTrace<E> {

        override fun toString(): String = "${subject()} emitted $effect"
    }

    /** The handler of the event returned. */
    public data class Completed<out E : UiEvent>(override val event: E, override val number: Long) : RouterTrace<E> {

        override fun toString(): String = "${subject()} completed"
    }

    /** The handler of the event threw [error]. The screen's [ErrorHandler] is given the same failure. */
    public data class Failed<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val error: Throwable,
    ) : RouterTrace<E> {

        override fun toString(): String = "${subject()} failed: $error"
    }

    /** The run of the event was cancelled, for [reason]. It may never have started. */
    public data class Cancelled<out E : UiEvent>(
        override val event: E,
        override val number: Long,
        val reason: Reason,
    ) : RouterTrace<E> {

        override fun toString(): String = when (reason) {
            is Reason.ReplacedBy -> "${subject()} was replaced by #${reason.number}"
            is Reason.ScopeCancelled -> "${subject()} was cancelled with the scope"
            is Reason.ByHandler -> "${subject()} was cancelled by its own handler"
        }

        /** Why a run was cancelled. */
        public sealed interface Reason {

            /** A strategy answered `Replace` for the event with this [number], naming this run. */
            public data class ReplacedBy(val number: Long) : Reason

            /** The router's scope was cancelled, as when a view model is cleared. */
            public data object ScopeCancelled : Reason

            /** The handler ended by cancellation of its own, such as a timeout that it did not catch. */
            public data object ByHandler : Reason
        }
    }
}

private fun RouterTrace<*>.subject(): String = "#$number $event"
