package io.github.mradzinski.eventus.sample.presentation.app.handlers

import io.github.mradzinski.eventus.sample.fake.FakeConnectivity
import io.github.mradzinski.eventus.sample.presentation.app.AppEvent.OnAppStarted
import io.github.mradzinski.eventus.sample.presentation.app.AppUiState
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnAppStartedHandlerTest {

    private val connectivity = FakeConnectivity()

    @Test
    fun `it shows whether the app is offline and follows each change`() = runEventHandlerTest(
        handler = OnAppStartedHandler(connectivity),
        initialState = AppUiState(),
    ) {
        dispatch(OnAppStarted)

        state.offline shouldBe false

        connectivity.setOnline(false)

        state.offline shouldBe true
    }
}
