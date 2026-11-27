plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.mradzinski.eventus.sample.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.mradzinski.eventus.sample.metro"
        // Metro's Android helper needs Android 9.
        minSdk = 28
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.sampleMetro.app)
}
