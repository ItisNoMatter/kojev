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

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
