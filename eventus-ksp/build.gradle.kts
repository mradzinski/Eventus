import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// A symbol processor: it runs inside the build of an app that uses Eventus and is never part of the app.
// It is a JVM library whatever the app targets, because the build runs on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()

    // The public API (one class, the entry point KSP looks up) is recorded in `eventus-ksp/api/` and compared
    // by `checkKotlinAbi`, as in `:eventus`.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // The processor recognizes Eventus and Koin types by name, so it depends on neither.
    implementation(libs.ksp.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kctfork.ksp)
    testImplementation(libs.ksp.aa.embeddable)
    testImplementation(libs.ksp.common.deps)
    // What the sources compiled by the tests refer to.
    testImplementation(projects.eventus)
    testImplementation(projects.eventusKoin)
    testImplementation(libs.koin.core)
}

/** The lowest share of covered lines the build accepts, in percent. The same limit as `:eventus`. */
val minimumLineCoveragePercent = 95

// The tests run the processor inside their own JVM, so Kover measures it like any other code.
// `koverVerify` is part of `check`.
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
    from(legalFiles) { into("META-INF") }
}

// Coordinates and POM details come from gradle.properties here and at the root.
mavenPublishing {
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
