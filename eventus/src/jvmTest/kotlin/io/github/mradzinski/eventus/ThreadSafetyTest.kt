package io.github.mradzinski.eventus

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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

    @Test
    fun `reports from many threads arrive one at a time and in order`() {
        readyMadeStrategies.forEach { strategy ->
            val reports = ArrayList<RouterTrace<TestEvent>>()
            val observing = AtomicInteger(0)
            val overlaps = AtomicInteger(0)
            val decisions = AtomicInteger(0)
            val unfinished = AtomicInteger(0)
            val everyReportArrived = CountDownLatch(1)

            // The list is not safe to share on purpose: the router must never call this twice at once.
            val observer = RouterObserver<TestEvent> { trace ->
                if (observing.incrementAndGet() != 1) overlaps.incrementAndGet()

                reports += trace
                observing.decrementAndGet()

                // A run's last report can arrive after its job has ended, on another thread. So the test
                // waits for the reports themselves: one decision for each event, and an end for each event
                // that was not ignored. The list is not touched after this point.
                when (trace) {
                    is RouterTrace.Dispatched -> {
                        if (trace.dispatch !is Dispatch.Ignore) unfinished.incrementAndGet()

                        decisions.incrementAndGet()
                    }

                    is RouterTrace.Started -> Unit

                    is RouterTrace.EffectEmitted -> Unit

                    else -> unfinished.decrementAndGet()
                }

                if (decisions.get() == THREADS * EVENTS_PER_THREAD && unfinished.get() == 0) {
                    everyReportArrived.countDown()
                }
            }

            val router = router(CountingHandler(strategy, CountDownLatch(0)), observer)

            sendFromEveryThread(router)
            awaitEveryRun()
            everyReportArrived.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

            val dispatched = reports.filterIsInstance<RouterTrace.Dispatched<TestEvent>>()

            overlaps.get() shouldBe 0
            dispatched.map { it.number } shouldContainExactly (1L..THREADS * EVENTS_PER_THREAD).toList()
            reports.groupBy { it.number }.values.forEach { ofOneEvent -> ofOneEvent.shouldBeInOrder() }
            dispatched.forEach { it.shouldComeBeforeTheRunsItReplaces(reports) }
            router.shouldDeliver(reports.filterIsInstance<RouterTrace.EffectEmitted<TestEvent>>().map { it.effect })
        }
    }

    private fun router(
        handler: TestHandler,
        observer: RouterObserver<TestEvent>? = null,
    ): EventRouter<TestEvent, TestState, TestEffect> = EventRouter(
        scope = scope,
        initialState = TestState(),
        handlers = listOf(handler),
        observer = observer,
    )

    /**
     * Dispatched first, then started unless it never ran, then the one effect of a run that started,
     * then one end unless it was ignored.
     */
    private fun List<RouterTrace<TestEvent>>.shouldBeInOrder() {
        val ignored = (first() as RouterTrace.Dispatched).dispatch is Dispatch.Ignore
        val started = indexOfFirst { it is RouterTrace.Started }
        val emitted = indexOfFirst { it is RouterTrace.EffectEmitted }

        val ends = filter {
            it !is RouterTrace.Dispatched && it !is RouterTrace.Started && it !is RouterTrace.EffectEmitted
        }

        first().shouldBeInstanceOf<RouterTrace.Dispatched<TestEvent>>()
        count { it is RouterTrace.Dispatched } shouldBe 1
        count { it is RouterTrace.EffectEmitted } shouldBe count { it is RouterTrace.Started }
        ends.size shouldBe if (ignored) 0 else 1

        if (started >= 0) (started < emitted) shouldBe true
        if (!ignored) last() shouldBe ends.single()
    }

    /** The effects the UI gets are the ones that were reported: none is taken by a report, none is added. */
    private fun EventRouter<TestEvent, TestState, TestEffect>.shouldDeliver(reported: List<UiEffect>) {
        if (reported.isEmpty()) return

        val delivered = runBlocking {
            withTimeout(TIMEOUT_SECONDS.seconds) {
                effects
                    .take(reported.size)
                    .toList()
            }
        }

        val deliveredNames = delivered
            .map { it.toString() }
            .sorted()

        val reportedNames = reported
            .map { it.toString() }
            .sorted()

        deliveredNames shouldContainExactly reportedNames
    }

    private fun RouterTrace.Dispatched<TestEvent>.shouldComeBeforeTheRunsItReplaces(
        reports: List<RouterTrace<TestEvent>>,
    ) {
        val reason = RouterTrace.Cancelled.Reason.ReplacedBy(number)

        val firstReplaced = reports.indexOfFirst { it is RouterTrace.Cancelled && it.reason == reason }

        if (firstReplaced >= 0) (reports.indexOf(this) < firstReplaced) shouldBe true
    }

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

    /** Logs and shows the id of each event and counts it off [handled]. */
    private class CountingHandler(override val strategy: DispatchStrategy<Run>, private val handled: CountDownLatch) :
        EventHandler<Run, TestState, TestEffect> {

        override val eventType = Run::class

        override suspend fun handle(event: Run, store: TestStore) {
            store.log(event.id)
            store.emit(Shown(event.id))
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
