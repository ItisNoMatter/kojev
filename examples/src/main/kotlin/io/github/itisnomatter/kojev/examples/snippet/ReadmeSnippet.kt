package io.github.itisnomatter.kojev.examples.snippet

import io.github.itisnomatter.kojev.Criterion
import io.github.itisnomatter.kojev.JevClient
import io.github.itisnomatter.kojev.choice
import io.github.itisnomatter.kojev.decide
import io.github.itisnomatter.kojev.noul
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

// The snippet the README opens with, compiled by `./gradlew build` so that it cannot go stale
// while the API moves. Keep the declarations below identical to the README's; the imports, the
// `Ticket` value, and `runBlocking` are the scaffolding a real program needs and the snippet
// leaves out. It lives in its own package because the other examples already use these names.

@Serializable
data class Ticket(
    val subject: String,
    val body: String,
    val plan: String,
    val priorContacts: Int,
)

enum class Intent(
    override val description: String,
) : Criterion {
    REFUND("The customer wants money returned"),
    TECHNICAL_SUPPORT("Something is broken or not working"),
    GENERAL_INQUIRY("A question, with nothing to fix or refund"),
}

val intentQ = choice<Intent>("intent", "What does the customer want?")
val angryQ = noul("is_angry", "Is the customer expressing anger?")

fun main() {
    runBlocking {
        JevClient(System.getenv("TYPESAFE_API_KEY"), CIO.create()).use { jev ->
            val ticket = Ticket("Charged twice", "Please refund the duplicate.", "pro", 2)

            val result = jev.decide(ticket) { ask(intentQ, angryQ) }

            val intent: Intent = result[intentQ].value // your enum, no string, no cast
            val angry: Double = result[angryQ] // a probability
            println("$intent, angry=$angry")
        }
    }
}
