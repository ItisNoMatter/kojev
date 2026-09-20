package io.github.itisnomatter.kojev.wire

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises the whole wire round trip through a mocked HTTP transport, using the exact
 * request/response JSON documented in `docs/api-notes.md`.
 */
class SystemOneApiTest {
    private fun clientReturning(
        responseJson: String,
        capturedRequests: MutableList<HttpRequestData>,
    ): HttpClient {
        val engine =
            MockEngine { request ->
                capturedRequests += request
                respond(
                    content = responseJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(jevJson) }
        }
    }

    @Test
    fun `posts to v1systemone with the serialized request and parses the response`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val client =
                clientReturning(
                    responseJson =
                        """{"model":"jev-latest","answers":{""" +
                            """"is_urgent":{"type":"noul","noul":0.92}},""" +
                            """"usage":{"input_tokens":312,"output_tokens":48}}""",
                    capturedRequests = requests,
                )
            val request =
                SystemOneRequestDto(
                    state = JsonPrimitive("Help! My payouts have been failing for 3 days."),
                    model = "jev-latest",
                    questions =
                        mapOf(
                            "is_urgent" to
                                NoulQuestionDto(instructions = "Does this convey urgency?"),
                        ),
                )

            val response = callSystemOne(client, "https://api.typesafe.ai", request)

            assertEquals(1, requests.size)
            assertEquals("https://api.typesafe.ai/v1/systemone", requests.single().url.toString())
            assertEquals(
                """{"state":"Help! My payouts have been failing for 3 days.","model":"jev-latest",""" +
                    """"questions":{"is_urgent":{"type":"noul","instructions":"Does this convey urgency?"}}}""",
                requests
                    .single()
                    .body
                    .toByteArray()
                    .decodeToString(),
            )
            assertEquals(
                SystemOneResponseDto(
                    model = "jev-latest",
                    answers = mapOf("is_urgent" to NoulAnswerDto(noul = 0.92)),
                    usage = UsageDto(inputTokens = 312, outputTokens = 48),
                ),
                response,
            )
        }
}

private fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray =
    when (this) {
        is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
        else -> error("Unsupported content type for test assertion: $this")
    }
