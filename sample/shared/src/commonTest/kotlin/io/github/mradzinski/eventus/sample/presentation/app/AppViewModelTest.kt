package io.github.mradzinski.eventus.sample.presentation.app

import io.github.mradzinski.eventus.sample.fake.FakeConnectivity
import io.github.mradzinski.eventus.sample.fake.silentLogger
import io.github.mradzinski.eventus.sample.presentation.app.handlers.OnAppStartedHandler
import io.github.mradzinski.eventus.sample.presentation.app.handlers.OnOfflineChangeHandler
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The view model as the UI uses it: events in, state out, with the real handlers behind it. */
class AppViewModelTest {

    private val connectivity = FakeConnectivity(online = false)

    @Test
    fun `it shows whether the app is offline without being asked`() = runTest {
        val viewModel = viewModel()

        testScheduler.runCurrent()

        viewModel.state.value.offline shouldBe true
    }

    @Test
    fun `it keeps following the setting for as long as it lives`() = runTest {
        val viewModel = viewModel()

        testScheduler.runCurrent()
        viewModel.onEvent(AppEvent.OnOfflineChange(offline = false))
        testScheduler.runCurrent()

        viewModel.state.value.offline shouldBe false
    }

    private fun TestScope.viewModel(): AppViewModel = AppViewModel(
        handlers = listOf(
            OnAppStartedHandler(connectivity),
            OnOfflineChangeHandler(connectivity),
        ),
        logger = silentLogger(),
        scope = backgroundScope,
    )
}
