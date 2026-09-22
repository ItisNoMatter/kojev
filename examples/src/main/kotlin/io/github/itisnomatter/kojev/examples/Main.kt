package io.github.itisnomatter.kojev.examples

import io.github.itisnomatter.kojev.JevClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Runs the examples against the real API. They need `TYPESAFE_API_KEY` in the environment - the
 * key is never written into code:
 *
 * ```
 * TYPESAFE_API_KEY=... ./gradlew :examples:run                  # all of them
 * TYPESAFE_API_KEY=... ./gradlew :examples:run --args=triage    # one of: triage, routing, score
 * ```
 *
 * `./gradlew build` compiles them, so they cannot silently stop matching the library.
 */
fun main(args: Array<String>) {
    val apiKey = System.getenv("TYPESAFE_API_KEY")
    if (apiKey.isNullOrBlank()) {
        System.err.println("Set TYPESAFE_API_KEY to run the examples; they call the real API.")
        exitProcess(2)
    }
    val all =
        listOf(
            "triage" to ::supportTicketTriage,
            "routing" to ::confidenceGatedRouting,
            "score" to ::scoreUsage,
        )
    val selected = args.firstOrNull()
    val examples = all.filter { selected == null || it.first == selected }
    if (examples.isEmpty()) {
        System.err.println("Unknown example '$selected'. Known: ${all.joinToString { it.first }}.")
        exitProcess(2)
    }
    runBlocking {
        JevClient(apiKey, CIO.create()).use { jev ->
            for ((name, run) in examples) {
                println("== $name ==")
                run(jev)
                println()
            }
        }
    }
}

internal fun Double.percent(): String = "${(this * 100).toInt()}%"
