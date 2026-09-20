plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.ktlint)
}

group = "io.github.itisnomatter"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    android {
        namespace = "io.github.itisnomatter.kojev"
        compileSdk = 36
        minSdk = 21
    }
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
