import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // Declared here (not applied) so the examples subproject can apply the same Kotlin version.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.maven.publish)
}

group = "io.github.itisnomatter"
version = "0.2.0-SNAPSHOT"

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
    // A live test asks whether the real API answers *now*. Gradle would otherwise treat an unchanged
    // source tree as up-to-date and silently reuse the previous run's result.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

tasks.named("check") {
    dependsOn("jvmLiveTest")
}

// The release workflow compares this with the pushed tag before publishing.
tasks.register("printVersion") {
    doLast { println(version) }
}

mavenPublishing {
    // Uploads and validates; the deployment is released by hand in the Central Portal.
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()
    coordinates(group.toString(), "kojev", version.toString())
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))

    pom {
        name.set("kojev")
        description.set("Kotlin Multiplatform client for Jev that returns your own enum/sealed types instead of string keys.")
        inceptionYear.set("2026")
        url.set("https://github.com/ItisNoMatter/kojev")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ItisNoMatter")
                name.set("ItisNoMatter")
                url.set("https://github.com/ItisNoMatter")
            }
        }
        scm {
            url.set("https://github.com/ItisNoMatter/kojev")
            connection.set("scm:git:git://github.com/ItisNoMatter/kojev.git")
            developerConnection.set("scm:git:ssh://git@github.com/ItisNoMatter/kojev.git")
        }
    }
}

apiValidation {
    // Runnable samples, not published API.
    ignoredProjects.add("examples")

    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
