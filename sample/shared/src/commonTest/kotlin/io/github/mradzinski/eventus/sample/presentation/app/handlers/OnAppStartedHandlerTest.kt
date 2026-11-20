package io.github.mradzinski.eventus.sample.presentation.app.handlers

import io.github.mradzinski.eventus.sample.fake.FakeConnectivity
import io.github.mradzinski.eventus.sample.presentation.app.AppEvent.OnAppStarted
import io.github.mradzinski.eventus.sample.presentation.app.AppStore
import io.github.mradzinski.eventus.sample.presentation.app.AppUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class OnAppStartedHandlerTest {

    private val connectivity = FakeConnectivity()
    private val store = AppStore(AppUiState())
    private val handler = OnAppStartedHandler(connectivity)

    @Test
    fun `it shows whether the app is offline and follows each change`() = runTest {
        backgroundScope.launch { handler.handle(OnAppStarted, store) }
        testScheduler.runCurrent()

        store.current.offline shouldBe false

        connectivity.setOnline(false)
        testScheduler.runCurrent()

        store.current.offline shouldBe true
    }
}
