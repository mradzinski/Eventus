package io.github.mradzinski.eventus.sample

import io.github.mradzinski.eventus.sample.di.sampleModules
import org.koin.core.context.startKoin

/** Starts dependency injection. Every launcher calls it once, before it shows [App]. */
fun startSample() {
    startKoin { modules(sampleModules) }
}
