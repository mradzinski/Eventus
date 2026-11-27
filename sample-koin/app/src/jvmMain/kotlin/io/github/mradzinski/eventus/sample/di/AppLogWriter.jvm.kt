package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.platformLogWriter

internal actual fun appLogWriter(): LogWriter = platformLogWriter()
