package io.github.mradzinski.eventus.sample.fake

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig

/** A logger that writes nowhere, for tests that only need one to exist. */
fun silentLogger(): Logger = Logger(StaticConfig(logWriterList = emptyList()))
