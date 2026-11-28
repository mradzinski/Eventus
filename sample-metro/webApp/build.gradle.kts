import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

// Web launcher of the sample, for both JS and Wasm. The page's content and the graph are in the app module.
// The Compose plugin is here because it is what adds Compose's drawing engine to the page.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { devServer = devServerOnPort8080() } }
        binaries.executable()
    }

    js {
        browser { commonWebpackConfig { devServer = devServerOnPort8080() } }
        binaries.executable()
    }

    sourceSets {
        webMain.dependencies {
            implementation(projects.sampleMetro.app)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}

/** A development server on port 8080 that does not open a browser window by itself. */
fun KotlinWebpackConfig.devServerOnPort8080(): KotlinWebpackConfig.DevServer =
    (devServer ?: KotlinWebpackConfig.DevServer()).copy(open = false, port = 8080)
