package io.github.mradzinski.eventus

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/** Events sent from several threads at once, with handlers running on a thread pool. */
class ThreadSafetyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cancelScope() = scope.cancel()

    @Test
    fun `concurrent events from many threads are each handled once`() {
        val handled = CountDownLatch(THREADS * EVENTS_PER_THREAD)
        val router = router(CountingHandler(DispatchStrategy.Concurrent, handled))

        sendFromEveryThread(router)

        handled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        router.state.value.log shouldContainExactlyInAnyOrder allIds()
    }

    @Test
    fun `sequential events keep the order each thread sent them in`() {
        val handled = CountDownLatch(THREADS * EVENTS_PER_THREAD)
        val router = router(CountingHandler(DispatchStrategy.Sequential<Run>(), handled))

        sendFromEveryThread(router)

        handled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

        val log = router.state.value.log

        log shouldContainExactlyInAnyOrder allIds()
        repeat(THREADS) { thread ->
            log.filter { it.startsWith("$thread-") } shouldContainExactly List(EVENTS_PER_THREAD) { "$thread-$it" }
        }
    }

    @Test
    fun `drop if already running never runs two events of one type at the same time`() {
        val handler = OverlapHandler()
        val router = router(handler)

        sendFromEveryThread(router)
        awaitEveryRun()

        handler.mostAtOnce.get() shouldBe 1
    }

    private fun router(handler: TestHandler): EventRouter<TestEvent, TestState, TestEffect> = EventRouter(
        scope = scope,
        initialState = TestState(),
        handlers = listOf(handler),
    )

    /** Starts every thread, releases them together and waits until each has sent all of its events. */
    private fun sendFromEveryThread(router: EventRouter<TestEvent, TestState, TestEffect>) {
        val ready = CountDownLatch(THREADS)
        val go = CountDownLatch(1)

        val threads = List(THREADS) { thread ->
            thread {
                ready.countDown()
                go.await()
                repeat(EVENTS_PER_THREAD) { router.onEvent(Run("$thread-$it")) }
            }
        }

        ready.await()
        go.countDown()
        threads.forEach { it.join() }
    }

    /**
     * Waits until every run the routers started has ended. Each router keeps its runs under a job of its
     * own inside [scope], so the runs are the children of the children of the scope's job. By the time
     * this is called every event has been sent, so no new run can appear while it waits.
     */
    private fun awaitEveryRun() = runBlocking {
        withTimeout(TIMEOUT_SECONDS.seconds) {
            scope.coroutineContext.job.children
                .flatMap { it.children }
                .forEach { it.join() }
        }
    }

    private fun allIds(): List<String> = List(THREADS) { thread -> List(EVENTS_PER_THREAD) { "$thread-$it" } }.flatten()

    /** Logs the id of each event and counts it off [handled]. */
    private class CountingHandler(override val strategy: DispatchStrategy<Run>, private val handled: CountDownLatch) :
        EventHandler<Run, TestState, TestEffect> {

        override val eventType = Run::class

        override suspend fun handle(event: Run, store: TestStore) {
            store.log(event.id)
            handled.countDown()
        }
    }

    /** Records the highest number of runs that were active at the same moment. */
    private class OverlapHandler : EventHandler<Run, TestState, TestEffect> {

        val mostAtOnce = AtomicInteger(0)

        private val active = AtomicInteger(0)

        override val eventType = Run::class
        override val strategy = DispatchStrategy.DropIfAlreadyRunning<Run>()

        override suspend fun handle(event: Run, store: TestStore) {
            val now = active.incrementAndGet()

            mostAtOnce.accumulateAndGet(now, ::maxOf)
            yield()
            active.decrementAndGet()
        }
    }

    private companion object {

        const val THREADS = 8
        const val EVENTS_PER_THREAD = 500
        const val TIMEOUT_SECONDS = 30L
    }
}
