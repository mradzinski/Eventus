package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.LogWriter

/**
 * Kermit's own choice for iOS writes to the system log, which Xcode shows and Android Studio does not.
 * This one prints, so the lines appear in the run console of both.
 */
internal actual fun appLogWriter(): LogWriter = CommonWriter()
