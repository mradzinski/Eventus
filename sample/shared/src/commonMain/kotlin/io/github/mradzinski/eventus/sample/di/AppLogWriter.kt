package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.LogWriter

/**
 * Where the app's log lines go on each platform: the place a developer looks while running the app.
 * That is Logcat on Android and the terminal on desktop. On iOS it is the app's console output, which
 * both Xcode and Android Studio show.
 */
internal expect fun appLogWriter(): LogWriter
