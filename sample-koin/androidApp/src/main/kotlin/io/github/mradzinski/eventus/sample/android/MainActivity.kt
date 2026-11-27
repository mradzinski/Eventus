package io.github.mradzinski.eventus.sample.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mradzinski.eventus.sample.App

/** The only activity of the Android sample: it shows the shared Compose app. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent { App() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // The activity handles its own configuration changes, so the system bars are restyled by hand.
        enableEdgeToEdge()
    }
}
