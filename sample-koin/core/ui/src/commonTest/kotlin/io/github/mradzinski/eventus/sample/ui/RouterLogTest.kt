package io.github.mradzinski.eventus.sample.ui

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import io.github.mradzinski.eventus.Dispatch
import io.github.mradzinski.eventus.RouterTrace
import io.github.mradzinski.eventus.UiEffect
import io.github.mradzinski.eventus.UiEvent
import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test

class RouterLogTest {

    private val written = RecordingWriter()
    private val log = RouterLog(Logger(StaticConfig(logWriterList = listOf(written))), tag = "TaskList")

    @Test
    fun `a report is logged as its sentence under the screen's tag`() {
        log.observe(
            RouterTrace.Dispatched(
                event = OnRefresh,
                number = 3,
                dispatch = Dispatch.Ignore,
                replacedOrAwaited = emptyList(),
            ),
        )

        written.lines shouldContainExactly listOf(
            Line(Severity.Debug, tag = "TaskList", message = "#3 OnRefresh is ignored", throwable = null),
        )
    }

    @Test
    fun `an effect is logged with the event it came from`() {
        val effect = OpenEditor(taskId = 7)

        log.observe(RouterTrace.EffectEmitted(OnRefresh, number = 4, effect = effect))

        written.lines shouldContainExactly listOf(
            Line(Severity.Debug, tag = "TaskList", message = "#4 OnRefresh emitted $effect", throwable = null),
        )
    }

    @Test
    fun `a handler that failed is logged as an error with its exception`() {
        val error = OfflineException()

        log.observe(RouterTrace.Failed(OnRefresh, number = 2, error = error))

        written.lines shouldContainExactly listOf(
            Line(Severity.Error, tag = "TaskList", message = "#2 OnRefresh failed: $error", throwable = error),
        )
    }

    // This module knows no screen, so the test has an event and an effect of its own.
    private data object OnRefresh : UiEvent

    private data class OpenEditor(val taskId: Long) : UiEffect

    private data class Line(
        val severity: Severity,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )

    /** Keeps what it is asked to write. */
    private class RecordingWriter : LogWriter() {

        val lines = mutableListOf<Line>()

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            lines += Line(severity, tag, message, throwable)
        }
    }
}
