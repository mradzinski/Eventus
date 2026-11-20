package io.github.mradzinski.eventus.sample.presentation.app.handlers

import io.github.mradzinski.eventus.sample.fake.FakeConnectivity
import io.github.mradzinski.eventus.sample.presentation.app.AppEvent.OnOfflineChange
import io.github.mradzinski.eventus.sample.presentation.app.AppStore
import io.github.mradzinski.eventus.sample.presentation.app.AppUiState
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OnOfflineChangeHandlerTest {

    private val connectivity = FakeConnectivity()
    private val store = AppStore(AppUiState())
    private val handler = OnOfflineChangeHandler(connectivity)

    @Test
    fun `it takes the app offline and back online`() = runTest {
        handler.handle(OnOfflineChange(offline = true), store)

        connectivity.online.value shouldBe false

        handler.handle(OnOfflineChange(offline = false), store)

        connectivity.online.value shouldBe true
    }
}
