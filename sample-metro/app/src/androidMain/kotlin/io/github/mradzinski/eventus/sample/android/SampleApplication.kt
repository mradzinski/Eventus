package io.github.mradzinski.eventus.sample.android

import android.app.Application
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

/** Creates the graph once. Metro's Android helper reads it to create [MainActivity]. */
class SampleApplication :
    Application(),
    MetroApplication {

    override val appComponentProviders: MetroAppComponentProviders by lazy { createGraph<AppGraph>() }
}
