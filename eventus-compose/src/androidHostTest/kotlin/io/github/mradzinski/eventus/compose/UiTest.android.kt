package io.github.mradzinski.eventus.compose

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The base of the tests that compose something. On Android it runs them with Robolectric, which gives a
 * test Android's own code on the computer, with no emulator. The settings are in `robolectric.properties`.
 */
@RunWith(RobolectricTestRunner::class)
actual abstract class UiTest
