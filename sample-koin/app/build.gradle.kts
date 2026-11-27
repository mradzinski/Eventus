import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app: its own router (the "Work offline" switch), the navigation between the features, and the start
// of Koin. It is the one module that depends on the implementations, because it puts them together.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "io.github.mradzinski.eventus.sample.app"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
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
            implementation(projects.sampleKoin.core.domain)
            implementation(projects.sampleKoin.core.data)
            implementation(projects.sampleKoin.core.ui)
            implementation(projects.sampleKoin.feature.tasklist.impl)
            implementation(projects.sampleKoin.feature.taskeditor.impl)

            implementation(projects.eventus)
            implementation(projects.eventusKoin)

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
        }

        commonTest.dependencies {
            implementation(projects.eventusTest)
            implementation(projects.sampleKoin.core.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
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
}
