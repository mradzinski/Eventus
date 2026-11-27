package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** What the app itself gives to the graph. Everything else comes from the modules it depends on. */
@ContributesTo(AppScope::class)
@BindingContainer
internal object AppBindings {

    /**
     * The app's logger: one configuration that never changes, writing where [appLogWriter] says for each
     * platform. Every view model takes it and gives it to the log of
     * its router, with a tag of its own.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun logger(): Logger = Logger(
        config = StaticConfig(logWriterList = listOf(appLogWriter())),
        tag = "EventusSample",
    )
}
