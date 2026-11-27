package io.github.mradzinski.eventus.sample.data

import io.github.mradzinski.eventus.sample.domain.Connectivity
import io.github.mradzinski.eventus.sample.domain.TaskRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/** The implementations of the domain's interfaces. This module is all the app sees of the data layer. */
val dataModule: Module = module {
    single<Connectivity> { InMemoryConnectivity() }
    single<TaskRepository> { InMemoryTaskRepository(connectivity = get()) }
}
