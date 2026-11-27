import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
    // Applied here to gather the API reference of every published module into one site; see the end.
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ksp) apply false
    // Applied here so the root build scripts are formatted too; modules get it from the block below.
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
}

dependencies {
    // The project's own rules, applied next to ktlint's standard ones. See tooling/ktlint-rules.
    ktlintRuleset(libs.eventus.ktlint.rules)
}

// Code quality for every module: ktlint checks formatting, detekt checks the code itself.
// Both run as part of `./gradlew build` (through `check`). Fix formatting with `./gradlew ktlintFormat`.
subprojects {
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }
    // In a multiplatform module the plain `detekt` task has no sources of its own: detekt creates one
    // task per source set instead. Make `detekt` run all of them, so one command covers every module.
    // Generated code (for example Compose resource accessors) is not ours to fix.
    tasks.withType<Detekt>().configureEach { exclude { it.file.path.contains("/build/") } }
    // Task names are filtered lazily, so no unrelated task is created just to be inspected.
    tasks.named("detekt") {
        dependsOn(
            provider {
                tasks.names.filter {
                    it.startsWith("detekt") && it.endsWith("SourceSet") &&
                        !it.startsWith("detektBaseline")
                }
            },
        )
    }
    extensions.configure<KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint)
        // Generated code (for example Compose resource accessors) is not ours to format.
        filter { exclude { it.file.path.contains("/build/") } }
    }
    dependencies {
        add("ktlintRuleset", rootProject.libs.eventus.ktlint.rules)
    }
}

// What is published, each as its own artifact with the same version: the library, the annotations that ask
// for a generated Koin module and for generated Metro bindings, the symbol processor, the Compose helpers, and
// the test helper.
val publishedModules = listOf(
    ":eventus",
    ":eventus-koin",
    ":eventus-metro",
    ":eventus-ksp",
    ":eventus-compose",
    ":eventus-test",
)

// One command to upload the library to Maven Central: ./gradlew publishLibrary
// It refuses to start, with a clear message, when the one-time setup (Central Portal token, signing key) is
// incomplete. It uploads only after everything below has passed, so a failing test stops it.
//
// The upload is staged: Central checks it and keeps it out of public view until someone presses "Publish" for
// that deployment on https://central.sonatype.com/publishing/deployments (or "Drop" to throw it away). A
// version on Maven Central can never be replaced, so the last step is a person's.
//
// `uploadLibrary` is the same upload for a commit whose checks have already passed somewhere else. The publish
// workflow uses it, after confirming that the build workflow passed for the commit, so the tests are not run a
// second time. By hand, use `publishLibrary`.
val publishTasks = setOf("publishLibrary", "uploadLibrary")

val publishRequested = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in publishTasks }

// What must pass before the library is published: `check` of every published module. For the library that is
// its tests on every platform (desktop, Android, iOS simulator, Wasm and JS in Chrome), lint, the coverage
// limit and the public API check; for the processor, its tests, lint and the public API check.
// The sample is not part of it: it is not what gets published.
val checksBeforePublishing = publishedModules.map { "$it:check" }

if (publishRequested) {
    val problems = buildList {
        listOf("mavenCentralUsername", "mavenCentralPassword", "signingInMemoryKey").forEach { name ->
            if (!providers.gradleProperty(name).isPresent) add("Missing '$name' in ~/.gradle/gradle.properties.")
        }
    }

    check(problems.isEmpty()) {
        "Cannot publish yet:\n" + problems.joinToString("\n") { " - $it" }
    }

    // Depending on the checks is not enough: Gradle may run tasks that do not depend on each other in any
    // order. Every publishing task of every published module therefore waits for the checks.
    publishedModules.forEach { module ->
        project(module) {
            tasks
                .matching { it.name.startsWith("publish") }
                .configureEach { mustRunAfter(checksBeforePublishing) }
        }
    }
}

tasks.register("publishLibrary") {
    group = "publishing"
    description = "Runs every check, then uploads the library to Maven Central, where it waits to be published by hand."
    dependsOn(checksBeforePublishing)
    dependsOn(publishedModules.map { "$it:publishToMavenCentral" })
}

tasks.register("uploadLibrary") {
    group = "publishing"
    description = "Uploads the library to Maven Central without running the checks. For a commit already checked."
    dependsOn(publishedModules.map { "$it:publishToMavenCentral" })
}

// One API reference for every published module, for the documentation site: ./gradlew :dokkaGenerate
// writes it to build/dokka/html. Each module still builds its own reference for its javadoc jar.
dependencies {
    publishedModules.forEach { module -> dokka(project(module)) }
}

dokka {
    moduleName.set("Eventus")
}

// The reference takes the site's logo and colors. Dokka builds each module's pages in that module, so the
// same settings go to every published module and to this one, which gathers them.
(publishedModules.map { project(it) } + project).forEach { documented ->
    documented.plugins.withId("org.jetbrains.dokka") {
        documented.extensions.configure<DokkaExtension> {
            pluginsConfiguration.named<DokkaHtmlPluginParameters>("html") {
                customStyleSheets.from(rootProject.file("config/dokka/eventus.css"))
                customAssets.from(rootProject.file("config/dokka/logo-icon.svg"))
                homepageLink.set("https://mradzinski.github.io/Eventus/")
                footerMessage.set("Eventus is released under the Apache License 2.0.")
            }
        }
    }
}

// The sample is several Gradle modules, so its tests get one task for all of them:
// `sampleKoinJvmTest` runs the desktop tests, and `sampleKoinTest` runs them on every platform (desktop, the
// Android host and the iOS simulator).
val sampleKoinJvmTest = tasks.register("sampleKoinJvmTest") {
    group = "verification"
    description = "Runs the desktop tests of every module of sample-koin."
}

val sampleKoinTest = tasks.register("sampleKoinTest") {
    group = "verification"
    description = "Runs the tests of every module of sample-koin, on every platform."
}

subprojects {
    if (!path.startsWith(":sample-koin:")) return@subprojects

    val module = this

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        sampleKoinJvmTest.configure { dependsOn(module.tasks.named("jvmTest")) }

        sampleKoinTest.configure {
            dependsOn(module.tasks.named("allTests"))
            dependsOn(module.tasks.matching { it.name == "testAndroidHostTest" })
        }
    }
}
