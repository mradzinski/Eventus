package io.github.mradzinski.eventus.sample.android

import android.app.Application
import io.github.mradzinski.eventus.sample.startSample

/** Starts the app's dependency injection once, before the first screen is drawn. */
class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startSample()
    }
}
