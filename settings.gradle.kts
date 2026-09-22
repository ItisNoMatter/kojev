pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kojev"

include("examples")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
