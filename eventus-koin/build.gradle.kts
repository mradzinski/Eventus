import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

// One annotation and nothing else, so it has no dependencies: not on Eventus, and not on Koin either. The
// code that eventus-ksp generates for it uses Koin, which the app already has.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    explicitApi()

    // The public API is recorded in `eventus-koin/api/` and compared by `checkKotlinAbi`, as in `:eventus`.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "io.github.mradzinski.eventus.koin"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
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
