package io.github.mradzinski.eventus

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job

class RouterScopeTest {

    @Test
    fun `it is active until it is cancelled`() {
        val scope = routerScope()

        scope.isActive shouldBe true

        scope.cancel()

        scope.isActive shouldBe false
    }

    @Test
    fun `a child that fails does not cancel the scope`() {
        val scope = routerScope()
        val child = Job(parent = scope.coroutineContext.job)

        child.completeExceptionally(IllegalStateException("boom"))

        scope.isActive shouldBe true

        scope.cancel()
    }

    @Test
    fun `each call creates a scope of its own`() {
        val first = routerScope()
        val second = routerScope()

        first.cancel()

        second.isActive shouldBe true

        second.cancel()
    }
}
