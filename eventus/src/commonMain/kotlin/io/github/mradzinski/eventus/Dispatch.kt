package io.github.mradzinski.eventus

/**
 * What an [EventRouter] does with an event: the answer of a [DispatchStrategy].
 *
 * [Replace] and [WaitFor] name events from the list the strategy was given. The router finds their runs
 * by equality, so naming an event names every active run of an event equal to it. An event that is no
 * longer active is skipped.
 */
public sealed interface Dispatch {

    /** Run the event now, next to whatever else is active. */
    public data object Run : Dispatch

    /** Do not run the event. */
    public data object Ignore : Dispatch

    /**
     * Cancel the runs of [events], then run the event. A run that was still waiting is cancelled before
     * it starts. With no events it is the same as [Run].
     */
    public class Replace(public val events: List<UiEvent>) : Dispatch

    /**
     * Run the event once the runs of [events] have ended, whether they ended well, failed or were
     * cancelled. With no events it is the same as [Run].
     */
    public class WaitFor(public val events: List<UiEvent>) : Dispatch
}
