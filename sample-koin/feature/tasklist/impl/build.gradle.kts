import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The task list: its events, state and effects, one event handler class per event, the view model, the
// screen, and the Koin module that wires them. Only the app depends on this module.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "io.github.mradzinski.eventus.sample.tasklist"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        withHostTest {}
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    // `binaries.executable()` is for the tests only: the Compose plugin refuses to run the tests of a module
    // that uses Compose UI in a browser without a bundle, which is what loads Compose's drawing engine.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.sampleKoin.feature.tasklist.api)
            implementation(projects.sampleKoin.core.domain)
            implementation(projects.sampleKoin.core.ui)

            implementation(projects.eventus)
            implementation(projects.eventusKoin)
            implementation(projects.eventusCompose)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        commonTest.dependencies {
            implementation(projects.eventusTest)
            implementation(projects.sampleKoin.core.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
            implementation(libs.koin.core)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.reflect)
        }
    }
}

dependencies {
    add("kspAndroid", projects.eventusKsp)
    add("kspJvm", projects.eventusKsp)
    add("kspIosArm64", projects.eventusKsp)
    add("kspIosSimulatorArm64", projects.eventusKsp)
    add("kspWasmJs", projects.eventusKsp)
    add("kspJs", projects.eventusKsp)
}
