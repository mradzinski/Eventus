package io.github.mradzinski.eventus.sample

import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.createGraph
import platform.UIKit.UIViewController

/** The view controller the iOS app shows. Swift calls it as `MainViewControllerKt.MainViewController()`. */
// Named like a type because that is how Swift code expects a factory of a view controller to read.
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
fun MainViewController(): UIViewController {
    val appGraph = createGraph<AppGraph>()

    return ComposeUIViewController { App(appGraph.metroViewModelFactory) }
}
