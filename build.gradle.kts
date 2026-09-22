plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.ktlint)
}

group = "io.github.itisnomatter"
version = "0.1.0-SNAPSHOT"

kotlin {
    // Pinned explicitly so published bytecode targets a broadly compatible JVM regardless of
    // whatever JDK happens to run the build (also works around older tooling - e.g.
    // binary-compatibility-validator 0.18.2's bundled ASM - not yet reading newer class files).
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    android {
        namespace = "io.github.itisnomatter.kojev"
        compileSdk = 36
        minSdk = 21
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Live-API tests: a separate JVM compilation, run by `jvmLiveTest` only when TYPESAFE_API_KEY is
// set and reported as SKIPPED otherwise (AGENTS.md hard rule 5). This is the only place a concrete
// Ktor engine is allowed as a dependency.
val liveTest =
    kotlin.jvm().compilations.create("liveTest") {
        associateWith(kotlin.jvm().compilations.getByName("main"))
        defaultSourceSet.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.cio)
        }
    }

tasks.register<Test>("jvmLiveTest") {
    group = "verification"
    description = "Runs the live-API tests against the real API; skipped unless TYPESAFE_API_KEY is set."
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.output.allOutputs + liveTest.runtimeDependencyFiles
    useJUnit()
    onlyIf("TYPESAFE_API_KEY is set") { !System.getenv("TYPESAFE_API_KEY").isNullOrBlank() }
}

tasks.named("check") {
    dependsOn("jvmLiveTest")
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
