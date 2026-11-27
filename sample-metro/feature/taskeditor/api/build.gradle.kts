import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What other modules may know of the task editor: its destination. Another feature that wants to navigate
// here depends on this module, never on the implementation.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "io.github.mradzinski.eventus.sample.taskeditor.api"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.navigation3.runtime)
        }
    }
}
