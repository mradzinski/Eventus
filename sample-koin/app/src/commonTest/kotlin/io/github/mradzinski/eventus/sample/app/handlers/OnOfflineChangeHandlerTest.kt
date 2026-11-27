package io.github.mradzinski.eventus.sample.app.handlers

import io.github.mradzinski.eventus.sample.app.AppEvent.OnOfflineChange
import io.github.mradzinski.eventus.sample.app.AppUiState
import io.github.mradzinski.eventus.sample.testing.FakeConnectivity
import io.github.mradzinski.eventus.test.runEventHandlerTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class OnOfflineChangeHandlerTest {

    private val connectivity = FakeConnectivity()

    @Test
    fun `it takes the app offline and back online`() = runEventHandlerTest(
        handler = OnOfflineChangeHandler(connectivity),
        initialState = AppUiState(),
    ) {
        dispatch(OnOfflineChange(offline = true))

        connectivity.online.value shouldBe false

        dispatch(OnOfflineChange(offline = false))

        connectivity.online.value shouldBe true
    }
}
