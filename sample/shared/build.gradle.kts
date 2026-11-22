import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The sample app shared by every launcher: a task list and a task editor. It holds no entry points except
// the iOS one (MainViewController), because the iOS app is an Xcode project and not a Gradle module.
// It is one module, layered by package, to keep the sample short; a real app would split it.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "io.github.mradzinski.eventus.sample.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // Needed so the strings (Compose resources) are packaged for Android.
        androidResources { enable = true }
        // Host (JVM) tests are off by default with this plugin; this runs commonTest on Android too.
        withHostTest {}
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SampleShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.eventus)
            // The annotation that asks for the generated Koin module of the handlers. See the `ksp` lines below.
            implementation(projects.eventusKoin)
            // `collectEffects`, which each route performs its effects with.
            implementation(projects.eventusCompose)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.resources)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }

        jvmTest.dependencies {
            // Lists the subclasses of a sealed event type, for the tests that check every event has a handler.
            implementation(libs.kotlin.reflect)
        }
    }
}

// The Eventus processor, once per target: it checks that every event has a handler and generates the Koin
// module of the handlers (`handlersModule`). A target left out here does not compile.
dependencies {
    add("kspAndroid", projects.eventusKsp)
    add("kspJvm", projects.eventusKsp)
    add("kspIosArm64", projects.eventusKsp)
    add("kspIosSimulatorArm64", projects.eventusKsp)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.mradzinski.eventus.sample.resources"
}
