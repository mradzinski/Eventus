import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/*
 * The project's own ktlint rules. The main build includes this build and adds it to every module's
 * `ktlintRuleset`, so `ktlintCheck` and `ktlintFormat` apply these rules next to the standard ones.
 * It is build tooling for everyone who works on the project: it lives in the repository, but it is not
 * uploaded to Maven Central and never reaches anyone who uses the library.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// The main build asks for `io.github.mradzinski.eventus:ktlint-rules`, which Gradle replaces with this project.
group = "io.github.mradzinski.eventus"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // ktlint runs the rules with the Kotlin standard library it ships, which is older than ours
        // (Kotlin 2.2 in ktlint 1.8).
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    // ktlint provides these at runtime.
    compileOnly(libs.ktlint.cli.ruleset.core)
    compileOnly(libs.ktlint.rule.engine.core)

    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    // The rule engine logs through SLF4J, which needs a binding at runtime.
    testRuntimeOnly(libs.slf4j.nop)

    // These rules check their own sources too.
    ktlintRuleset(files(tasks.jar))
}

ktlint {
    version.set(libs.versions.ktlint)
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(file("../../config/detekt/detekt.yml"))
}
