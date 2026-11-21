package io.github.mradzinski.eventus.ksp

// The processor finds Eventus and Koin types by name, so it needs neither library to run.
internal const val EVENT_HANDLER = "io.github.mradzinski.eventus.EventHandler"
internal const val ERROR_HANDLER = "io.github.mradzinski.eventus.ErrorHandler"
internal const val UI_EVENT = "io.github.mradzinski.eventus.UiEvent"
internal const val KOIN_MODULE_ANNOTATION = "io.github.mradzinski.eventus.koin.EventusKoinModule"
internal const val KOIN_MODULE = "org.koin.core.module.Module"
