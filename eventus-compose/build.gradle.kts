import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// Helpers for a Compose UI. They live here and not in `:eventus`, which knows nothing about a user
// interface and depends on neither Compose nor the lifecycle library.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // Only for `compose.desktop.currentOs`, which gives the desktop tests something to draw with.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    // The public API is recorded in `eventus-compose/api/` and compared by `checkKotlinAbi`, as in `:eventus`.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    // The tests use one `expect` class, the base class that carries Android's test runner. Kotlin marks
    // such classes as Beta and warns on every build; this says the use is deliberate. The helpers have none.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "io.github.mradzinski.eventus.compose"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // Host (JVM) tests are off by default with this plugin; this runs the tests on Android too.
        withHostTest { isIncludeAndroidResources = true }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    // No `iosX64`, unlike `:eventus`: Compose Multiplatform 1.12.2 does not publish for it.
    iosArm64()
    iosSimulatorArm64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        // For the tests only: a browser can load the drawing engine Compose needs only from a bundle, and
        // the Compose plugin refuses to run UI tests without one.
        binaries.executable()
    }
    js { browser() }

    // The default source sets (iosMain, iosTest and so on) are kept, next to the one added below by hand.
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            // "api": the helpers are extensions of the library's `EventRouter`, and composables.
            api(projects.eventus)
            api(libs.compose.runtime)
            // The lifecycle the helpers follow, and `LocalLifecycleOwner`.
            implementation(libs.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotest.assertions.core)
        }

        // Every test of this module composes something, so they all live here, shared by the targets that
        // can run them: desktop, iOS, the web (Wasm) and Android, where Robolectric stands in for a device.
        val uiTest = create("uiTest") {
            dependsOn(commonTest.get())

            dependencies {
                implementation(libs.compose.ui.test)
            }
        }

        // The JavaScript target is left out: in its test page the drawing engine is not loaded, so every
        // test that composes fails there. The Wasm target covers the web.
        jvmTest { dependsOn(uiTest) }
        iosTest { dependsOn(uiTest) }
        wasmJsTest { dependsOn(uiTest) }

        getByName("androidHostTest") {
            dependsOn(uiTest)

            dependencies {
                implementation(libs.robolectric)
                implementation(libs.androidx.compose.ui.test.manifest)
                implementation(libs.androidx.test.espresso.core)
            }
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            // The main dispatcher on desktop, which the lifecycle library switches to.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// The Compose plugin unpacks the drawing engine for the web only when a module's own code depends on
// Compose UI. This one depends on the runtime alone, but its tests compose, so they need the engine too.
// It is set once the plugin has finished configuring the task, or the plugin's own condition would win.
afterEvaluate {
    tasks.named("unpackSkikoWasmRuntime") { setOnlyIf { true } }
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
