import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Desktop launcher of the sample. The window and the graph are in the app module, as in Metro's own sample.
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
    implementation(projects.sampleMetro.app)
}

compose.desktop {
    application {
        mainClass = "io.github.mradzinski.eventus.sample.MainKt"
    }
}
