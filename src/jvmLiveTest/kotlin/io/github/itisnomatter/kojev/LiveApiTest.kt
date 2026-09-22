package io.github.itisnomatter.kojev

import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against the real API. The `jvmLiveTest` task only executes when `TYPESAFE_API_KEY` is
 * set, so an absent key is a skip, not a failure. These assert types and ranges, never the
 * model's actual verdicts.
 */
class LiveApiTest {
    private val apiKey: String = requireNotNull(System.getenv("TYPESAFE_API_KEY")) { "TYPESAFE_API_KEY is not set" }

    private enum class Intent(
        override val description: String,
    ) : Criterion {
        REFUND("The customer wants money returned"),
        OTHER("Anything else"),
    }

    private enum class Urgency(
        override val description: String,
    ) : Criterion {
        LATER("Not time-sensitive"),
        NOW("Needs immediate attention"),
    }

    @Test
    fun `one decision with all three primitives round-trips with typed answers`() {
        runBlocking {
            JevClient(apiKey, CIO.create()).use { jev ->
                val intentQ = choice<Intent>("intent", "What does the customer want?")
                val angryQ = noul("is_angry", "Is the customer expressing anger?")
                val urgencyQ = score<Urgency>("urgency", "How urgently does this need a response?")

                val result =
                    jev.decide("Cancel my order and refund me right now.") {
                        ask(intentQ, angryQ, urgencyQ)
                    }

                assertNotNull(result.requestId)
                assertTrue(result.model.startsWith("jev-"))
                assertTrue(result.usage.inputTokens > 0)

                val intent = result[intentQ]
                assertTrue(intent.value in Intent.entries)
                assertEquals(Intent.entries.toSet(), intent.probabilities.keys)
                assertTrue(intent.confidence in 0.0..1.0)

                assertTrue(result[angryQ] in 0.0..1.0)

                val urgency = result[urgencyQ]
                assertTrue(urgency.score in 0.0..1.0)
                assertTrue(urgency.mostLikely in Urgency.entries)
                assertTrue(urgency.confidence in 0.0..1.0)
            }
        }
    }

    @Test
    fun `an invalid key is a JevAuthenticationException with a request id`() {
        runBlocking {
            JevClient("invalid-key", CIO.create()).use { jev ->
                val e =
                    assertFailsWith<JevAuthenticationException> {
                        jev.decide("hello") { ask(noul("q", "Is this a greeting?")) }
                    }
                assertEquals(401, e.status)
                assertNotNull(e.requestId)
            }
        }
    }
}
