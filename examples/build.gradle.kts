plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.cio)
}

application {
    mainClass = "io.github.itisnomatter.kojev.examples.MainKt"
}
