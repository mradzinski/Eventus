package io.github.mradzinski.eventus.sample.di

import co.touchlab.kermit.Logger
import co.touchlab.kermit.StaticConfig
import io.github.mradzinski.eventus.koin.EventusKoinModule
import io.github.mradzinski.eventus.sample.app.AppHandler
import io.github.mradzinski.eventus.sample.app.AppViewModel
import io.github.mradzinski.eventus.sample.data.dataModule
import io.github.mradzinski.eventus.sample.taskeditor.taskEditorModule
import io.github.mradzinski.eventus.sample.tasklist.taskListModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The app's logger: one configuration that never changes, writing where [appLogWriter] says for each
 * platform. Every view model takes it and gives it to the log of
 * its router, with a tag of its own.
 */
internal val loggingModule: Module = module {
    single {
        Logger(
            config = StaticConfig(logWriterList = listOf(appLogWriter())),
            tag = "EventusSample",
        )
    }
}

/**
 * The event handlers of the app's own router, the one behind the "Work offline" switch. The Eventus
 * processor writes this module on each target, as it does in each feature.
 */
@EventusKoinModule
@Suppress("KotlinNoActualForExpect")
internal expect val handlersModule: Module

internal val appShellModule: Module = module {
    includes(handlersModule)

    viewModel {
        AppViewModel(
            handlers = getAll<AppHandler<*>>(),
            logger = get(),
        )
    }
}

/** Everything Koin starts with: the app's own modules, the data layer, and one module per feature. */
internal val sampleModules: List<Module> = listOf(
    loggingModule,
    dataModule,
    appShellModule,
    taskListModule,
    taskEditorModule,
)
