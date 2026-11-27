package io.github.mradzinski.eventus.sample.android

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/** The graph of the Android app. Metro fills it with what every module contributes to [AppScope]. */
@DependencyGraph(AppScope::class)
interface AppGraph :
    MetroAppComponentProviders,
    ViewModelGraph
