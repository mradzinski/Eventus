package io.github.mradzinski.eventus.sample.ui

import androidx.compose.runtime.Composable
import io.github.mradzinski.eventus.sample.domain.OfflineException
import io.github.mradzinski.eventus.sample.domain.TaskNotFoundException
import io.github.mradzinski.eventus.sample.resources.Res
import io.github.mradzinski.eventus.sample.resources.failure_not_found
import io.github.mradzinski.eventus.sample.resources.failure_offline
import io.github.mradzinski.eventus.sample.resources.failure_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Why something the user asked for did not happen, in terms the UI can explain. State and effects carry
 * this instead of an exception or its message, so no error text from a lower layer reaches the screen.
 */
enum class FailureReason {

    Offline,
    NotFound,
    Unknown,
}

/** Turns an error into the reason the UI shows. This is the one place where exceptions are read. */
fun Throwable.toFailureReason(): FailureReason = when (this) {
    is OfflineException -> FailureReason.Offline
    is TaskNotFoundException -> FailureReason.NotFound
    else -> FailureReason.Unknown
}

/** The text that explains [reason] to the user. */
@Composable
fun failureText(reason: FailureReason): String = stringResource(reason.textResource())

/** The same text as [failureText], for code that is not composable, such as an effect collector. */
fun FailureReason.textResource(): StringResource = when (this) {
    FailureReason.Offline -> Res.string.failure_offline
    FailureReason.NotFound -> Res.string.failure_not_found
    FailureReason.Unknown -> Res.string.failure_unknown
}
