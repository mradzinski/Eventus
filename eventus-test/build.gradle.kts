import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// What an app's tests use to test its event handlers. It lives here and not in `:eventus`, so that nothing
// made for tests is part of the library an app ships.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    // The public API is recorded in `eventus-test/api/` and compared by `checkKotlinAbi`, as in `:eventus`.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "io.github.mradzinski.eventus.test"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // Host (JVM) tests are off by default with this plugin; this runs commonTest on Android too.
        withHostTest {}
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    js { browser() }

    sourceSets {
        commonMain.dependencies {
            // "api": a test names the library's types, and the function returns what `runTest` returns.
            api(projects.eventus)
            api(libs.kotlinx.coroutines.test)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.assertions.core)
            // Not needed by the helper: its tests show that another testing tool works beside it.
            implementation(libs.turbine)
        }
    }
}

/** The lowest share of covered lines the build accepts, in percent. The same limit as `:eventus`. */
val minimumLineCoveragePercent = 95

kover {
    reports {
        verify {
            rule {
                minBound(minimumLineCoveragePercent)
            }
        }
    }
}

/** The license and the attribution notice, which every published file carries. See `:eventus`. */
val legalFiles = rootProject.files("LICENSE", "NOTICE")

tasks.withType<Jar>().configureEach {
    val isLibraryJar = name == "jvmJar" || name.endsWith("SourcesJar", ignoreCase = true) || name.endsWith("JavadocJar")

    if (isLibraryJar) from(legalFiles) { into("META-INF") }
}

tasks.withType<Zip>().matching { it.name == "bundleAndroidMainAar" }.configureEach {
    from(legalFiles)
}

// Coordinates and POM details come from gradle.properties here and at the root.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
