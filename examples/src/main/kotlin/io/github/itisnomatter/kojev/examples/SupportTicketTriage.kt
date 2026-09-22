package io.github.itisnomatter.kojev.examples

import io.github.itisnomatter.kojev.Criterion
import io.github.itisnomatter.kojev.JevClient
import io.github.itisnomatter.kojev.choice
import io.github.itisnomatter.kojev.decide
import io.github.itisnomatter.kojev.noul
import io.github.itisnomatter.kojev.score
import kotlinx.serialization.Serializable

// Several questions about one structured state, answered in a single request. This is Jev's
// primary use: every question is evaluated in parallel against the same state.

/** Send a structure built for the decision - not the whole ticket record. */
@Serializable
data class Ticket(
    val subject: String,
    val body: String,
    val plan: String,
    val priorContacts: Int,
)

/** The caller's own enum carries the descriptions; a constant without one doesn't compile. */
enum class Intent(
    override val description: String,
) : Criterion {
    REFUND("The customer wants money returned"),
    TECHNICAL_SUPPORT("Something is broken or not working as expected"),
    GENERAL_INQUIRY("A question, with nothing to fix or refund"),
}

/** Declaration order is the rubric order, lowest level first. */
enum class Urgency(
    override val description: String,
) : Criterion {
    LATER("Not time-sensitive"),
    TODAY("Should be handled today"),
    NOW("Needs immediate attention"),
}

// Questions are values: define them once, ask them anywhere.
val intentQ = choice<Intent>("intent", "What does the customer want?")
val angryQ =
    noul("is_angry", "Is the customer expressing anger?") {
        whenTrue = "Clear irritation, blame, or forceful language"
        whenFalse = "Neutral or calm, even if unhappy"
    }
val urgencyQ = score<Urgency>("urgency", "How urgently does this need a response?")

suspend fun supportTicketTriage(jev: JevClient) {
    val ticket =
        Ticket(
            subject = "Charged twice this month",
            body = "I see two charges of $49 on my card for August. I only have one account. Please fix this ASAP.",
            plan = "pro",
            priorContacts = 2,
        )

    val result = jev.decide(ticket) { ask(intentQ, angryQ, urgencyQ) }

    val intent = result[intentQ]
    println("intent:   ${intent.value} (confidence ${intent.confidence.percent()})")
    println("          ${intent.probabilities.entries.joinToString { "${it.key} ${it.value.percent()}" }}")

    println("angry:    ${result[angryQ].percent()}")

    val urgency = result[urgencyQ]
    println("urgency:  most likely ${urgency.mostLikely}, mean level ${"%.2f".format(urgency.score)}")

    println("model ${result.model}, ${result.usage.inputTokens} input tokens, request ${result.requestId}")
}
