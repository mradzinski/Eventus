import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Desktop entry point of the sample: the window.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(projects.sampleKoin.app)
    implementation(projects.sampleKoin.core.ui)
    implementation(compose.desktop.currentOs)
    // For the window title, which comes from the string resources in core/ui.
    implementation(libs.compose.resources)
}

compose.desktop {
    application {
        mainClass = "io.github.mradzinski.eventus.sample.desktop.MainKt"
    }
}
