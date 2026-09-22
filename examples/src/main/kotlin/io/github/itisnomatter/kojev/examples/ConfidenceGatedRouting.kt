package io.github.itisnomatter.kojev.examples

import io.github.itisnomatter.kojev.Criterion
import io.github.itisnomatter.kojev.JevApiException
import io.github.itisnomatter.kojev.JevClient
import io.github.itisnomatter.kojev.JevException
import io.github.itisnomatter.kojev.choice
import io.github.itisnomatter.kojev.decide

// Act automatically when the model is confident, hand off to a human when it isn't. The
// threshold belongs to this code, not to the library: what counts as "confident enough" depends
// on what a wrong routing costs here.

enum class Team(
    override val description: String,
) : Criterion {
    BILLING("Payments, invoicing, refunds, plan changes"),
    TECHNICAL("Bugs, outages, integrations, API errors"),
    SALES("Pricing, upgrades, new accounts, quotes"),
}

val teamQ = choice<Team>("team", "Which team should handle this message?")

/** A wrong routing costs a bounce between queues, so we accept anything reasonably clear. */
private const val AUTO_ROUTE_CONFIDENCE = 0.8

sealed interface Routing {
    data class Auto(
        val team: Team,
    ) : Routing

    data class Human(
        val bestGuess: Team,
        val runnerUp: Team,
    ) : Routing
}

suspend fun route(
    jev: JevClient,
    message: String,
): Routing {
    val answer = jev.decide(message) { ask(teamQ) }[teamQ]
    if (answer.confidence >= AUTO_ROUTE_CONFIDENCE) return Routing.Auto(answer.value)
    val ranked =
        answer.probabilities.entries
            .sortedByDescending { it.value }
            .map { it.key }
    return Routing.Human(bestGuess = ranked[0], runnerUp = ranked[1])
}

suspend fun confidenceGatedRouting(jev: JevClient) {
    val messages =
        listOf(
            "Your API returns 500 every time I call /v1/orders since this morning.",
            "I was charged for the Pro plan but I also can't log in - is that related?",
        )
    for (message in messages) {
        println("\"$message\"")
        try {
            when (val routing = route(jev, message)) {
                is Routing.Auto -> println("  -> ${routing.team} (automatic)")
                is Routing.Human -> println("  -> a person; best guess ${routing.bestGuess}, then ${routing.runnerUp}")
            }
        } catch (e: JevApiException) {
            // Everything a decision throws is a JevException; the API-level ones carry the request id.
            println("  -> could not decide: ${e.message} (request ${e.requestId})")
        } catch (e: JevException) {
            println("  -> could not decide: ${e.message}")
        }
    }
}
