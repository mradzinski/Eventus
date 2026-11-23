package io.github.mradzinski.eventus.integration.metro

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
internal interface PlatformAppGraph : AppGraph

internal actual fun createAppGraph(): AppGraph = createGraph<PlatformAppGraph>()
