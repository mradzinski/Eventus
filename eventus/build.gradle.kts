import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    // The library's public API is recorded in `eventus/api/`, and `checkKotlinAbi` (part of `check`) fails
    // when the code no longer matches it, so the API cannot change by accident. After a change that is meant:
    // `./gradlew :eventus:updateKotlinAbi`, then review the difference in those files like any other code.
    // The Kotlin plugin marks this feature as experimental; it is build tooling, not part of the library.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "io.github.mradzinski.eventus"
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
            // "api": these types (StateFlow, Flow, CoroutineScope) appear in the library's public signatures.
            api(libs.kotlinx.coroutines.core)
            // Only used inside the library, for the lock of the router.
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
            implementation(libs.turbine)
        }
    }
}

/** The lowest share of covered lines the build accepts, in percent. */
val minimumLineCoveragePercent = 95

// Test coverage of the library, measured on the desktop and Android host test runs together.
// `./gradlew :eventus:koverHtmlReport` writes a browsable report; `koverVerify` (part of `check`, so of
// `build`) fails when fewer than 95% of the lines are covered.
kover {
    reports {
        verify {
            rule {
                minBound(minimumLineCoveragePercent)
            }
        }
    }
}

/** The license and the attribution notice, which every published file of the library carries. */
val legalFiles = rootProject.files("LICENSE", "NOTICE")

// The license asks anyone who redistributes the library to pass on its NOTICE, which only binds when the
// NOTICE is part of what they received. So both files go into every jar the library publishes (classes,
// sources and documentation, for every target) and into the Android archive. The Kotlin library files
// of the iOS and web targets (.klib) have a fixed layout and are left alone: those targets carry the
// two files in their sources jar.
tasks.withType<Jar>().configureEach {
    val isLibraryJar = name == "jvmJar" || name.endsWith("SourcesJar", ignoreCase = true) || name.endsWith("JavadocJar")

    if (isLibraryJar) from(legalFiles) { into("META-INF") }
}

tasks.withType<Zip>().matching { it.name == "bundleAndroidMainAar" }.configureEach {
    from(legalFiles)
}

// Coordinates and POM details come from the GROUP / POM_* / VERSION_NAME values in gradle.properties.
mavenPublishing {
    publishToMavenCentral()
    // Sign only when a key is configured, so publishing to the local Maven repository needs no key.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
