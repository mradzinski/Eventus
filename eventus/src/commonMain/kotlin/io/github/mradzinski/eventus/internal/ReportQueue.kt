package io.github.mradzinski.eventus.internal

import io.github.mradzinski.eventus.RouterObserver
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.UiEvent
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Hands a router's reports to its observer in the order they were queued, one at a time, whatever
 * threads they come from.
 *
 * A report is queued while the router's lock is held, which is what fixes its place in the order. The
 * thread that queues one then delivers everything that is waiting, unless another thread is already
 * delivering: that one will find the new report in the queue. No lock is held while the observer runs,
 * so an observer that is slow, or that sends an event itself, cannot block or deadlock the router.
 *
 * The queue shares the router's lock and is taken a whole batch at a time, by swapping two lists, so
 * delivering in order costs about as much as calling the observer directly. A lock-free queue was
 * evaluated as well and not used: with this little left to gain it is not much more efficient, and it
 * adds risk on five platforms.
 */
internal class ReportQueue<E : UiEvent>(
    private val observer: RouterObserver<E>,
    private val lock: SynchronizedObject,
    private val onFailure: (trace: RouterTrace<E>, error: Exception) -> Unit,
) {

    /** Reports waiting to be delivered, oldest first. Guarded by [lock]. */
    private var queued = ArrayList<RouterTrace<E>>()

    /** An empty list to swap with [queued], so that taking a batch allocates nothing. Guarded by [lock]. */
    private var spare = ArrayList<RouterTrace<E>>()

    /** Whether a thread is delivering. Guarded by [lock]. */
    private var delivering = false

    /**
     * Queues [trace]. Call it with the lock held.
     *
     * @return The reports for the caller to [deliver] once it has released the lock, or null when
     * another thread is delivering and will take this one too.
     */
    fun queue(trace: RouterTrace<E>): ArrayList<RouterTrace<E>>? {
        queued += trace

        if (delivering) return null

        delivering = true

        return take()
    }

    /** Hands [batch] to the observer, and then whatever was queued meanwhile. Call it without the lock. */
    fun deliver(batch: ArrayList<RouterTrace<E>>?) {
        var current = batch ?: return

        while (true) {
            current.forEach { observe(it) }
            current.clear()

            val emptied = current

            current = synchronized(lock) { next(emptied) } ?: return
        }
    }

    /** The next batch, or null when nothing is queued and this thread stops delivering. */
    private fun next(emptied: ArrayList<RouterTrace<E>>): ArrayList<RouterTrace<E>>? {
        spare = emptied

        if (queued.isEmpty()) {
            delivering = false

            return null
        }

        return take()
    }

    private fun take(): ArrayList<RouterTrace<E>> {
        val batch = queued

        queued = spare

        return batch
    }

    private fun observe(trace: RouterTrace<E>) {
        // An observer that throws is a mistake in the app's code. It must not stop the reports after it.
        @Suppress("TooGenericExceptionCaught")
        try {
            observer.observe(trace)
        } catch (error: Exception) {
            onFailure(trace, error)
        }
    }
}
