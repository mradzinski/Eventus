import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the screens share: the top bar, the failure reasons with their texts, the log of a router, and the
// app's string resources.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "io.github.mradzinski.eventus.sample.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        androidResources { enable = true }
        withHostTest {}
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.eventus)
            implementation(projects.sampleKoin.core.domain)
            api(libs.kermit)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.resources)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "io.github.mradzinski.eventus.sample.resources"
}
