// Named differently from the :eventus module, so the generated project accessors do not clash.
rootProject.name = "eventus-root"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// The project's own ktlint rules, added to every module's ktlintRuleset by the root build script.
includeBuild("tooling/ktlint-rules")

// The library.
include(":eventus")

// Sample apps: one shared module with the two demo screens, and one entry-point module per platform.
// The iOS entry point is the Xcode project in sample/iosApp, which is not a Gradle module.
include(":sample:shared")
include(":sample:androidApp")
include(":sample:desktopApp")
