package io.github.itisnomatter.kojev

import io.github.itisnomatter.kojev.wire.ChoiceQuestionDto
import io.github.itisnomatter.kojev.wire.NoulQuestionDto
import io.github.itisnomatter.kojev.wire.Transport
import io.github.itisnomatter.kojev.wire.jevJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class SystemOneTest {
    private enum class Department(
        override val description: String,
    ) : Criterion {
        BILLING("Billing"),
        TECHNICAL("Technical"),
    }

    @Test
    fun `buildRequest requires at least one question`() {
        assertFailsWith<IllegalArgumentException> {
            buildRequest(model = "jev-latest", state = JsonPrimitive("..."), keys = emptyList())
        }
    }

    @Test
    fun `buildRequest rejects two questions with the same name`() {
        assertFailsWith<IllegalArgumentException> {
            buildRequest(
                model = "jev-latest",
                state = JsonPrimitive("..."),
                keys =
                    listOf(
                        noul("dup", NoulQuestion(instructions = "a")),
                        noul("dup", NoulQuestion(instructions = "b")),
                    ),
            )
        }
    }

    @Test
    fun `buildRequest assembles every question under the shared state and model`() {
        val angryQ = noul("is_angry", NoulQuestion(instructions = "Is the customer angry?"))
        val deptQ = choice<Department>("department", instructions = "Which team?")

        val request = buildRequest(model = "jev-latest", state = JsonPrimitive("Refund please."), keys = listOf(angryQ, deptQ))

        assertEquals(JsonPrimitive("Refund please."), request.state)
        assertEquals("jev-latest", request.model)
        assertEquals(
            mapOf(
                "is_angry" to NoulQuestionDto(instructions = "Is the customer angry?"),
                "department" to
                    ChoiceQuestionDto(
                        instructions = "Which team?",
                        criteria =
                            linkedMapOf(
                                "billing" to "Billing",
                                "technical" to "Technical",
                            ),
                    ),
            ),
            request.questions,
        )
    }

    @Test
    fun `requestDecision sends the built request and returns typed answers`() =
        runTest {
            val angryQ = noul("is_angry", NoulQuestion(instructions = "Is the customer angry?"))
            val engine =
                MockEngine {
                    respond(
                        content =
                            """{"model":"jev-1.13.0","answers":{"is_angry":{"type":"noul","noul":0.9}},""" +
                                """"usage":{"input_tokens":10,"output_tokens":2}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val client = HttpClient(engine) { install(ContentNegotiation) { json(jevJson) } }
            val transport = Transport(client, "https://api.typesafe.ai", 10.seconds, RetryPolicy().snapshot())

            val decision =
                requestDecision(
                    transport = transport,
                    model = "jev-latest",
                    state = JsonPrimitive("Cancel my order and refund me right now."),
                    keys = listOf(angryQ),
                )

            assertEquals(0.9, decision[angryQ])
            assertEquals("jev-1.13.0", decision.model)
        }
}
